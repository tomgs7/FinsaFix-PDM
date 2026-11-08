package pt.ubi.di.pdm.finsafix.data

import android.content.Context
import android.util.Log
import pt.ubi.di.pdm.finsafix.util.ProducaoPerdida
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import pt.ubi.di.pdm.finsafix.R

/**
 * Lê assets/dados-demo.json e preenche a base de dados na primeira execução.
 * Os campos "*HaMin" são minutos antes de agora: data = agora - minutos * 60 000.
 */
class CarregadorDadosDemo(private val context: Context) {

    private val agora = System.currentTimeMillis()

    /**
     * Só carrega se ainda não há secções: é a primeira execução ou a app foi fechada a meio do carregamento
     * (a transação desfaz tudo, por isso a base de dados fica vazia e tenta-se outra vez na abertura seguinte).
     */
    suspend fun carregarSeVazia(db: AppDatabase) {
        if (db.secaoDao().contar() == 0) carregar(db)
    }

    suspend fun carregar(db: AppDatabase) {
        try {
            val json = context.assets.open(FICHEIRO).bufferedReader().use { it.readText() }
            val raiz = JSONObject(json)
            val secoes = lerSecoes(raiz)
            val pessoas = lerPessoas(raiz, secoes.map { it.id }.toSet())
            val avarias = raiz.getJSONArray("avarias").objetos()

            // Tudo numa transação: ou fica tudo gravado, ou nada.
            // A ordem respeita as chaves estrangeiras: primeiro o que é referido, depois quem o refere.
            db.withTransaction {
                db.secaoDao().inserirTodas(secoes)
                db.equipamentoDao().inserirTodos(lerEquipamentos(raiz))
                db.pessoaDao().inserirTodas(pessoas)
                avarias.forEach { inserirAvaria(db, it, pessoas) }
                db.autorizacaoDao().inserirTodas(lerAutorizacoes(raiz, pessoas))
                db.paragemDao().inserirTodas(lerParagens(raiz))
                db.parametroDao().inserirTodas(lerParametros(raiz))
                db.leituraDao().inserirTodas(lerLeituras(raiz))
                carregarExtra(db)
            }
        } catch (e: Exception) {
            // Sem dados de demonstração a aplicação continua a funcionar, mas o ecrã de entrada avisa.
            falhou = true
            Log.e(TAG, "Não foi possível carregar os dados de demonstração", e)
        }
    }

    /** Instruções, preventivas, peças e registos do turno (pedidos, notas, receções e rondas): ficheiro à parte. */
    private suspend fun carregarExtra(db: AppDatabase) {
        val raiz = JSONObject(context.assets.open(FICHEIRO_EXTRA).bufferedReader().use { it.readText() })
        db.instrucaoDao().inserirTodas(raiz.getJSONArray("instrucoes").objetos().map {
            val passos = it.getJSONArray("passos")
            Instrucao(
                id = it.getString("id"), secaoId = it.getString("secao"), titulo = it.getString("titulo"),
                duracaoMin = it.getInt("min"), passos = (0 until passos.length()).joinToString("\n") { i -> passos.getString(i) },
                estado = it.getString("estado"), validadaPorId = it.textoOuNulo("validadaPor"), dataValidacao = it.dataOuNula("validadaHaMin")
            )
        })
        db.preventivaDao().inserirTodas(raiz.getJSONArray("preventivas").objetos().map {
            Preventiva(
                equipamentoId = it.getString("equipamento"), titulo = it.getString("titulo"), especialidade = it.getString("esp"),
                intervaloH = it.getInt("intervaloH"), ultimaData = agora - it.getInt("desdeUltimaH") * Preventiva.HORA_MS,
                ultimaPorId = it.textoOuNulo("ultimaPor")
            )
        })
        db.pecaDao().inserirTodas(raiz.getJSONArray("pecas").objetos().map {
            Peca(
                id = it.getString("id"), nome = it.getString("nome"), equipamentoId = it.getString("equipamento"),
                especialidade = it.getString("esp"), minimo = it.getInt("minimo"), stock = it.getInt("stock"),
                pedidaPorId = it.textoOuNulo("pedidaPor"), dataPedido = it.dataOuNula("pedidaHaMin")
            )
        })
        val equipamentos = db.equipamentoDao().obterTodos()
        db.ordemProducaoDao().inserirTodas(
            equipamentos.groupBy { it.secaoId }.flatMap { (secaoId, lista) ->
                val porEquipamento = (ProducaoPerdida.ritmo(secaoId) * 8 / lista.size / 5).toInt().coerceAtLeast(1) * 5.0
                lista.flatMap { equipamento -> Turno.TODOS.map { OrdemProducao(secaoId = secaoId, equipamentoId = equipamento.id, turno = it, planeado = porEquipamento) } }
            }
        )
        db.registoDao().inserirTodos(raiz.getJSONArray("registos").objetos().map {
            Registo(
                tipo = it.getString("tipo"), secaoId = it.getString("secao"), pessoaId = it.getString("pessoa"),
                texto = it.getString("texto"), extra = it.textoOuNulo("extra"), urgente = it.optBoolean("urgente"),
                estado = it.getString("estado"), dataCriacao = it.dataOuNula("haMin") ?: agora,
                tratadoPorId = it.textoOuNulo("tratadoPor"), resposta = it.textoOuNulo("resposta"),
                dataTratamento = it.dataOuNula("tratadoHaMin")
            )
        })
    }

