package pt.ubi.di.pdm.finsafix

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.ubi.di.pdm.finsafix.data.AppDatabase
import pt.ubi.di.pdm.finsafix.data.Autorizacao
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Aviso
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.OrdemProducao
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Producao
import pt.ubi.di.pdm.finsafix.data.Repositorio
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoAutorizacao.CHECKLIST_INCOMPLETA
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoAutorizacao.ESTADO_INVALIDO
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoAutorizacao.EXPIRADA
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoAutorizacao.FALTA_CADEADO
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoAutorizacao.FALTA_VIGILANTE
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoAutorizacao.JA_EXISTE_ABERTA
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoAutorizacao.OK
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoAutorizacao.PIN_INCORRETO
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoAutorizacao.SEM_PERMISSAO
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoAutorizacao.VIGILANCIA_EM_CURSO
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.util.Seguranca

/**
 * Testes das autorizações de trabalho e do acesso do chefe, com uma base de dados em memória.
 * Duas secções (SEC e SEC2), cada uma com o seu chefe, para provar que um chefe não mexe na secção do outro.
 */
@RunWith(AndroidJUnit4::class)
class AutorizacoesTest {

    private lateinit var db: AppDatabase
    private lateinit var repositorio: Repositorio

    private val operador = pessoa("op", Pessoa.PAPEL_OPERADOR, "SEC")
    private val tecnico = pessoa("tec", Pessoa.PAPEL_TECNICO, "OFI", Avaria.MECANICA)
    private val outroTecnico = pessoa("tec2", Pessoa.PAPEL_TECNICO, "OFI", Avaria.MECANICA)
    private val chefe = pessoa("chefe1", Pessoa.PAPEL_CHEFE, "SEC", pin = "1234")
    private val chefeDeOutraSecao = pessoa("chefe2", Pessoa.PAPEL_CHEFE, "SEC2", pin = "4321")
    private val chefeDeOficina = pessoa("chefeOfi", Pessoa.PAPEL_CHEFE, "OFI", pin = "1111")

