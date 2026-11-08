package pt.ubi.di.pdm.finsafix.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Seguranca
import pt.ubi.di.pdm.finsafix.util.Validacao
import java.time.LocalDate
import java.util.Locale

/**
 * Único ponto de acesso aos dados para os ViewModels.
 * Aqui ficam as regras de negócio das avarias (estados, cronologia, códigos).
 */
class Repositorio(
    context: Context,
    // Os testes passam aqui uma base de dados em memória; a aplicação usa a de sempre.
    private val db: AppDatabase = AppDatabase.obter(context)
) {

    private val context = context.applicationContext
    private val avariaDao = db.avariaDao()
    private val eventoDao = db.eventoAvariaDao()
    private val paragemDao = db.paragemDao()
    private val avisoDao = db.avisoDao()
    private val autorizacaoDao = db.autorizacaoDao()
    private val pessoaDao = db.pessoaDao()
    private val registoDao = db.registoDao()
    private val instrucaoDao = db.instrucaoDao()
    private val preventivaDao = db.preventivaDao()
    private val pecaDao = db.pecaDao()
    private val equipaDao = db.avariaTecnicoDao()

    /** Resultado de tentar assumir uma intervenção. */
    enum class ResultadoAssumir { OK, JA_TEM_INTERVENCAO, JA_NAO_ESTA_PENDENTE, ATRIBUIDA_A_OUTRO }

    /** Resultado de “Juntar-me à intervenção”; cada recusa corresponde a uma regra. */
    enum class ResultadoJuntar { OK, NAO_ESTA_EM_CURSO, OUTRA_OFICINA, JA_TEM_INTERVENCAO, SEM_LOTO }

    /** Resultado de cada passo de uma autorização de trabalho (ninguém salta um estado). */
    enum class ResultadoAutorizacao {
        OK, SEM_PERMISSAO, ESTADO_INVALIDO, CHECKLIST_INCOMPLETA, PIN_INCORRETO,
        FALTA_CADEADO, VIGILANCIA_EM_CURSO, JA_EXISTE_ABERTA, FALTA_VIGILANTE, EXPIRADA
    }

    /** Tudo o que se apaga ao eliminar uma avaria, para o "Desfazer" o poder repor. */
    data class AvariaEliminada(val avaria: Avaria, val eventos: List<EventoAvaria>, val paragens: List<Paragem>, val equipa: List<AvariaTecnico> = emptyList())

    // ---------- Consultas ----------

    fun secoes(): Flow<List<Secao>> = db.secaoDao().obterTodas()
    fun equipamentosPorSecao() = db.equipamentoDao().contarPorSecao()
    suspend fun secaoDoPosto(id: String): Secao? = db.secaoDao().obterUma(id)
    fun secao(id: String): Flow<Secao?> = db.secaoDao().obter(id)
    fun equipamentosDaSecao(secaoId: String): Flow<List<Equipamento>> = db.equipamentoDao().obterPorSecao(secaoId)
    fun pessoasDoPosto(postoId: String): Flow<List<Pessoa>> = db.pessoaDao().obterDoPosto(postoId)

    fun avaria(id: Long): Flow<AvariaResumo?> = avariaDao.obterResumo(id)
    fun cronologia(avariaId: Long): Flow<List<EventoAvaria>> = eventoDao.obterDaAvaria(avariaId)
    suspend fun avariaParaEditar(id: Long): Avaria? = avariaDao.obter(id)

    fun avariasDaSecao(secaoId: String, estados: List<String>) = avariaDao.obterDaSecao(secaoId, estados)
    /** [tecnicoId]: o técnico não vê as avarias que o chefe de oficina atribuiu a outro (nulo = vê todas). */
    fun avariasDaOficina(especialidades: List<String>, estados: List<String>, tecnicoId: String? = null) =
        avariaDao.obterDaOficina(especialidades, estados, tecnicoId)
    fun avariasEmProgresso(especialidades: List<String>) = avariaDao.obterEmProgresso(especialidades)
    fun avariasDoTecnico(tecnicoId: String) = avariaDao.obterDoTecnico(tecnicoId)
    fun equipaDaAvaria(avariaId: Long) = equipaDao.obterAtuais(avariaId)
    fun equipasDasOficinas(especialidades: List<String>) = equipaDao.obterAtuaisDasEspecialidades(especialidades)
    fun eventosDasAvariasEmCurso(especialidades: List<String>) = eventoDao.obterDasAvariasEmCurso(especialidades)
    fun avariasComoAjudante(tecnicoId: String) = equipaDao.avariasComoAjudante(tecnicoId)
    fun avariasConcluidasPor(tecnicoId: String) = avariaDao.obterConcluidasDoTecnico(tecnicoId)
    fun historicoDaOficina(especialidades: List<String>) = avariaDao.obterHistoricoDaOficina(especialidades)

    fun paragensRecentes(secaoId: String?, desde: Long) = paragemDao.obterRecentes(secaoId, desde)
    fun paragemAtivaDaAvaria(avariaId: Long) = paragemDao.observarAtivaDaAvaria(avariaId)
    /** As paragens ainda em curso da secção, ou de toda a fábrica com [secaoId] nulo. */
    fun paragensAtivas(secaoId: String?) = paragemDao.obterAtivas(secaoId)

    // ---------- Criar, editar e eliminar ----------

    /**
     * Grava uma avaria nova e o evento "Reportada por".
     * O código vem do id (ex.: id 424 → AV-2026-0424). O Room cria o id com AUTOINCREMENT,
     * por isso um número nunca é reutilizado, mesmo depois de eliminar uma avaria.
     * Crítica quer dizer máquina parada: abre-se uma paragem ligada à avaria (se ainda não houver uma).
     */
    /** [abrirParagem]: numa avaria crítica, o operador diz se a máquina parou (por omissão sim). */
    suspend fun criarAvaria(avaria: Avaria, quem: Pessoa, abrirParagem: Boolean = avaria.prioridade == Avaria.CRITICA): Long = db.withTransaction {
        exigirQueNaoSejaEngenharia(quem)
        // O ecrã já limita a descrição; confirma-se aqui para que nenhum outro caminho grave uma descrição demasiado longa
        require(Validacao.descricaoValida(avaria.descricao)) { "Descrição demasiado longa" }
        val id = avariaDao.inserir(avaria.copy(codigo = ""))
        avariaDao.atualizar(avaria.copy(id = id, codigo = codigoPara(id)))
        registarEvento(id, R.string.evento_reportada, quem)
        if (avaria.prioridade == Avaria.CRITICA && abrirParagem && paragemDao.obterAtivaDoEquipamento(avaria.equipamentoId) == null) {
            paragemDao.inserir(
                Paragem(
                    equipamentoId = avaria.equipamentoId,
                    causa = context.getString(R.string.causa_avaria),
                    inicio = avaria.dataCriacao,
                    avariaId = id
                )
            )
        }
        id
    }

    suspend fun editarAvaria(avaria: Avaria, quem: Pessoa) = db.withTransaction {
        exigirQueNaoSejaEngenharia(quem)
        require(Validacao.descricaoValida(avaria.descricao)) { "Descrição demasiado longa" }
        // Só os campos do formulário, sobre a avaria atual: se o técnico a assumiu entretanto, não se perde nada
        // (e a avaria deixa de poder ser editada). Só quem a reportou edita.
        val atual = avariaDao.obter(avaria.id)
        check(atual != null && atual.podeSerAlteradaPor(quem.id)) { "A avaria já não pode ser editada" }
        avariaDao.atualizar(
            atual.copy(
                equipamentoId = avaria.equipamentoId, titulo = avaria.titulo, descricao = avaria.descricao,
                prioridade = avaria.prioridade, especialidade = avaria.especialidade
            )
        )
        registarEvento(avaria.id, R.string.evento_editada, quem)
    }

    /**
     * Elimina a avaria. Os eventos vão com ela (CASCADE); as paragens que ela abriu também se apagam,
     * porque se foi reportada por engano a máquina não esteve parada. Devolve tudo para o "Desfazer".
     */
    suspend fun eliminarAvaria(avaria: Avaria, quem: Pessoa): AvariaEliminada = db.withTransaction {
        exigirQueNaoSejaEngenharia(quem)
        // Confirma na base de dados (e não só no ecrã) que a avaria ainda não começou a ser tratada e que é de quem elimina.
        val atual = avariaDao.obter(avaria.id)
        check(atual != null && atual.podeSerAlteradaPor(quem.id)) {
            "A avaria já não pode ser eliminada"
        }
        val eliminada = AvariaEliminada(avaria, eventoDao.obterDaAvariaLista(avaria.id), paragemDao.obterDaAvaria(avaria.id), equipaDao.obterDaAvaria(avaria.id))
        paragemDao.eliminarDaAvaria(avaria.id)
        avariaDao.eliminar(avaria)
        eliminada
    }

    /** "Desfazer" da Snackbar: volta a inserir a avaria com o mesmo id, a cronologia e as paragens. */
    suspend fun restaurarAvaria(eliminada: AvariaEliminada) = db.withTransaction {
        avariaDao.inserir(eliminada.avaria)
        eventoDao.inserirTodos(eliminada.eventos)
        paragemDao.inserirTodas(eliminada.paragens)
        equipaDao.inserirTodos(eliminada.equipa)
    }

    // ---------- Intervenção do técnico ----------

    /**
     * Assumir: só se a avaria ainda estiver pendente e o técnico não tiver outra em curso.
     * [especialidade] é a classificação escolhida quando a avaria era "Não sei".
     */
    suspend fun assumir(avariaId: Long, tecnico: Pessoa, especialidade: String?): ResultadoAssumir =
        db.withTransaction {
            exigirQueNaoSejaEngenharia(tecnico)
            val avaria = avariaDao.obter(avariaId)
            when {
                avaria == null || avaria.estado != Avaria.PENDENTE -> ResultadoAssumir.JA_NAO_ESTA_PENDENTE
                avaria.atribuidaAId != null && avaria.atribuidaAId != tecnico.id -> ResultadoAssumir.ATRIBUIDA_A_OUTRO
                equipaDao.avariaAtualDoTecnico(tecnico.id) != null -> ResultadoAssumir.JA_TEM_INTERVENCAO
                else -> {
                    val agora = System.currentTimeMillis()
                    // Ao retomar um trabalho deixado em progresso, o tempo em espera não conta para a duração.
                    val tempoEmEspera = avaria.progressoData?.let { agora - it } ?: 0
                    avariaDao.atualizar(
                        avaria.copy(
                            estado = Avaria.EM_CURSO,
                            tecnicoId = tecnico.id,
                            especialidade = especialidade ?: avaria.especialidade,
                            dataInicio = avaria.dataInicio ?: agora,
                            tempoParadoMs = avaria.tempoParadoMs + tempoEmEspera,
                            progressoData = null
                        )
                    )
                    registarEvento(avariaId, R.string.evento_assumida, tecnico)
                    // Os ajudantes que o chefe já escolheu entram agora; o responsável também fica na equipa.
                    equipaDao.reiniciarEntrada(avariaId, agora)
                    equipaDao.entrar(AvariaTecnico(avariaId, tecnico.id, agora))
                    ResultadoAssumir.OK
                }
            }
        }

    /** Conclui a intervenção; com [retomarProducao], termina também a paragem que a avaria abriu. */
    suspend fun concluir(avariaId: Long, tecnico: Pessoa, retomarProducao: Boolean): Boolean = db.withTransaction {
        exigirQueNaoSejaEngenharia(tecnico)
        val avaria = avariaDao.obter(avariaId) ?: return@withTransaction false
        check(avaria.estado == Avaria.EM_CURSO && avaria.tecnicoId == tecnico.id) { "Só o técnico da intervenção pode concluir" }
        // Com uma autorização aberta (exceto a vigilância de fogo) a avaria não se conclui. Devolve false.
        if (temAutorizacaoQueBloqueia(avariaId)) return@withTransaction false
        val agora = System.currentTimeMillis()
        avariaDao.atualizar(avaria.copy(estado = Avaria.RESOLVIDA, dataFim = agora))
        equipaDao.sairTodos(avariaId, agora)
        registarEvento(avariaId, R.string.evento_concluida, tecnico)
        if (retomarProducao) {
            paragemDao.obterDaAvaria(avariaId).filter { it.fim == null }.forEach { paragemDao.terminar(it.id, agora) }
        }
        true
    }

    /** A avaria volta a Pendente, sem técnico, com a nota para quem vem a seguir. */
    suspend fun deixarEmProgresso(
        avariaId: Long,
        tecnico: Pessoa,
        ordem: String,
        retoma: String,
        motivo: String,
        nota: String
    ): Boolean = db.withTransaction {
        exigirQueNaoSejaEngenharia(tecnico)
        val avaria = avariaDao.obter(avariaId) ?: return@withTransaction false
        check(avaria.estado == Avaria.EM_CURSO && avaria.tecnicoId == tecnico.id) { "Só o técnico da intervenção a pode deixar" }
        // A autorização é de quem a pediu: com uma aberta, o técnico não sai (como ao concluir). Devolve false.
        if (temAutorizacaoQueBloqueia(avariaId)) return@withTransaction false
        equipaDao.sairTodos(avariaId, System.currentTimeMillis())
        avariaDao.atualizar(
            avaria.copy(
                estado = Avaria.PENDENTE,
                tecnicoId = null,
                progressoOrdem = ordem,
                progressoRetoma = retoma,
                progressoMotivo = motivo,
                progressoNota = nota,
                progressoPorId = tecnico.id,
                progressoData = System.currentTimeMillis()
            )
        )
        registarEvento(avariaId, R.string.evento_em_progresso, tecnico)
        true
    }

    // ---------- Equipa da intervenção ----------

    /** Pode trabalhar na avaria: é técnico da mesma oficina ou a avaria ainda é “Não sei” (qualquer oficina a pode tratar). */
    private fun Pessoa.servePara(avaria: Avaria) =
        papel == Pessoa.PAPEL_TECNICO && (especialidade == avaria.especialidade || avaria.especialidade == Avaria.NAO_SEI)

    /**
     * “Juntar-me à intervenção”: o técnico entra como ajudante. Só numa avaria em curso, da sua oficina, estando livre
     * (um técnico só pode estar numa intervenção de cada vez) e com os 3 pontos LOTO marcados.
     */
    suspend fun juntarAIntervencao(avariaId: Long, tecnico: Pessoa, lotoConfirmado: Boolean): ResultadoJuntar = db.withTransaction {
        exigirQueNaoSejaEngenharia(tecnico)
        val real = pessoaReal(tecnico)
        val avaria = avariaDao.obter(avariaId)
        when {
            avaria == null || avaria.estado != Avaria.EM_CURSO -> ResultadoJuntar.NAO_ESTA_EM_CURSO
            !real.servePara(avaria) -> ResultadoJuntar.OUTRA_OFICINA
            equipaDao.avariaAtualDoTecnico(real.id) != null -> ResultadoJuntar.JA_TEM_INTERVENCAO
            !lotoConfirmado -> ResultadoJuntar.SEM_LOTO
            else -> {
                equipaDao.entrar(AvariaTecnico(avariaId, real.id, System.currentTimeMillis()))
                registarEvento(avariaId, R.string.evento_juntou, real)
                ResultadoJuntar.OK
            }
        }
    }

    /** O ajudante sai da intervenção. O responsável não sai por aqui: usa Concluir ou Deixar em progresso. */
    suspend fun sairDaIntervencao(avariaId: Long, tecnico: Pessoa): Boolean = db.withTransaction {
        exigirQueNaoSejaEngenharia(tecnico)
        val avaria = avariaDao.obter(avariaId) ?: return@withTransaction false
        if (avaria.estado != Avaria.EM_CURSO || avaria.tecnicoId == tecnico.id) return@withTransaction false
        if (equipaDao.sair(avariaId, tecnico.id, System.currentTimeMillis()) == 0) return@withTransaction false
        registarEvento(avariaId, R.string.evento_saiu, tecnico)
        true
    }

    // ---------- Paragens ----------

    /** Regista uma paragem. Devolve false se o equipamento já estiver parado (não há duas paragens ativas). */
    suspend fun registarParagem(equipamentoId: String, causa: String, quem: Pessoa): Boolean = db.withTransaction {
        exigirQueNaoSejaEngenharia(quem)
        if (paragemDao.obterAtivaDoEquipamento(equipamentoId) != null) return@withTransaction false
        paragemDao.inserir(Paragem(equipamentoId = equipamentoId, causa = causa, inicio = System.currentTimeMillis()))
        true
    }

    suspend fun retomarProducao(paragemId: Long, quem: Pessoa) = db.withTransaction {
        exigirQueNaoSejaEngenharia(quem)
        // A mesma regra do ecrã, verificada aqui: com a avaria por resolver, a máquina não volta a produzir.
        val paragem = checkNotNull(paragemDao.obter(paragemId))
        val avaria = paragem.avariaId?.let { avariaDao.obter(it) }
        check(avaria == null || avaria.estado == Avaria.RESOLVIDA) { "A avaria ainda não está resolvida" }
        paragemDao.terminar(paragemId, System.currentTimeMillis())
    }

    // ---------- Parâmetros ----------

    fun parametrosDaSecao(secaoId: String) = db.parametroDao().obterComUltimaLeitura(secaoId)
    fun leiturasDaSecao(secaoId: String) = db.parametroDao().obterTodasDaSecao(secaoId)
    fun todosOsParametros() = db.parametroDao().obterComUltimaLeitura(null)
    fun ultimasLeituras(secaoId: String) = db.parametroDao().obterUltimas(secaoId)

    suspend fun registarLeitura(parametroId: String, valor: Double, quem: Pessoa) {
        exigirQueNaoSejaEngenharia(quem)
        db.leituraDao().inserir(Leitura(parametroId = parametroId, valor = valor, pessoaId = quem.id, data = System.currentTimeMillis()))
    }

    // ---------- Produção ----------

    fun producaoDoTurno(secaoId: String, turno: String, desde: Long) = db.producaoDao().obterDoTurno(secaoId, turno, desde)

    suspend fun registarProducao(equipamentoId: String, turno: String, quantidade: Double, quem: Pessoa, cortes: Int? = null) {
        require(Validacao.quantidadeValida(quantidade)) { "Quantidade inválida" }
        exigirQueNaoSejaEngenharia(quem)
        db.producaoDao().inserir(
            Producao(equipamentoId = equipamentoId, turno = turno, quantidade = quantidade, pessoaId = quem.id, data = System.currentTimeMillis(), cortes = cortes)
        )
    }

    // ---------- Plano do turno ----------

    fun producaoDesde(secaoId: String?, desde: Long) = db.producaoDao().obterDesde(secaoId, desde)

    fun ordensDoTurno(secaoId: String, turno: String) = db.ordemProducaoDao().obterDoTurno(secaoId, turno)

    /**
     * O chefe da secção guarda os ajustes ao plano: [ajustes] = variação por ordem (−5, −1, +1, +5 somados).
     * O plano nunca fica negativo. Só mexe nas ordens da secção do chefe.
     */
    suspend fun ajustarPlano(ajustes: Map<Long, Double>, chefe: Pessoa): Boolean = db.withTransaction {
        var algum = false
        for ((id, variacao) in ajustes) {
            val ordem = db.ordemProducaoDao().obter(id) ?: continue
            if (variacao == 0.0 || !eChefeDaSecao(chefe, ordem.secaoId)) continue
            db.ordemProducaoDao().atualizar(
                ordem.copy(planeado = (ordem.planeado + variacao).coerceAtLeast(0.0), ajustadaPorId = chefe.id, dataAjuste = System.currentTimeMillis())
            )
            algum = true
        }
        algum
    }

    // ---------- Avisos ----------

    fun avisoUrgente(secaoId: String) = avisoDao.obterUrgente(secaoId)
    fun avisosRecentes(secaoId: String) = avisoDao.obterRecentes(secaoId)

    /**
     * O técnico avisa a secção do equipamento da avaria ([tipo] = PARAR ou ARRANCAR).
     * Só quem tem a intervenção em curso avisa. Devolve false se já havia um aviso igual por responder.
     */
    suspend fun avisarSecao(avariaId: Long, tipo: String, tecnico: Pessoa): Boolean = db.withTransaction {
        exigirQueNaoSejaEngenharia(tecnico)
        val avaria = checkNotNull(avariaDao.obter(avariaId))
        check(avaria.estado == Avaria.EM_CURSO && avaria.tecnicoId == tecnico.id) { "Só o técnico da intervenção pode avisar a secção" }
        if (avisoDao.contarPorResponder(avariaId, tipo) > 0) return@withTransaction false
        val secaoId = checkNotNull(db.equipamentoDao().obterSecaoId(avaria.equipamentoId))
        avisoDao.inserir(
            Aviso(
                secaoId = secaoId, tipo = tipo, equipamentoId = avaria.equipamentoId, avariaId = avariaId,
                criadoPorId = tecnico.id, dataCriacao = System.currentTimeMillis()
            )
        )
        true
    }

    /** O operador avisa o chefe de um parâmetro fora do limite. Fica na lista de avisos, sem banner. */
    suspend fun avisarChefe(secaoId: String, texto: String, quem: Pessoa) {
        exigirQueNaoSejaEngenharia(quem)
        avisoDao.inserir(
            Aviso(secaoId = secaoId, tipo = Aviso.FORA_LIMITE, texto = texto, criadoPorId = quem.id, dataCriacao = System.currentTimeMillis())
        )
    }

    /**
     * Responde ao aviso urgente ("Máquina parada" ou "Percebido"). Devolve false se outra pessoa já respondeu.
     * "Máquina parada" regista também a paragem do equipamento, ligada à avaria, mas só se a avaria ainda estiver
     * em curso e o equipamento ainda não estiver parado (o banner pode ter ficado esquecido depois de a avaria acabar).
     */
    suspend fun responderAviso(avisoId: Long, quem: Pessoa): Boolean = db.withTransaction {
        val aviso = avisoDao.obter(avisoId) ?: return@withTransaction false
        if (aviso.dataResposta != null) return@withTransaction false
        if (pessoaDao.obter(quem.id)?.postoId != aviso.secaoId) return@withTransaction false // só quem é da secção
        val agora = System.currentTimeMillis()
        avisoDao.atualizar(aviso.copy(respondidoPorId = quem.id, dataResposta = agora))
        val avaria = aviso.avariaId?.let { avariaDao.obter(it) }
        val aindaEmCurso = avaria?.estado == Avaria.EM_CURSO
        if (aviso.tipo == Aviso.PARAR && aviso.equipamentoId != null && aindaEmCurso &&
            paragemDao.obterAtivaDoEquipamento(aviso.equipamentoId) == null
        ) {
            paragemDao.inserir(
                Paragem(
                    equipamentoId = aviso.equipamentoId, causa = context.getString(R.string.causa_pedido_tecnico),
                    inicio = agora, avariaId = aviso.avariaId
                )
            )
        }
        true
    }

    // ---------- Chefe de secção e autorizações de trabalho ----------

    /**
     * Entrada pelo cadeado. O chefe (de secção ou de oficina) só entra no tablet do seu posto; a engenharia entra
     * em qualquer tablet. Os PINs só existem como hash.
     */
    suspend fun autenticarChefia(postoId: String, pin: String): Pessoa? =
        (pessoaDao.obterChefesDoPosto(postoId) + pessoaDao.obterEngenheiros())
            .firstOrNull { it.pinConfere(pin) }

    // ---------- Consultas da engenharia e do chefe de oficina (só leitura) ----------

    /**
     * As avarias que [quem] pode consultar: a engenharia vê toda a fábrica (e filtra por [secaoId]); o chefe de
     * oficina vê só a sua especialidade ([secaoId] é ignorado: não pode alargar o que vê). Os outros perfis: vazio.
     */
    fun avariasDeConsulta(quem: Pessoa, secaoId: String?, estados: List<String>): Flow<List<AvariaResumo>> =
        consultaPara(quem) {
            val especialidade = it.especialidade
            when {
                it.papel == Pessoa.PAPEL_ENGENHEIRO -> avariaDao.obterDaFabrica(secaoId, estados)
                it.eChefeDeOficina() && especialidade != null -> avariaDao.obterDasEspecialidades(listOf(especialidade, Avaria.NAO_SEI), estados)
                else -> null
            }
        }

    /** MTBF e MTTR por equipamento (só a engenharia). */
    fun indicadores(quem: Pessoa): Flow<List<Indicadores.PorEquipamento>> = consultaPara(quem) {
        if (it.papel == Pessoa.PAPEL_ENGENHEIRO) avariaDao.obterDaFabrica(null, Avaria.ESTADOS).map(Indicadores::porEquipamento)
        else null
    }

    /** Relê a pessoa da base de dados (não se confia no ecrã); se [fonte] devolver null, a lista vem vazia. */
    private fun <T> consultaPara(quem: Pessoa, fonte: (Pessoa) -> Flow<List<T>>?): Flow<List<T>> = flow {
        val origem = pessoaDao.obter(quem.id)?.let(fonte)
        if (origem == null) emit(emptyList()) else emitAll(origem)
    }

    fun autorizacaoDaAvaria(avariaId: Long) = autorizacaoDao.obterAtualDaAvaria(avariaId)
    fun todosOsTecnicos() = db.pessoaDao().observarTecnicos()
    fun todasAsAutorizacoes() = autorizacaoDao.obterTodas()
    fun autorizacoesAbertasDaSecao(secaoId: String) = autorizacaoDao.obterAbertasDaSecao(secaoId)
    fun contarAutorizacoesPorTratar(secaoId: String) = autorizacaoDao.contarPorTratar(secaoId)
    fun avisosPorResponder(secaoId: String) = avisoDao.obterPorResponder(secaoId)

    /** O técnico da intervenção em curso pede uma autorização. Só há uma aberta de cada vez por avaria. */
    suspend fun pedirAutorizacao(avariaId: Long, tipo: String, descricao: String, tecnico: Pessoa, duracaoMin: Int = 60): ResultadoAutorizacao =
        db.withTransaction {
            val avaria = checkNotNull(avariaDao.obter(avariaId))
            // [tipo] pode ter vários tipos separados por vírgula
            require(tipo.split(",").all { it in Autorizacao.TIPOS } && Validacao.descricaoAutorizacaoValida(descricao)) { "Pedido inválido" }
            if (avaria.estado != Avaria.EM_CURSO || avaria.tecnicoId != tecnico.id) return@withTransaction ResultadoAutorizacao.SEM_PERMISSAO
            if (autorizacaoDao.obterAbertasDaAvaria(avariaId).isNotEmpty()) return@withTransaction ResultadoAutorizacao.JA_EXISTE_ABERTA
            autorizacaoDao.inserir(
                Autorizacao(
                    secaoId = checkNotNull(db.equipamentoDao().obterSecaoId(avaria.equipamentoId)), avariaId = avariaId,
                    tipo = tipo, descricao = descricao.trim(), estado = Autorizacao.PEDIDA,
                    pedidaPorId = tecnico.id, dataPedido = System.currentTimeMillis(), duracaoMin = duracaoMin
                )
            )
            registarEvento(avariaId, R.string.evento_aut_pedida, tecnico)
            ResultadoAutorizacao.OK
        }

    /** O chefe da secção do equipamento aprova, com a checklist toda confirmada e o seu PIN. */
    suspend fun aprovarAutorizacao(id: Long, chefe: Pessoa, pin: String, checklistCompleta: Boolean, vigilante: String? = null): ResultadoAutorizacao =
        db.withTransaction {
            val aut = autorizacaoDao.obter(id) ?: return@withTransaction ResultadoAutorizacao.ESTADO_INVALIDO
            if (!eChefeDaSecao(chefe, aut.secaoId)) return@withTransaction ResultadoAutorizacao.SEM_PERMISSAO
            if (aut.estado != Autorizacao.PEDIDA) return@withTransaction ResultadoAutorizacao.ESTADO_INVALIDO
            if (!checklistCompleta) return@withTransaction ResultadoAutorizacao.CHECKLIST_INCOMPLETA
            // O trabalho a quente precisa do nome de quem vigia o fogo
            if (aut.temQuente() && vigilante.isNullOrBlank()) return@withTransaction ResultadoAutorizacao.FALTA_VIGILANTE
            if (!pinCorreto(chefe.id, pin)) return@withTransaction ResultadoAutorizacao.PIN_INCORRETO
            autorizacaoDao.atualizar(
                aut.copy(estado = Autorizacao.APROVADA, decididaPorId = chefe.id, dataDecisao = System.currentTimeMillis(), vigilante = vigilante?.trim(),
                    validaAte = Datas.fimDoTurnoAtual()) // vale até ao fim do turno; depois é preciso renovar
            )
            registarEvento(aut.avariaId, R.string.evento_aut_aprovada, chefe)
            ResultadoAutorizacao.OK
        }

    suspend fun recusarAutorizacao(id: Long, chefe: Pessoa, motivo: String): ResultadoAutorizacao = db.withTransaction {
        require(motivo.isNotBlank()) { "O motivo é obrigatório" }
        val aut = autorizacaoDao.obter(id) ?: return@withTransaction ResultadoAutorizacao.ESTADO_INVALIDO
        if (!eChefeDaSecao(chefe, aut.secaoId)) return@withTransaction ResultadoAutorizacao.SEM_PERMISSAO
        if (aut.estado != Autorizacao.PEDIDA) return@withTransaction ResultadoAutorizacao.ESTADO_INVALIDO
        autorizacaoDao.atualizar(
            aut.copy(estado = Autorizacao.RECUSADA, decididaPorId = chefe.id, dataDecisao = System.currentTimeMillis(), motivoRecusa = motivo)
        )
        registarEvento(aut.avariaId, R.string.evento_aut_recusada, chefe)
        ResultadoAutorizacao.OK
    }

    /** O técnico começa o trabalho (aprovada → em curso). Na consignação e no bloqueio escreve o número do cadeado. */
    suspend fun iniciarTrabalho(id: Long, tecnico: Pessoa, cadeado: String?): ResultadoAutorizacao = db.withTransaction {
        val aut = autorizacaoDao.obter(id) ?: return@withTransaction ResultadoAutorizacao.ESTADO_INVALIDO
        if (!eTecnicoDaIntervencao(aut, tecnico)) return@withTransaction ResultadoAutorizacao.SEM_PERMISSAO
        if (aut.estado != Autorizacao.APROVADA) return@withTransaction ResultadoAutorizacao.ESTADO_INVALIDO
        if (aut.expirada(System.currentTimeMillis())) return@withTransaction ResultadoAutorizacao.EXPIRADA
        if (aut.exigeCadeado() && cadeado.isNullOrBlank()) return@withTransaction ResultadoAutorizacao.FALTA_CADEADO
        autorizacaoDao.atualizar(
            aut.copy(estado = Autorizacao.EM_CURSO, cadeado = cadeado?.trim(), dataInicio = System.currentTimeMillis())
        )
        registarEvento(aut.avariaId, R.string.evento_aut_iniciada, tecnico)
        ResultadoAutorizacao.OK
    }

    /** O técnico acabou o trabalho: fica a aguardar que o chefe encerre (no trabalho a quente, depois da vigilância). */
    suspend fun terminarTrabalho(id: Long, tecnico: Pessoa): ResultadoAutorizacao = db.withTransaction {
        val aut = autorizacaoDao.obter(id) ?: return@withTransaction ResultadoAutorizacao.ESTADO_INVALIDO
        if (!eTecnicoDaIntervencao(aut, tecnico)) return@withTransaction ResultadoAutorizacao.SEM_PERMISSAO
        if (aut.estado != Autorizacao.EM_CURSO || aut.dataTrabalhoTerminado != null) return@withTransaction ResultadoAutorizacao.ESTADO_INVALIDO
        autorizacaoDao.atualizar(aut.copy(dataTrabalhoTerminado = System.currentTimeMillis()))
        registarEvento(aut.avariaId, R.string.evento_aut_terminada, tecnico)
        ResultadoAutorizacao.OK
    }

    /**
     * O chefe encerra, com o seu PIN. Na consignação e no bloqueio isto autoriza o regresso da energia:
     * os operadores recebem o aviso "Máquina pode voltar a arrancar".
     */
    suspend fun encerrarAutorizacao(id: Long, chefe: Pessoa, pin: String, checklistCompleta: Boolean): ResultadoAutorizacao =
        db.withTransaction {
        val aut = autorizacaoDao.obter(id) ?: return@withTransaction ResultadoAutorizacao.ESTADO_INVALIDO
        if (!eChefeDaSecao(chefe, aut.secaoId)) return@withTransaction ResultadoAutorizacao.SEM_PERMISSAO
        if (aut.estado != Autorizacao.EM_CURSO || aut.dataTrabalhoTerminado == null) return@withTransaction ResultadoAutorizacao.ESTADO_INVALIDO
        val agora = System.currentTimeMillis()
        if (aut.vigilanciaRestanteMs(agora) > 0) return@withTransaction ResultadoAutorizacao.VIGILANCIA_EM_CURSO
        if (!checklistCompleta) return@withTransaction ResultadoAutorizacao.CHECKLIST_INCOMPLETA
        if (!pinCorreto(chefe.id, pin)) return@withTransaction ResultadoAutorizacao.PIN_INCORRETO
        autorizacaoDao.atualizar(aut.copy(estado = Autorizacao.ENCERRADA, encerradaPorId = chefe.id, dataFim = agora))
        if (aut.exigeCadeado()) {
            val avaria = checkNotNull(avariaDao.obter(aut.avariaId))
            avisoDao.inserir(
                Aviso(
                    secaoId = aut.secaoId, tipo = Aviso.ARRANCAR, equipamentoId = avaria.equipamentoId,
                    avariaId = aut.avariaId, criadoPorId = chefe.id, dataCriacao = agora
                )
            )
        }
        registarEvento(aut.avariaId, R.string.evento_aut_encerrada, chefe)
        ResultadoAutorizacao.OK
    }

    // A engenharia só consulta. O papel é relido da base de dados: não se confia no objeto que vem do ecrã.
    private suspend fun exigirQueNaoSejaEngenharia(quem: Pessoa) {
        val real = pessoaDao.obter(quem.id)
        check(real != null && real.papel != Pessoa.PAPEL_ENGENHEIRO) { "A engenharia só consulta" }
    }

    private fun Pessoa.pinConfere(pin: String) = pinHash != null && Seguranca.hashPin(id, pin) == pinHash

    private suspend fun temAutorizacaoQueBloqueia(avariaId: Long): Boolean =
        autorizacaoDao.obterAbertasDaAvaria(avariaId).any { it.bloqueiaConclusao() }

    // ---------- Pedidos, notas, receção do turno e rondas (tabela genérica Registo) ----------

    fun pedidosDaSecao(secaoId: String) = registoDao.obterDaSecao(secaoId, listOf(Registo.AJUDA, Registo.MATERIAL))
    fun notasDoPosto(secaoId: String) = registoDao.obterDaSecao(secaoId, listOf(Registo.NOTA))
    fun notasParaChefe(secaoId: String, pessoaId: String) = registoDao.obterNotasParaChefe(secaoId, pessoaId)
    fun autorizacoesAtivasDasOficinas(especialidades: List<String>) = autorizacaoDao.obterAtivasDasEspecialidades(especialidades)
    fun rececoesDoPosto(secaoId: String) = registoDao.obterDaSecao(secaoId, listOf(Registo.RECECAO))
    fun rondasDa(especialidade: String) = registoDao.obterRondas(especialidade)
    fun checklistsDaSecao(secaoId: String) = registoDao.obterDaSecao(secaoId, listOf(Registo.CHECKLIST))
    fun contarChecklistsDoTurno(secaoId: String) = registoDao.contarChecklists(secaoId, Datas.inicioDoTurnoAtual())
    fun contarPorValidar(secaoId: String) = registoDao.contarPorValidar(secaoId)
    fun contarPedidosEmAberto(secaoId: String) = registoDao.contarPedidosEmAberto(secaoId)
    fun contarPedidosPorFechar(secaoId: String) = registoDao.contarPedidosPorFechar(secaoId)
    fun contarNotas(secaoId: String, pessoaId: String) = registoDao.contarNotas(secaoId, pessoaId)

    /** O operador pede ajuda (a [destino]) ou material à sua secção. */
    suspend fun criarPedido(tipo: String, destino: String?, texto: String, urgente: Boolean, quem: Pessoa): Long = db.withTransaction {
        val real = pessoaReal(quem)
        check(real.papel == Pessoa.PAPEL_OPERADOR && real.postoId != null) { "Só o operador faz pedidos" }
        require((tipo == Registo.AJUDA && destino in Registo.DESTINOS) || (tipo == Registo.MATERIAL && texto.isNotBlank())) { "Pedido inválido" }
        registoDao.inserir(
            Registo(
                tipo = tipo, secaoId = checkNotNull(real.postoId), pessoaId = real.id, texto = texto.trim(), extra = destino,
                urgente = urgente, estado = Registo.ABERTO, dataCriacao = System.currentTimeMillis()
            )
        )
    }

    /** O chefe da secção responde a um pedido de ajuda ("Vou já aí"). Devolve false se não puder. */
    suspend fun responderPedido(id: Long, resposta: String, quem: Pessoa): Boolean = db.withTransaction {
        require(resposta.isNotBlank()) { "A resposta é obrigatória" }
        val registo = registoDao.obter(id) ?: return@withTransaction false
        if (registo.tipo != Registo.AJUDA || registo.estado !in listOf(Registo.ABERTO, Registo.A_CAMINHO)) return@withTransaction false
        if (!eChefeDaSecao(quem, registo.secaoId)) return@withTransaction false
        registoDao.atualizar(
            registo.copy(estado = Registo.A_CAMINHO, tratadoPorId = quem.id, resposta = resposta.trim(), dataTratamento = System.currentTimeMillis())
        )
        true
    }

    suspend fun fecharPedido(id: Long, quem: Pessoa): Boolean = db.withTransaction {
        val registo = registoDao.obter(id) ?: return@withTransaction false
        if (registo.tipo != Registo.AJUDA || registo.estado == Registo.FECHADO || !eChefeDaSecao(quem, registo.secaoId)) return@withTransaction false
        registoDao.atualizar(registo.copy(estado = Registo.FECHADO, tratadoPorId = quem.id, dataTratamento = System.currentTimeMillis()))
        true
    }

    /** Quem fez o pedido diz que já foi atendido ("Já fui atendido") ou que o material chegou. */
    suspend fun confirmarPedido(id: Long, quem: Pessoa): Boolean = db.withTransaction {
        val registo = registoDao.obter(id) ?: return@withTransaction false
        val aberto = registo.estado == Registo.ABERTO || registo.estado == Registo.A_CAMINHO
        if (registo.pessoaId != quem.id || !aberto) return@withTransaction false
        registoDao.atualizar(registo.copy(estado = if (registo.tipo == Registo.AJUDA) Registo.ATENDIDO else Registo.RECEBIDO))
        true
    }

    /** Nota para o turno seguinte (qualquer pessoa de um posto, menos a engenharia). A de um chefe de oficina é para o chefe seguinte. */
    suspend fun criarNota(categoria: String, texto: String, quem: Pessoa) = db.withTransaction {
        val real = pessoaReal(quem)
        check(real.papel != Pessoa.PAPEL_ENGENHEIRO && real.postoId != null) { "Só quem trabalha num posto escreve notas" }
        require(categoria in Registo.CATEGORIAS_NOTA && Validacao.notaValida(texto)) { "Nota inválida" }
        registoDao.inserir(
            Registo(
                tipo = Registo.NOTA, secaoId = checkNotNull(real.postoId), pessoaId = real.id, texto = texto.trim(), extra = categoria,
                resposta = Registo.DESTINO_CHEFE.takeIf { real.eChefeDeOficina() },
                estado = Registo.ATIVA, dataCriacao = System.currentTimeMillis()
            )
        )
    }

    /** "Li as notas": uma só vez por pessoa e por turno. Devolve false se já tinha confirmado. */
    suspend fun confirmarRececao(quem: Pessoa): Boolean = db.withTransaction {
        val real = pessoaReal(quem)
        check(real.papel != Pessoa.PAPEL_ENGENHEIRO && real.postoId != null) { "Só quem trabalha num posto confirma a receção" }
        val posto = checkNotNull(real.postoId)
        if (registoDao.contarRececoes(posto, real.id, Datas.inicioDoTurnoAtual()) > 0) return@withTransaction false
        val agora = System.currentTimeMillis()
        // O chefe de oficina não tem ninguém acima para a confirmar: a receção dele fica logo confirmada
        val chefe = real.eChefeDeOficina()
        registoDao.inserir(
            Registo(
                tipo = Registo.RECECAO, secaoId = posto, pessoaId = real.id, texto = "", extra = Datas.turnoAtual(),
                estado = if (chefe) Registo.CONFIRMADA else Registo.POR_CONFIRMAR, dataCriacao = agora,
                tratadoPorId = real.id.takeIf { chefe }, dataTratamento = agora.takeIf { chefe }
            )
        )
        true
    }

    /**
     * Checklist de arranque do turno: o operador responde OK ou Não OK a cada ponto. Uma por turno e secção
     * (devolve false se já foi feita). Todos OK = conforme; qualquer Não OK fica por validar pelo chefe.
     */
    suspend fun registarChecklist(resultados: List<Boolean>, quem: Pessoa): Boolean = db.withTransaction {
        val real = pessoaReal(quem)
        val posto = real.postoId
        check(real.papel == Pessoa.PAPEL_OPERADOR && posto != null) { "Só o operador faz a checklist de arranque" }
        require(resultados.size == PONTOS_CHECKLIST) { "A checklist tem $PONTOS_CHECKLIST pontos" }
        if (registoDao.contarChecklists(posto, Datas.inicioDoTurnoAtual()).first() > 0) return@withTransaction false
        registoDao.inserir(
            Registo(
                tipo = Registo.CHECKLIST, secaoId = posto, pessoaId = real.id, texto = "", extra = resultados.joinToString(",") { if (it) "1" else "0" },
                estado = if (resultados.all { it }) Registo.CONFORME else Registo.NAO_CONFORME, dataCriacao = System.currentTimeMillis()
            )
        )
        true
    }

    // ---------- Distribuir avarias e renovar autorizações ----------

    /**
     * O chefe de oficina atribui uma avaria pendente da sua especialidade a um técnico da mesma especialidade (a
     * engenharia atribui as já classificadas); a avaria só aparece a esse técnico. [tecnicoId] nulo tira a atribuição.
     */
    suspend fun atribuirAvaria(avariaId: Long, tecnicoId: String?, chefe: Pessoa): Boolean = atribuirEquipa(avariaId, tecnicoId, emptyList(), chefe)

    /**
     * Como [atribuirAvaria], mas com uma equipa: um responsável e, se quiser, ajudantes (todos da oficina da avaria e livres).
     * Os ajudantes ficam na tabela da equipa e passam a ver a avaria; entram na intervenção quando o responsável a assumir.
     */
    suspend fun atribuirEquipa(avariaId: Long, responsavelId: String?, ajudantesIds: List<String>, chefe: Pessoa): Boolean = db.withTransaction {
        val real = pessoaDao.obter(chefe.id) ?: return@withTransaction false
        val avaria = avariaDao.obter(avariaId) ?: return@withTransaction false
        if (avaria.estado != Avaria.PENDENTE) return@withTransaction false
        val daOficina = real.eChefeDeOficina() && avaria.especialidade == real.especialidade
        val daEngenharia = real.papel == Pessoa.PAPEL_ENGENHEIRO && avaria.especialidade != Avaria.NAO_SEI
        if (!daOficina && !daEngenharia) return@withTransaction false
        val ajudantes = ajudantesIds.distinct()
        if ((responsavelId == null && ajudantes.isNotEmpty()) || responsavelId in ajudantes) return@withTransaction false
        val responsavel = responsavelId?.let { pessoaDao.obter(it) ?: return@withTransaction false }
        val pessoasAjudantes = ajudantes.map { pessoaDao.obter(it) ?: return@withTransaction false }
        val tecnicos = listOfNotNull(responsavel) + pessoasAjudantes
        if (tecnicos.any { it.papel != Pessoa.PAPEL_TECNICO || it.especialidade != avaria.especialidade }) return@withTransaction false
        // Um ajudante que já está noutra intervenção não pode ser escolhido
        if (ajudantes.any { equipaDao.avariaAtualDoTecnico(it) != null }) return@withTransaction false

        val agora = System.currentTimeMillis()
        avariaDao.atualizar(avaria.copy(atribuidaAId = responsavelId))
        equipaDao.apagarAtuais(avariaId)
        equipaDao.inserirTodos(ajudantes.map { AvariaTecnico(avariaId, it, agora) })
        registarEventoTexto(avariaId, context.getString(R.string.evento_atribuida, real.nome, responsavel?.nome ?: "—"), real)
        if (ajudantes.isNotEmpty()) {
            registarEventoTexto(avariaId, context.getString(R.string.evento_atribuida_ajudantes, real.nome, pessoasAjudantes.joinToString(", ") { it.nome }), real)
        }
        true
    }

    /** O chefe da secção renova uma autorização que passou do fim do turno em que foi aprovada. */
    suspend fun renovarAutorizacao(id: Long, chefe: Pessoa): Boolean = db.withTransaction {
        val aut = autorizacaoDao.obter(id) ?: return@withTransaction false
        if (!eChefeDaSecao(chefe, aut.secaoId) || !aut.expirada(System.currentTimeMillis())) return@withTransaction false
        autorizacaoDao.atualizar(aut.copy(validaAte = Datas.fimDoTurnoAtual()))
        registarEvento(aut.avariaId, R.string.evento_aut_renovada, chefe)
        true
    }

    // ---------- Planos da engenharia: preventivas, mínimos de stock e instruções ----------

    /** A engenharia cria um plano de preventiva; a contagem das horas começa agora. */
    suspend fun criarPreventiva(equipamentoId: String, titulo: String, especialidade: String, intervaloH: Int, quem: Pessoa) = db.withTransaction {
        engenheiro(quem)
        require(titulo.trim().length >= 5 && intervaloH in 1..100_000 && especialidade in listOf(Avaria.MECANICA, Avaria.ELETRICA)) { "Plano inválido" }
        preventivaDao.inserir(
            Preventiva(equipamentoId = equipamentoId, titulo = titulo.trim(), especialidade = especialidade, intervaloH = intervaloH, ultimaData = System.currentTimeMillis())
        )
    }

    /** A engenharia muda o stock mínimo de uma peça (−1 / +1); nunca fica abaixo de 1. */
    suspend fun ajustarMinimo(pecaId: String, variacao: Int, quem: Pessoa): Boolean = db.withTransaction {
        engenheiro(quem)
        val peca = pecaDao.obter(pecaId) ?: return@withTransaction false
        pecaDao.atualizar(peca.copy(minimo = (peca.minimo + variacao).coerceAtLeast(1)))
        true
    }

    /** A engenharia aprova uma instrução ou pede a correção (só as que estão por validar ou com correção pedida). */
    suspend fun validarInstrucao(id: String, aprovar: Boolean, quem: Pessoa): Boolean = db.withTransaction {
        val eng = engenheiro(quem)
        val instrucao = instrucaoDao.obter(id) ?: return@withTransaction false
        if (instrucao.estado == Instrucao.VALIDADA && aprovar) return@withTransaction false
        instrucaoDao.atualizar(
            if (aprovar) instrucao.copy(estado = Instrucao.VALIDADA, validadaPorId = eng.id, dataValidacao = System.currentTimeMillis())
            else instrucao.copy(estado = Instrucao.CORRECAO, validadaPorId = null, dataValidacao = null)
        )
        true
    }

    /** O chefe da secção vê uma checklist com Não OK. */
    suspend fun validarChecklist(id: Long, chefe: Pessoa): Boolean = db.withTransaction {
        val registo = registoDao.obter(id) ?: return@withTransaction false
        if (registo.tipo != Registo.CHECKLIST || registo.estado != Registo.NAO_CONFORME || !eChefeDaSecao(chefe, registo.secaoId)) return@withTransaction false
        registoDao.atualizar(registo.copy(estado = Registo.VALIDADA, tratadoPorId = chefe.id, dataTratamento = System.currentTimeMillis()))
        true
    }

    /** Passagem de turno: o chefe da secção (ou da oficina) confirma a receção de quem chegou. */
    suspend fun confirmarPassagem(id: Long, chefe: Pessoa): Boolean = db.withTransaction {
        val registo = registoDao.obter(id) ?: return@withTransaction false
        if (registo.tipo != Registo.RECECAO || registo.estado != Registo.POR_CONFIRMAR || !eChefeDaSecao(chefe, registo.secaoId)) return@withTransaction false
        registoDao.atualizar(registo.copy(estado = Registo.CONFIRMADA, tratadoPorId = chefe.id, dataTratamento = System.currentTimeMillis()))
        true
    }

    /** O chefe de oficina valida uma ronda não conforme feita por um técnico da sua especialidade. */
    suspend fun validarRonda(id: Long, chefe: Pessoa): Boolean = db.withTransaction {
        val real = pessoaDao.obter(chefe.id) ?: return@withTransaction false
        val registo = registoDao.obter(id) ?: return@withTransaction false
        val tecnico = pessoaDao.obter(registo.pessoaId)
        if (registo.tipo != Registo.RONDA || registo.estado != Registo.NAO_CONFORME) return@withTransaction false
        if (!real.eChefeDeOficina() || real.especialidade != tecnico?.especialidade) return@withTransaction false
        registoDao.atualizar(registo.copy(estado = Registo.VALIDADA, tratadoPorId = real.id, dataTratamento = System.currentTimeMillis()))
        true
    }

    /** Ronda do técnico a uma secção: os pontos não conformes (vazio = tudo conforme) e uma observação. */
    suspend fun registarRonda(secaoId: String, naoConformes: List<String>, observacao: String, quem: Pessoa) = db.withTransaction {
        val real = pessoaReal(quem)
        val pontos = Registo.PONTOS_RONDA[real.especialidade]
        check(real.papel == Pessoa.PAPEL_TECNICO && pontos != null) { "Só o técnico faz rondas" }
        require(naoConformes.all { it in pontos }) { "Ponto de ronda inválido" }
        registoDao.inserir(
            Registo(
                tipo = Registo.RONDA, secaoId = secaoId, pessoaId = real.id, texto = observacao.trim(),
                extra = naoConformes.joinToString("; ").ifEmpty { null },
                estado = if (naoConformes.isEmpty()) Registo.CONFORME else Registo.NAO_CONFORME, dataCriacao = System.currentTimeMillis()
            )
        )
    }

    // ---------- Instruções, preventiva e stock ----------

    fun instrucoesDaSecao(secaoId: String) = instrucaoDao.obterDaSecao(secaoId)
    fun todasAsInstrucoes() = instrucaoDao.obterTodas()
    fun todosOsEquipamentos() = db.equipamentoDao().observarTodos()
    fun contarInstrucoesPorValidar() = instrucaoDao.contarPorValidar()
    fun preventivas() = preventivaDao.obterTodas()
    fun pecas() = pecaDao.obterTodas()

    /** "Registar execução": o técnico da mesma especialidade fecha o ciclo da preventiva (recomeça a contagem). */
    suspend fun registarExecucao(preventivaId: Long, quem: Pessoa): Boolean = db.withTransaction {
        val real = pessoaReal(quem)
        check(real.papel == Pessoa.PAPEL_TECNICO) { "Só o técnico regista execuções" }
        val preventiva = preventivaDao.obter(preventivaId) ?: return@withTransaction false
        if (preventiva.especialidade != real.especialidade) return@withTransaction false
        preventivaDao.atualizar(preventiva.copy(ultimaData = System.currentTimeMillis(), ultimaPorId = real.id))
        true
    }

    /** Muda o stock de uma peça (−1, +1, +5…). Nunca fica negativo; ao chegar ao mínimo a reposição pedida fecha-se. */
    suspend fun ajustarStock(pecaId: String, variacao: Int, quem: Pessoa): Boolean = db.withTransaction {
        val real = pessoaReal(quem)
        check(real.papel == Pessoa.PAPEL_TECNICO) { "Só o técnico mexe no stock" }
        val peca = pecaDao.obter(pecaId) ?: return@withTransaction false
        if (peca.especialidade != real.especialidade) return@withTransaction false
        val novo = (peca.stock + variacao).coerceAtLeast(0)
        val reposta = novo >= peca.minimo
        pecaDao.atualizar(peca.copy(stock = novo, pedidaPorId = if (reposta) null else peca.pedidaPorId, dataPedido = if (reposta) null else peca.dataPedido))
        true
    }

    fun pecasDaAvaria(avariaId: Long) = db.pecaUsadaDao().obterDaAvaria(avariaId)

    /**
     * Regista uma peça substituída na intervenção em curso do próprio técnico e desconta-a do stock.
     * Devolve false se a peça é de outra especialidade ou não há stock suficiente.
     */
    suspend fun registarPecaUsada(avariaId: Long, pecaId: String, quantidade: Int, tecnico: Pessoa): Boolean = db.withTransaction {
        val real = pessoaReal(tecnico)
        check(real.papel == Pessoa.PAPEL_TECNICO) { "Só o técnico regista peças" }
        require(quantidade > 0) { "Quantidade inválida" }
        val avaria = avariaDao.obter(avariaId) ?: return@withTransaction false
        check(avaria.estado == Avaria.EM_CURSO && avaria.tecnicoId == real.id) { "Só o técnico da intervenção regista peças" }
        val peca = pecaDao.obter(pecaId) ?: return@withTransaction false
        if (peca.especialidade != real.especialidade || peca.stock < quantidade) return@withTransaction false
        ajustarStock(pecaId, -quantidade, real)
        db.pecaUsadaDao().inserir(PecaUsada(avariaId = avariaId, pecaId = pecaId, quantidade = quantidade, tecnicoId = real.id, data = System.currentTimeMillis()))
        true
    }

    /** "Pedir reposição": só de uma peça abaixo do mínimo e que ainda não foi pedida. */
    suspend fun pedirReposicao(pecaId: String, quem: Pessoa): Boolean = db.withTransaction {
        val real = pessoaReal(quem)
        check(real.papel == Pessoa.PAPEL_TECNICO) { "Só o técnico pede reposições" }
        val peca = pecaDao.obter(pecaId) ?: return@withTransaction false
        if (peca.especialidade != real.especialidade || peca.pedida || peca.stock >= peca.minimo) return@withTransaction false
        pecaDao.atualizar(peca.copy(pedidaPorId = real.id, dataPedido = System.currentTimeMillis()))
        true
    }

    /**
     * Anula um registo de produção (por engano): o chefe da secção ou quem o registou.
     * Devolve false se não for dele ou já não existir.
     */
    suspend fun anularProducao(id: Long, quem: Pessoa): Boolean = db.withTransaction {
        val producao = db.producaoDao().obter(id) ?: return@withTransaction false
        val secaoId = db.equipamentoDao().obterSecaoId(producao.equipamentoId) ?: return@withTransaction false
        val real = pessoaDao.obter(quem.id) ?: return@withTransaction false
        val autor = real.papel == Pessoa.PAPEL_OPERADOR && producao.pessoaId == real.id
        if (!autor && !eChefeDaSecao(quem, secaoId)) return@withTransaction false
        db.producaoDao().eliminar(id)
        true
    }

    private companion object {
        const val PONTOS_CHECKLIST = 6
    }

    private suspend fun pessoaReal(quem: Pessoa): Pessoa = checkNotNull(pessoaDao.obter(quem.id)) { "Pessoa desconhecida" }

    // Relê a pessoa da base de dados: não se confia no objeto que vem do ecrã.
    private suspend fun eChefeDaSecao(chefe: Pessoa, secaoId: String): Boolean {
        val real = pessoaDao.obter(chefe.id) ?: return false
        return real.papel == Pessoa.PAPEL_CHEFE && real.postoId == secaoId
    }

    private suspend fun pinCorreto(pessoaId: String, pin: String): Boolean {
        return pessoaDao.obter(pessoaId)?.pinConfere(pin) == true
    }

    private suspend fun eTecnicoDaIntervencao(aut: Autorizacao, tecnico: Pessoa): Boolean {
        val avaria = avariaDao.obter(aut.avariaId) ?: return false
        return avaria.estado == Avaria.EM_CURSO && avaria.tecnicoId == tecnico.id
    }

    // ---------- Auxiliares ----------

    // ---------- Decisões da engenharia (só a engenharia; o papel é relido da base de dados) ----------

    private suspend fun engenheiro(quem: Pessoa): Pessoa {
        val real = pessoaReal(quem)
        check(real.papel == Pessoa.PAPEL_ENGENHEIRO) { "Só a engenharia decide" }
        return real
    }

    /** Aprova o fecho de uma avaria resolvida. Devolve false se já não está por aprovar. */
    suspend fun aprovarFecho(avariaId: Long, quem: Pessoa): Boolean = db.withTransaction {
        val eng = engenheiro(quem)
        val avaria = avariaDao.obter(avariaId) ?: return@withTransaction false
        if (!avaria.porAprovar()) return@withTransaction false
        avariaDao.atualizar(avaria.copy(aprovadaPorId = eng.id, dataAprovacao = System.currentTimeMillis()))
        registarEvento(avariaId, R.string.evento_fecho_aprovado, eng)
        true
    }

    /** Devolve ao técnico uma avaria por aprovar (volta a “em curso”), com o motivo na cronologia. */
    suspend fun devolverAoTecnico(avariaId: Long, motivo: String, quem: Pessoa): Boolean = db.withTransaction {
        val eng = engenheiro(quem)
        require(motivo.isNotBlank()) { "Falta o motivo" }
        val avaria = avariaDao.obter(avariaId) ?: return@withTransaction false
        if (!avaria.porAprovar()) return@withTransaction false
        avariaDao.atualizar(avaria.copy(estado = Avaria.EM_CURSO, dataFim = null))
        // O responsável volta à equipa (a participação anterior fechou ao concluir)
        avaria.tecnicoId?.let { equipaDao.entrar(AvariaTecnico(avariaId, it, System.currentTimeMillis())) }
        registarEventoTexto(avariaId, context.getString(R.string.evento_devolvida, eng.nome, motivo.trim()), eng)
        true
    }

    /** Muda a oficina (Mecânica, Elétrica ou Não sei) de uma avaria ainda pendente; a atribuição a um técnico cai. */
    suspend fun reclassificarOficina(avariaId: Long, especialidade: String, quem: Pessoa): Boolean = db.withTransaction {
        val eng = engenheiro(quem)
        require(especialidade in listOf(Avaria.MECANICA, Avaria.ELETRICA, Avaria.NAO_SEI)) { "Oficina inválida" }
        val avaria = avariaDao.obter(avariaId) ?: return@withTransaction false
        if (avaria.estado != Avaria.PENDENTE) return@withTransaction false
        avariaDao.atualizar(avaria.copy(especialidade = especialidade, atribuidaAId = null))
        equipaDao.apagarAtuais(avariaId) // os ajudantes escolhidos para a oficina anterior deixam de fazer sentido
        registarEventoTexto(avariaId, context.getString(R.string.evento_oficina_reclassificada, eng.nome, context.getString(EstiloAvaria.especialidade(especialidade))), eng)
        true
    }

    /** Muda a prioridade de uma avaria ainda pendente. */
    suspend fun reclassificar(avariaId: Long, prioridade: String, quem: Pessoa): Boolean = db.withTransaction {
        val eng = engenheiro(quem)
        require(prioridade in listOf(Avaria.BAIXA, Avaria.MEDIA, Avaria.CRITICA)) { "Prioridade inválida" }
        val avaria = avariaDao.obter(avariaId) ?: return@withTransaction false
        if (avaria.estado != Avaria.PENDENTE) return@withTransaction false
        avariaDao.atualizar(avaria.copy(prioridade = prioridade))
        registarEventoTexto(avariaId, context.getString(R.string.evento_reclassificada, eng.nome, context.getString(EstiloAvaria.prioridade(prioridade))), eng)
        true
    }

    private suspend fun registarEventoTexto(avariaId: Long, texto: String, quem: Pessoa) {
        eventoDao.inserir(EventoAvaria(avariaId = avariaId, texto = texto, pessoaId = quem.id, data = System.currentTimeMillis()))
    }

    private suspend fun registarEvento(avariaId: Long, texto: Int, quem: Pessoa) {
        eventoDao.inserir(
            EventoAvaria(
                avariaId = avariaId,
                texto = context.getString(texto, quem.nome),
                pessoaId = quem.id,
                data = System.currentTimeMillis()
            )
        )
    }

    /** AV-<ano>-<id com 4 dígitos>. */
    private fun codigoPara(id: Long): String =
        String.format(Locale.ROOT, "AV-%d-%04d", LocalDate.now().year, id)
}