    private fun lerSecoes(raiz: JSONObject): List<Secao> {
        val secoes = raiz.getJSONArray("secoes").objetos().map {
            Secao(it.getString("id"), it.getString("nome"), it.getString("unidade"), Secao.TIPO_SECAO)
        }
        val oficinas = raiz.getJSONArray("oficinas").objetos().map {
            Secao(it.getString("id"), it.getString("nome"), "", Secao.TIPO_OFICINA)
        }
        return secoes + oficinas
    }

    private fun lerEquipamentos(raiz: JSONObject): List<Equipamento> {
        return raiz.getJSONArray("secoes").objetos().flatMap { secao ->
            secao.getJSONArray("equipamentos").objetos().map {
                Equipamento(it.getString("id"), it.getString("nome"), secao.getString("id"))
            }
        }
    }

    private fun lerPessoas(raiz: JSONObject, idsDosPostos: Set<String>): List<Pessoa> {
        // As listas "chefes" e "engenheiros" já trazem só o hash do PIN (nunca o PIN em texto).
        val pins = (raiz.getJSONArray("chefes").objetos() + raiz.getJSONArray("engenheiros").objetos())
            .associate { it.getString("id") to it.getString("pinHash") }

        return raiz.getJSONArray("pessoas").objetos().map {
            val id = it.getString("id")
            Pessoa(
                id = id,
                nome = it.getString("nome"),
                funcao = it.getString("funcao"),
                papel = it.getString("papel"),
                // A engenharia tem o posto "—" (toda a fábrica): fica sem posto.
                postoId = it.getString("posto").takeIf { posto -> posto in idsDosPostos },
                turno = it.getString("turno"),
                especialidade = it.textoOuNulo("especialidade"),
                pinHash = pins[id]
            )
        }
    }

    private suspend fun inserirAvaria(db: AppDatabase, json: JSONObject, pessoas: List<Pessoa>) {
        val progresso = if (json.isNull("emProgresso")) null else json.getJSONObject("emProgresso")
        val progressoPor = progresso?.let { p -> pessoas.find { it.nome == p.getString("nome") } }

        val codigo = json.getString("id")
        val avaria = Avaria(
            // O id é o número do código (AV-2026-0422 → 422), para as avarias novas continuarem a numeração.
            id = numeroDoCodigo(codigo),
            codigo = codigo,
            equipamentoId = json.getString("equipamento"),
            titulo = json.getString("titulo"),
            descricao = json.getString("descricao"),
            prioridade = json.getString("prioridade"),
            especialidade = json.getString("especialidade"),
            estado = json.getString("estado"),
            reportadaPorId = json.getString("reportadaPor"),
            dataCriacao = json.dataOuNula("criadaHaMin") ?: agora,
            tecnicoId = json.textoOuNulo("tecnico"),
            dataInicio = json.dataOuNula("inicioHaMin"),
            dataFim = json.dataOuNula("fimHaMin"),
            progressoOrdem = progresso?.getString("ordem"),
            progressoRetoma = progresso?.getString("ate"),
            progressoMotivo = progresso?.getString("motivo"),
            progressoNota = progresso?.getString("nota"),
            progressoPorId = progressoPor?.id,
            progressoData = progresso?.dataOuNula("haMin")
        )
        val avariaId = db.avariaDao().inserir(avaria)
        val ajudantes = json.optJSONArray("ajudantes")?.let { lista -> (0 until lista.length()).map { lista.getString(it) } }.orEmpty()
        val equipa = criarEquipa(avariaId, avaria, ajudantes)
        db.avariaTecnicoDao().inserirTodos(equipa)
        db.eventoAvariaDao().inserirTodos(criarCronologia(avariaId, avaria, pessoas, equipa))
    }