    @Before
    fun criarBaseDeDados() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repositorio = Repositorio(context, db)
        db.secaoDao().inserirTodas(
            listOf(
                Secao("SEC", "Secção 1", "t", Secao.TIPO_SECAO),
                Secao("SEC2", "Secção 2", "t", Secao.TIPO_SECAO),
                Secao("OFI", "Oficina", "", Secao.TIPO_OFICINA)
            )
        )
        db.equipamentoDao().inserirTodos(listOf(Equipamento("EQ", "Equipamento 1", "SEC"), Equipamento("EQ2", "Equipamento 2", "SEC2")))
        db.pessoaDao().inserirTodas(listOf(operador, tecnico, outroTecnico, chefe, chefeDeOutraSecao, chefeDeOficina))
    }

    @After
    fun fecharBaseDeDados() = db.close()

    // ---------- Estados: ninguém salta um ----------

    @Test
    fun ninguem_salta_um_estado() = runBlocking {
        val id = autorizacaoPedida()
        assertEquals(ESTADO_INVALIDO, repositorio.iniciarTrabalho(id, tecnico, null)) // ainda não foi aprovada
        assertEquals(ESTADO_INVALIDO, repositorio.terminarTrabalho(id, tecnico))
        assertEquals(ESTADO_INVALIDO, repositorio.encerrarAutorizacao(id, chefe, PIN, true))

        assertEquals(OK, repositorio.aprovarAutorizacao(id, chefe, PIN, checklistCompleta = true, vigilante = "Rui"))
        assertEquals(ESTADO_INVALIDO, repositorio.aprovarAutorizacao(id, chefe, PIN, true, vigilante = "Rui")) // não se aprova duas vezes
        assertEquals(ESTADO_INVALIDO, repositorio.recusarAutorizacao(id, chefe, "Falta informação"))
        assertEquals(ESTADO_INVALIDO, repositorio.terminarTrabalho(id, tecnico)) // ainda não começou
        assertEquals(ESTADO_INVALIDO, repositorio.encerrarAutorizacao(id, chefe, PIN, true))

        assertEquals(OK, repositorio.iniciarTrabalho(id, tecnico, null))
        assertEquals(ESTADO_INVALIDO, repositorio.iniciarTrabalho(id, tecnico, null))
        assertEquals(ESTADO_INVALIDO, repositorio.encerrarAutorizacao(id, chefe, PIN, true)) // o trabalho ainda não terminou
    }

    @Test
    fun trabalho_a_quente_so_se_aprova_com_o_nome_do_vigilante() = runBlocking {
        val id = autorizacaoPedida() // a quente por omissão
        assertEquals(FALTA_VIGILANTE, repositorio.aprovarAutorizacao(id, chefe, PIN, true))
        assertEquals(FALTA_VIGILANTE, repositorio.aprovarAutorizacao(id, chefe, PIN, true, vigilante = "  "))
        assertEquals(Autorizacao.PEDIDA, db.autorizacaoDao().obter(id)!!.estado)

        assertEquals(OK, repositorio.aprovarAutorizacao(id, chefe, PIN, true, vigilante = "Rui Costa"))
        assertEquals("Rui Costa", db.autorizacaoDao().obter(id)!!.vigilante)
    }

    @Test
    fun so_o_chefe_da_secao_anula_um_registo_de_producao() = runBlocking {
        db.producaoDao().inserir(Producao(equipamentoId = "EQ", turno = "M", quantidade = 10.0, pessoaId = operador.id, data = 1L))
        val id = db.producaoDao().obter(1L)!!.id
        assertFalse(repositorio.anularProducao(id, chefeDeOutraSecao))
        assertFalse(repositorio.anularProducao(id, tecnico))
        assertFalse(repositorio.anularProducao(id, pessoa("op9", Pessoa.PAPEL_OPERADOR, "SEC"))) // outro operador
        assertNotNull(db.producaoDao().obter(id))

        assertTrue(repositorio.anularProducao(id, chefe))
        assertNull(db.producaoDao().obter(id))
        assertFalse(repositorio.anularProducao(id, chefe)) // já não existe
    }

    @Test
    fun o_chefe_ajusta_o_plano_do_turno_sem_ficar_negativo() = runBlocking {
        db.ordemProducaoDao().inserirTodas(listOf(OrdemProducao(secaoId = "SEC", equipamentoId = "EQ", turno = "T", planeado = 20.0)))
        val id = db.ordemProducaoDao().obterDoTurno("SEC", "T").first().single().ordem.id
        assertFalse(repositorio.ajustarPlano(mapOf(id to 5.0), chefeDeOutraSecao)) // outra secção
        assertFalse(repositorio.ajustarPlano(mapOf(id to 5.0), operador))
        assertEquals(20.0, db.ordemProducaoDao().obter(id)!!.planeado, 0.0)

        assertTrue(repositorio.ajustarPlano(mapOf(id to 6.0), chefe)) // +5 +1
        assertEquals(26.0, db.ordemProducaoDao().obter(id)!!.planeado, 0.0)
        assertEquals(chefe.id, db.ordemProducaoDao().obter(id)!!.ajustadaPorId)
        assertTrue(repositorio.ajustarPlano(mapOf(id to -100.0), chefe))
        assertEquals(0.0, db.ordemProducaoDao().obter(id)!!.planeado, 0.0) // nunca negativo
        assertFalse(repositorio.ajustarPlano(mapOf(id to 0.0), chefe)) // sem ajuste
    }

    @Test
    fun quem_registou_a_producao_tambem_a_anula() = runBlocking {
        repositorio.registarProducao("EQ", "T", 12.0, operador, cortes = 3)
        val id = db.producaoDao().obter(1L)!!.id
        assertEquals(3, db.producaoDao().obter(id)!!.cortes)
        assertTrue(repositorio.anularProducao(id, operador))
        assertNull(db.producaoDao().obter(id))
    }

    @Test
    fun a_autorizacao_vale_ate_ao_fim_do_turno_e_o_chefe_renova() = runBlocking {
        val id = autorizacaoPedida()
        assertEquals(OK, repositorio.aprovarAutorizacao(id, chefe, PIN, true, vigilante = "Rui"))
        val aprovada = db.autorizacaoDao().obter(id)!!
        assertNotNull(aprovada.validaAte)
        assertFalse(aprovada.expirada(System.currentTimeMillis()))
        assertFalse(repositorio.renovarAutorizacao(id, chefe)) // ainda não expirou

        // Passa o fim do turno em que foi aprovada
        db.autorizacaoDao().atualizar(aprovada.copy(validaAte = System.currentTimeMillis() - 1000))
        assertEquals(EXPIRADA, repositorio.iniciarTrabalho(id, tecnico, null))
        assertFalse(repositorio.renovarAutorizacao(id, chefeDeOutraSecao)) // só o chefe da secção
        assertTrue(repositorio.renovarAutorizacao(id, chefe))
        assertFalse(db.autorizacaoDao().obter(id)!!.expirada(System.currentTimeMillis()))
        assertEquals(OK, repositorio.iniciarTrabalho(id, tecnico, null))
    }

    @Test
    fun uma_autorizacao_pode_ter_varios_tipos() = runBlocking {
        val avariaId = repositorio.criarAvaria(
            Avaria(
                codigo = "", equipamentoId = "EQ", titulo = "Cinta", descricao = "", prioridade = Avaria.MEDIA, especialidade = Avaria.MECANICA,
                estado = Avaria.PENDENTE, reportadaPorId = operador.id, dataCriacao = System.currentTimeMillis()
            ),
            operador
        )
        repositorio.assumir(avariaId, tecnico, null)
        assertFalha { repositorio.pedirAutorizacao(avariaId, "${Autorizacao.QUENTE},voo", "Soldar o suporte", tecnico) } // tipo desconhecido
        assertEquals(OK, repositorio.pedirAutorizacao(avariaId, "${Autorizacao.CONSIGNACAO},${Autorizacao.ALTURA}", "Trocar o motor em altura", tecnico))
        val aut = db.autorizacaoDao().obterAbertasDaAvaria(avariaId).single()
        assertEquals(listOf(Autorizacao.CONSIGNACAO, Autorizacao.ALTURA), aut.tipos())
        assertTrue(aut.exigeCadeado()) // basta um dos tipos exigir cadeado
        assertFalse(aut.temQuente())
    }

    @Test
    fun aprovar_exige_a_checklist_toda_e_o_pin_do_chefe() = runBlocking {
        val id = autorizacaoPedida()
        assertEquals(CHECKLIST_INCOMPLETA, repositorio.aprovarAutorizacao(id, chefe, PIN, checklistCompleta = false, vigilante = "Rui"))
        assertEquals(PIN_INCORRETO, repositorio.aprovarAutorizacao(id, chefe, "9999", checklistCompleta = true, vigilante = "Rui"))
        assertEquals(Autorizacao.PEDIDA, db.autorizacaoDao().obter(id)!!.estado) // nada mudou

        assertEquals(OK, repositorio.aprovarAutorizacao(id, chefe, PIN, checklistCompleta = true, vigilante = "Rui"))
        val aprovada = db.autorizacaoDao().obter(id)!!
        assertEquals(Autorizacao.APROVADA, aprovada.estado)
        assertEquals(chefe.id, aprovada.decididaPorId)
        // A cronologia da avaria guarda quem pediu e quem aprovou.
        val eventos = db.eventoAvariaDao().obterDaAvariaLista(aprovada.avariaId)
        assertTrue(eventos.any { it.pessoaId == chefe.id })
        assertTrue(eventos.any { it.pessoaId == tecnico.id && it.texto.contains("pedida") })
    }

    @Test
    fun recusar_pede_motivo_e_permite_um_novo_pedido() = runBlocking {
        val avariaId = avariaEmCurso()
        val id = autorizacaoPedida(avariaId)
        assertFalha { repositorio.recusarAutorizacao(id, chefe, " ") } // o motivo é obrigatório

        assertEquals(OK, repositorio.recusarAutorizacao(id, chefe, "Falta informação no pedido"))
        val recusada = db.autorizacaoDao().obter(id)!!
        assertEquals(Autorizacao.RECUSADA, recusada.estado)
        assertEquals("Falta informação no pedido", recusada.motivoRecusa)

        assertEquals(OK, repositorio.pedirAutorizacao(avariaId, Autorizacao.QUENTE, "Soldar o suporte", tecnico)) // já pode pedir outra
    }

    // ---------- Segurança: cada chefe só na sua secção ----------

    @Test
    fun o_chefe_de_outra_secao_nao_ve_nem_decide_nada() = runBlocking {
        val id = autorizacaoPedida() // pedida na SEC
        assertEquals(1, repositorio.autorizacoesAbertasDaSecao("SEC").first().size)
        assertEquals(0, repositorio.autorizacoesAbertasDaSecao("SEC2").first().size) // não a vê
        assertEquals(0, repositorio.contarAutorizacoesPorTratar("SEC2").first())

        // Nem com o PIN certo do seu próprio chefe.
        assertEquals(SEM_PERMISSAO, repositorio.aprovarAutorizacao(id, chefeDeOutraSecao, "4321", true, vigilante = "Rui"))
        assertEquals(SEM_PERMISSAO, repositorio.recusarAutorizacao(id, chefeDeOutraSecao, "Não gosto"))
        // Nem fingindo que é da SEC: o Repositório relê a pessoa da base de dados e não confia no ecrã.
        assertEquals(SEM_PERMISSAO, repositorio.aprovarAutorizacao(id, chefeDeOutraSecao.copy(postoId = "SEC"), "4321", true))
        // Nem um técnico, nem um operador, a fingir que são chefes.
        assertEquals(SEM_PERMISSAO, repositorio.aprovarAutorizacao(id, tecnico.copy(papel = Pessoa.PAPEL_CHEFE), PIN, true))
        assertEquals(SEM_PERMISSAO, repositorio.recusarAutorizacao(id, operador.copy(papel = Pessoa.PAPEL_CHEFE), "Não"))
        assertEquals(Autorizacao.PEDIDA, db.autorizacaoDao().obter(id)!!.estado)
    }

    @Test
    fun o_chefe_de_outra_secao_nao_encerra() = runBlocking {
        val id = autorizacaoEmCursoTerminada(Autorizacao.CONSIGNACAO)
        assertEquals(SEM_PERMISSAO, repositorio.encerrarAutorizacao(id, chefeDeOutraSecao, "4321", true))
        assertEquals(Autorizacao.EM_CURSO, db.autorizacaoDao().obter(id)!!.estado)
    }

    @Test
    fun so_o_tecnico_da_intervencao_pede_e_executa() = runBlocking {
        val avariaId = avariaEmCurso()
        assertEquals(SEM_PERMISSAO, repositorio.pedirAutorizacao(avariaId, Autorizacao.QUENTE, "Soldar o suporte", outroTecnico))
        val id = autorizacaoPedida(avariaId)
        repositorio.aprovarAutorizacao(id, chefe, PIN, true, vigilante = "Rui")
        assertEquals(SEM_PERMISSAO, repositorio.iniciarTrabalho(id, outroTecnico, null))
    }

    @Test
    fun a_entrada_do_chefe_so_vale_na_sua_secao() = runBlocking {
        assertEquals(chefe.id, repositorio.autenticarChefia("SEC", PIN)?.id)
        assertNull(repositorio.autenticarChefia("SEC2", PIN)) // PIN do chefe da SEC no tablet da SEC2
        assertNull(repositorio.autenticarChefia("SEC", "0000")) // PIN errado
        assertEquals(chefeDeOficina.id, repositorio.autenticarChefia("OFI", "1111")?.id) // o chefe do posto OFI, no tablet da OFI
    }

    @Test
    fun so_quem_e_da_secao_responde_aos_avisos() = runBlocking {
        // Erro corrigido: qualquer pessoa podia responder ao aviso de outra secção.
        repositorio.avisarChefe("SEC", "Pressão: 8,0 bar", operador)
        val aviso = repositorio.avisosPorResponder("SEC").first().single().aviso
        assertEquals(0, repositorio.avisosPorResponder("SEC2").first().size) // o chefe da SEC2 não o vê
        assertEquals(false, repositorio.responderAviso(aviso.id, chefeDeOutraSecao))
        assertEquals(false, repositorio.responderAviso(aviso.id, chefeDeOutraSecao.copy(postoId = "SEC"))) // relido da BD
        assertEquals(true, repositorio.responderAviso(aviso.id, chefe))
        assertEquals(0, repositorio.avisosPorResponder("SEC").first().size)
    }

    // ---------- Consignação, vigilância de fogo e bloqueios ----------

    @Test
    fun a_consignacao_pede_o_cadeado_e_ao_encerrar_avisa_a_secao() = runBlocking {
        val id = autorizacaoPedida(tipo = Autorizacao.CONSIGNACAO)
        repositorio.aprovarAutorizacao(id, chefe, PIN, true, vigilante = "Rui")
        assertEquals(FALTA_CADEADO, repositorio.iniciarTrabalho(id, tecnico, " "))
        assertEquals(OK, repositorio.iniciarTrabalho(id, tecnico, "E-09"))
        assertEquals(OK, repositorio.terminarTrabalho(id, tecnico))

        assertEquals(CHECKLIST_INCOMPLETA, repositorio.encerrarAutorizacao(id, chefe, PIN, false)) // fecho por confirmar
        assertEquals(PIN_INCORRETO, repositorio.encerrarAutorizacao(id, chefe, "0000", true))
        assertEquals(OK, repositorio.encerrarAutorizacao(id, chefe, PIN, true))

        assertEquals(Autorizacao.ENCERRADA, db.autorizacaoDao().obter(id)!!.estado)
        val aviso = repositorio.avisoUrgente("SEC").first()!!.aviso
        assertEquals(Aviso.ARRANCAR, aviso.tipo) // "Máquina pode voltar a arrancar"
        assertEquals("SEC", aviso.secaoId)
    }

    @Test
    fun o_trabalho_a_quente_so_encerra_depois_da_vigilancia_de_60_minutos() = runBlocking {
        val id = autorizacaoEmCursoTerminada(Autorizacao.QUENTE)
        assertEquals(VIGILANCIA_EM_CURSO, repositorio.encerrarAutorizacao(id, chefe, PIN, true))

        val terminada = db.autorizacaoDao().obter(id)!!
        db.autorizacaoDao().atualizar(terminada.copy(dataTrabalhoTerminado = System.currentTimeMillis() - 61 * 60_000L))
        assertEquals(OK, repositorio.encerrarAutorizacao(id, chefe, PIN, true))
        assertNull(repositorio.avisoUrgente("SEC").first()) // o trabalho a quente não tem aviso de arranque
    }

    @Test
    fun uma_autorizacao_aberta_impede_concluir_exceto_na_vigilancia_de_fogo() = runBlocking {
        val avariaId = avariaEmCurso()
        val id = autorizacaoPedida(avariaId)
        assertEquals(JA_EXISTE_ABERTA, repositorio.pedirAutorizacao(avariaId, Autorizacao.ALTURA, "Trocar as facas", tecnico))
        assertEquals(false, repositorio.concluir(avariaId, tecnico, false)) // pedida
        repositorio.aprovarAutorizacao(id, chefe, PIN, true, vigilante = "Rui")
        assertEquals(false, repositorio.concluir(avariaId, tecnico, false)) // aprovada
        repositorio.iniciarTrabalho(id, tecnico, null)
        assertEquals(false, repositorio.concluir(avariaId, tecnico, false)) // em curso
        repositorio.terminarTrabalho(id, tecnico)
        assertEquals(true, repositorio.concluir(avariaId, tecnico, false)) // vigilância de fogo: já pode
        assertEquals(Avaria.RESOLVIDA, db.avariaDao().obter(avariaId)!!.estado)
    }

    @Test
    fun a_consignacao_terminada_ainda_impede_concluir_ate_o_chefe_encerrar() = runBlocking {
        val avariaId = avariaEmCurso()
        val id = autorizacaoEmCursoTerminada(Autorizacao.CONSIGNACAO, avariaId)
        assertEquals(false, repositorio.concluir(avariaId, tecnico, false)) // falta o regresso da energia
        repositorio.encerrarAutorizacao(id, chefe, PIN, true)
        assertEquals(true, repositorio.concluir(avariaId, tecnico, false))
    }

    @Test
    fun com_uma_autorizacao_aberta_o_tecnico_nao_deixa_em_progresso() = runBlocking {
        // Erro corrigido: o técnico saía e outro técnico podia executar a autorização aprovada para o primeiro.
        val avariaId = avariaEmCurso()
        val id = autorizacaoPedida(avariaId)
        assertEquals(false, deixar(avariaId)) // pedida
        repositorio.aprovarAutorizacao(id, chefe, PIN, true, vigilante = "Rui")
        assertEquals(false, deixar(avariaId)) // aprovada
        assertEquals(Avaria.EM_CURSO, db.avariaDao().obter(avariaId)!!.estado)
        assertEquals(Repositorio.ResultadoAssumir.JA_NAO_ESTA_PENDENTE, repositorio.assumir(avariaId, outroTecnico, null))

        repositorio.iniciarTrabalho(id, tecnico, null)
        repositorio.terminarTrabalho(id, tecnico)
        assertEquals(true, deixar(avariaId)) // vigilância de fogo: o chefe encerra sem o técnico
    }

    private suspend fun deixar(avariaId: Long) =
        repositorio.deixarEmProgresso(avariaId, tecnico, "Chefe de turno", Avaria.RETOMA_TURNO, "Falta de peça", "Falta a peça")

    // ---------- Auxiliares ----------

    private suspend fun avariaEmCurso(): Long {
        val id = repositorio.criarAvaria(
            Avaria(
                codigo = "", equipamentoId = "EQ", titulo = "Encravamento", descricao = "", prioridade = Avaria.MEDIA,
                especialidade = Avaria.MECANICA, estado = Avaria.PENDENTE, reportadaPorId = operador.id,
                dataCriacao = System.currentTimeMillis()
            ),
            operador
        )
        repositorio.assumir(id, tecnico, null)
        return id
    }

    /** Pede uma autorização (por omissão a quente, numa avaria nova) e devolve o seu id. */
    private suspend fun autorizacaoPedida(avariaId: Long? = null, tipo: String = Autorizacao.QUENTE): Long {
        val avaria = avariaId ?: avariaEmCurso()
        assertEquals(OK, repositorio.pedirAutorizacao(avaria, tipo, "Soldar o suporte", tecnico))
        return db.autorizacaoDao().obterAbertasDaAvaria(avaria).single().id
    }

    /** Leva uma autorização até "em curso, trabalho terminado, à espera do chefe". */
    private suspend fun autorizacaoEmCursoTerminada(tipo: String, avariaId: Long? = null): Long {
        val id = autorizacaoPedida(avariaId, tipo)
        repositorio.aprovarAutorizacao(id, chefe, PIN, true, vigilante = "Rui")
        repositorio.iniciarTrabalho(id, tecnico, if (tipo == Autorizacao.CONSIGNACAO) "E-09" else null)
        repositorio.terminarTrabalho(id, tecnico)
        assertNotNull(db.autorizacaoDao().obter(id)!!.dataTrabalhoTerminado)
        return id
    }

    private fun pessoa(id: String, papel: String, postoId: String, especialidade: String? = null, pin: String? = null) =
        Pessoa(id, id, "função", papel, postoId, "M", especialidade, pin?.let { Seguranca.hashPin(id, it) })

    /** Confirma que [bloco] lança uma exceção (inline para poder chamar funções suspend lá dentro). */
    private inline fun assertFalha(bloco: () -> Unit) {
        try {
            bloco()
        } catch (e: Exception) {
            return
        }
        throw AssertionError("Esperava uma exceção")
    }

    private companion object {
        const val PIN = "1234" // o PIN do chefe da SEC
    }
}