    /** O responsável entra quando assume; os ajudantes juntam-se 15 minutos depois (ou agora, se ainda não passou tanto tempo). */
    private fun criarEquipa(avariaId: Long, avaria: Avaria, ajudantes: List<String>): List<AvariaTecnico> {
        val inicio = avaria.dataInicio ?: return emptyList()
        val responsavel = avaria.tecnicoId?.let { AvariaTecnico(avariaId, it, inicio, avaria.dataFim) }
        val ajudas = ajudantes.map { AvariaTecnico(avariaId, it, minOf(inicio + 15 * 60_000, agora), avaria.dataFim) }
        return listOfNotNull(responsavel) + ajudas
    }

    /**
     * As datas seguintes ao pedido são inventadas de forma coerente: decisão 5 min depois, início 10 min depois,
     * fim do trabalho após a duração pedida e encerramento a seguir (60 min no trabalho a quente, pela vigilância).
     */
    private fun lerAutorizacoes(raiz: JSONObject, pessoas: List<Pessoa>): List<Autorizacao> =
        raiz.getJSONArray("autorizacoes").objetos().map {
            val estado = it.getString("estado")
            val tipo = it.getString("tipo")
            val secaoId = it.getString("secao")
            val chefe = pessoas.find { p -> p.papel == Pessoa.PAPEL_CHEFE && p.postoId == secaoId }
            val pedido = it.dataOuNula("haMin") ?: agora
            val minuto = 60_000L
            val iniciou = estado == Autorizacao.EM_CURSO || estado == Autorizacao.ENCERRADA
            val inicio = if (iniciou) pedido + 10 * minuto else null
            val terminado = when {
                estado == Autorizacao.ENCERRADA -> minOf(inicio!! + it.getLong("duracaoMin") * minuto, agora)
                estado == Autorizacao.EM_CURSO && it.textoOuNulo("fase") == "vigilancia" -> agora - 20 * minuto
                else -> null
            }
            Autorizacao(
                id = numeroDoCodigo(it.getString("id")),
                secaoId = secaoId,
                avariaId = numeroDoCodigo(it.getString("avaria")),
                tipo = tipo,
                descricao = it.getString("descricao"),
                estado = estado,
                pedidaPorId = pessoas.first { p -> p.nome == it.getString("pedidaPor") }.id,
                dataPedido = pedido,
                decididaPorId = if (estado == Autorizacao.PEDIDA) null else chefe?.id,
                dataDecisao = if (estado == Autorizacao.PEDIDA) null else pedido + 5 * minuto,
                motivoRecusa = if (estado == Autorizacao.RECUSADA) raiz.getJSONArray("motivosRecusa").getString(0) else null,
                cadeado = it.textoOuNulo("cadeado"),
                dataInicio = inicio,
                dataTrabalhoTerminado = terminado,
                encerradaPorId = if (estado == Autorizacao.ENCERRADA) chefe?.id else null,
                dataFim = if (estado == Autorizacao.ENCERRADA) {
                    minOf(terminado!! + (if (tipo == Autorizacao.QUENTE) 60 else 5) * minuto, agora)
                } else null
            )
        }

    private fun lerParagens(raiz: JSONObject): List<Paragem> =
        raiz.getJSONArray("paragens").objetos().map {
            Paragem(
                id = numeroDoCodigo(it.getString("id")),
                equipamentoId = it.getString("equipamento"),
                causa = it.getString("causa"),
                inicio = it.dataOuNula("inicioHaMin") ?: agora,
                fim = it.dataOuNula("fimHaMin"),
                avariaId = it.textoOuNulo("avaria")?.let { codigo -> numeroDoCodigo(codigo) }
            )
        }

    private fun lerParametros(raiz: JSONObject): List<Parametro> {
        val porSecao = raiz.getJSONObject("parametrosPorSecao")
        return porSecao.keys().asSequence().toList().flatMap { secaoId ->
            porSecao.getJSONArray(secaoId).objetos().map {
                Parametro(
                    id = "$secaoId-${it.getString("id")}",
                    secaoId = secaoId,
                    nome = it.getString("l"),
                    unidade = it.getString("u"),
                    minimo = it.getDouble("min"),
                    maximo = it.getDouble("max"),
                    decimais = it.getInt("dec"),
                    tipoLimite = if (it.getString("mode") == "up") Parametro.LIMITE_MAXIMO else Parametro.LIMITE_INTERVALO
                )
            }
        }
    }

    /** No JSON, cada leitura tem os valores de vários parâmetros; na base de dados fica uma linha por valor. */
    private fun lerLeituras(raiz: JSONObject): List<Leitura> =
        raiz.getJSONArray("leituras").objetos().flatMap { leitura ->
            val valores = leitura.getJSONObject("valores")
            valores.keys().asSequence().map { parametro ->
                Leitura(
                    parametroId = "${leitura.getString("secao")}-$parametro",
                    valor = valores.getDouble(parametro),
                    pessoaId = leitura.getString("porId"),
                    data = leitura.dataOuNula("haMin") ?: agora
                )
            }.toList()
        }

    /** "AV-2026-0422" → 422; "PG-0164" → 164. */
    private fun numeroDoCodigo(codigo: String): Long = codigo.substringAfterLast('-').toLong()

    /** Reconstrói a cronologia a partir das datas da avaria. */
    private fun criarCronologia(avariaId: Long, avaria: Avaria, pessoas: List<Pessoa>, equipa: List<AvariaTecnico>): List<EventoAvaria> {
        fun evento(texto: Int, pessoaId: String?, data: Long): EventoAvaria {
            val nome = pessoas.find { it.id == pessoaId }?.nome ?: ""
            return EventoAvaria(avariaId = avariaId, texto = context.getString(texto, nome), pessoaId = pessoaId, data = data)
        }

        val eventos = mutableListOf(evento(R.string.evento_reportada, avaria.reportadaPorId, avaria.dataCriacao))
        // Quem começou a intervenção: o técnico atual ou, se foi deixada em progresso, quem a deixou.
        val iniciouId = avaria.tecnicoId ?: avaria.progressoPorId
        if (avaria.dataInicio != null) eventos += evento(R.string.evento_assumida, iniciouId, avaria.dataInicio)
        equipa.filter { it.tecnicoId != avaria.tecnicoId }.forEach { eventos += evento(R.string.evento_juntou, it.tecnicoId, it.dataEntrada) }
        if (avaria.progressoData != null) {
            eventos += evento(R.string.evento_em_progresso, avaria.progressoPorId, avaria.progressoData)
        }
        if (avaria.dataFim != null) eventos += evento(R.string.evento_concluida, avaria.tecnicoId, avaria.dataFim)
        return eventos
    }

    private fun JSONArray.objetos(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    // No org.json, optString devolve o texto "null" para valores nulos; por isso verifica-se isNull.
    private fun JSONObject.textoOuNulo(chave: String): String? =
        if (isNull(chave)) null else getString(chave)

    private fun JSONObject.dataOuNula(chave: String): Long? =
        if (isNull(chave)) null else agora - getLong(chave) * 60_000

    companion object {
        /**
         * true se o carregamento falhou nesta execução; o ecrã de entrada mostra um aviso.
         * shortcut: o aviso só aparece na execução em que falhou; na abertura seguinte tenta-se carregar outra vez.
         */
        @Volatile
        var falhou = false

        private const val FICHEIRO = "dados-demo.json"
        private const val FICHEIRO_EXTRA = "dados-demo-extra.json"
        private const val TAG = "CarregadorDadosDemo"
    }
}
