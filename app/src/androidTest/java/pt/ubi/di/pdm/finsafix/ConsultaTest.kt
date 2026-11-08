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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.ubi.di.pdm.finsafix.data.AppDatabase
import pt.ubi.di.pdm.finsafix.data.Autorizacao
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Repositorio
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoAutorizacao.SEM_PERMISSAO
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.util.Seguranca

/**
 * A engenharia só consulta e decide e o chefe de oficina só vê a sua especialidade e as "Não sei".
 * Fábrica de teste: SEC (equipamento EQ, avaria mecânica) e SEC2 (EQ2, avaria elétrica), mais uma avaria "Não sei".
 */
@RunWith(AndroidJUnit4::class)
class ConsultaTest {

    private lateinit var db: AppDatabase
    private lateinit var repositorio: Repositorio

    private val operador = pessoa("op", Pessoa.PAPEL_OPERADOR, "SEC")
    private val tecnico = pessoa("tec", Pessoa.PAPEL_TECNICO, "OFI", Avaria.MECANICA)
    private val chefe = pessoa("chefe1", Pessoa.PAPEL_CHEFE, "SEC", pin = "1234")
    private val engenheiro = pessoa("eng", Pessoa.PAPEL_ENGENHEIRO, null, pin = "3001")
    private val chefeMecanica = pessoa("ofmec", Pessoa.PAPEL_CHEFE, "OFI", Avaria.MECANICA, pin = "2401")
    private val chefeEletrica = pessoa("ofele", Pessoa.PAPEL_CHEFE, "OFI2", Avaria.ELETRICA, pin = "2501")

    private var avariaMecanica = 0L
    private var avariaEletrica = 0L
    private var avariaNaoSei = 0L

    @Before
    fun criarBaseDeDados() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repositorio = Repositorio(context, db)
        db.secaoDao().inserirTodas(
            listOf(
                Secao("SEC", "Secção 1", "t", Secao.TIPO_SECAO), Secao("SEC2", "Secção 2", "t", Secao.TIPO_SECAO),
                Secao("OFI", "Oficina Mecânica", "", Secao.TIPO_OFICINA), Secao("OFI2", "Oficina Elétrica", "", Secao.TIPO_OFICINA)
            )
        )
        db.equipamentoDao().inserirTodos(listOf(Equipamento("EQ", "Equipamento 1", "SEC"), Equipamento("EQ2", "Equipamento 2", "SEC2")))
        db.pessoaDao().inserirTodas(listOf(operador, tecnico, chefe, engenheiro, chefeMecanica, chefeEletrica))
        avariaMecanica = repositorio.criarAvaria(avaria("EQ", Avaria.MECANICA), operador)
        avariaEletrica = repositorio.criarAvaria(avaria("EQ2", Avaria.ELETRICA), operador)
        avariaNaoSei = repositorio.criarAvaria(avaria("EQ", Avaria.NAO_SEI), operador)
    }

    @After
    fun fecharBaseDeDados() = db.close()

    // ---------- Entrada ----------

    @Test
    fun a_engenharia_entra_em_qualquer_tablet_e_o_chefe_de_oficina_so_no_seu() = runBlocking {
        assertEquals(engenheiro.id, repositorio.autenticarChefia("SEC", "3001")?.id)
        assertEquals(engenheiro.id, repositorio.autenticarChefia("SEC2", "3001")?.id)
        assertEquals(engenheiro.id, repositorio.autenticarChefia("OFI", "3001")?.id)
        assertNull(repositorio.autenticarChefia("SEC", "0000")) // PIN errado

        assertEquals(chefeMecanica.id, repositorio.autenticarChefia("OFI", "2401")?.id)
        assertNull(repositorio.autenticarChefia("SEC", "2401")) // o PIN do chefe da oficina noutro tablet
        assertNull(repositorio.autenticarChefia("OFI2", "2401"))
    }

    // ---------- Engenharia: vê tudo, só consulta ----------

    @Test
    fun a_engenharia_ve_a_fabrica_toda_e_filtra_por_seccao_e_estado() = runBlocking {
        assertEquals(3, repositorio.avariasDeConsulta(engenheiro, null, Avaria.ESTADOS).first().size)
        assertEquals(2, repositorio.avariasDeConsulta(engenheiro, "SEC", Avaria.ESTADOS).first().size)
        assertEquals(listOf(avariaEletrica), repositorio.avariasDeConsulta(engenheiro, "SEC2", Avaria.ESTADOS).first().map { it.avaria.id })

        repositorio.assumir(avariaMecanica, tecnico, null)
        assertEquals(listOf(avariaMecanica), repositorio.avariasDeConsulta(engenheiro, null, listOf(Avaria.EM_CURSO)).first().map { it.avaria.id })
        assertEquals(0, repositorio.avariasDeConsulta(engenheiro, null, listOf(Avaria.RESOLVIDA)).first().size)
    }

    @Test
    fun so_a_engenharia_ve_a_fabrica_e_os_indicadores() = runBlocking {
        for (quem in listOf(operador, tecnico, chefe)) {
            assertEquals(0, repositorio.avariasDeConsulta(quem, null, Avaria.ESTADOS).first().size)
            assertEquals(0, repositorio.indicadores(quem).first().size)
        }
        // Nem fingindo ser engenharia: o papel é relido da base de dados.
        assertEquals(0, repositorio.avariasDeConsulta(operador.copy(papel = Pessoa.PAPEL_ENGENHEIRO), null, Avaria.ESTADOS).first().size)
        assertEquals(0, repositorio.indicadores(chefeMecanica).first().size)
        assertEquals(2, repositorio.indicadores(engenheiro).first().size) // EQ e EQ2
    }

    @Test
    fun a_engenharia_decide_so_o_fecho_a_devolucao_e_a_prioridade() = runBlocking {
        // Reclassificar: só uma avaria pendente, e só a engenharia
        assertTrue(repositorio.reclassificar(avariaMecanica, Avaria.CRITICA, engenheiro))
        assertEquals(Avaria.CRITICA, db.avariaDao().obter(avariaMecanica)!!.prioridade)
        assertFalha { repositorio.reclassificar(avariaMecanica, Avaria.BAIXA, tecnico) }
        assertFalha { repositorio.reclassificar(avariaMecanica, Avaria.BAIXA, tecnico.copy(papel = Pessoa.PAPEL_ENGENHEIRO)) } // o papel é relido da BD
        assertFalha { repositorio.reclassificar(avariaMecanica, "urgente", engenheiro) }

        // Resolvida e ainda por aprovar
        repositorio.assumir(avariaMecanica, tecnico, null)
        assertFalse(repositorio.reclassificar(avariaMecanica, Avaria.BAIXA, engenheiro)) // já não está pendente
        repositorio.concluir(avariaMecanica, tecnico, false)
        assertTrue(db.avariaDao().obter(avariaMecanica)!!.porAprovar())
        assertFalha { repositorio.aprovarFecho(avariaMecanica, chefe) }
        assertFalha { repositorio.devolverAoTecnico(avariaMecanica, "Falta testar", tecnico) }
        assertFalha { repositorio.devolverAoTecnico(avariaMecanica, " ", engenheiro) } // o motivo é obrigatório

        // Devolver: volta a em curso, com o motivo na cronologia
        assertTrue(repositorio.devolverAoTecnico(avariaMecanica, "Falta testar", engenheiro))
        assertEquals(Avaria.EM_CURSO, db.avariaDao().obter(avariaMecanica)!!.estado)
        assertTrue(repositorio.cronologia(avariaMecanica).first().any { it.texto.contains("Falta testar") })
        assertFalse(repositorio.aprovarFecho(avariaMecanica, engenheiro)) // já não está por aprovar

        // Aprovar o fecho depois de concluir outra vez
        repositorio.concluir(avariaMecanica, tecnico, false)
        assertTrue(repositorio.aprovarFecho(avariaMecanica, engenheiro))
        assertFalse(db.avariaDao().obter(avariaMecanica)!!.porAprovar())
        assertFalse(repositorio.aprovarFecho(avariaMecanica, engenheiro)) // não se aprova duas vezes
    }

    @Test
    fun a_avaria_atribuida_so_aparece_e_so_e_assumida_pelo_tecnico_escolhido() = runBlocking {
        val outroTecnico = pessoa("tec2", Pessoa.PAPEL_TECNICO, "OFI", Avaria.MECANICA)
        val eletricista = pessoa("ele", Pessoa.PAPEL_TECNICO, "OFI2", Avaria.ELETRICA)
        db.pessoaDao().inserirTodas(listOf(outroTecnico, eletricista))

        assertFalse(repositorio.atribuirAvaria(avariaMecanica, tecnico.id, chefe)) // chefe de secção
        assertFalse(repositorio.atribuirAvaria(avariaMecanica, tecnico.id, chefeEletrica)) // outra especialidade
        assertFalse(repositorio.atribuirAvaria(avariaMecanica, eletricista.id, chefeMecanica)) // técnico de outra especialidade
        assertTrue(repositorio.atribuirAvaria(avariaMecanica, tecnico.id, chefeMecanica))

        val pendentes = listOf(Avaria.PENDENTE)
        val mecanica = listOf(Avaria.MECANICA)
        assertEquals(1, repositorio.avariasDaOficina(mecanica, pendentes, tecnico.id).first().size)
        assertEquals(0, repositorio.avariasDaOficina(mecanica, pendentes, outroTecnico.id).first().size) // não a vê
        assertEquals(Repositorio.ResultadoAssumir.ATRIBUIDA_A_OUTRO, repositorio.assumir(avariaMecanica, outroTecnico, null))
        assertEquals(Repositorio.ResultadoAssumir.OK, repositorio.assumir(avariaMecanica, tecnico, null))
    }

    @Test
    fun a_engenharia_atribui_a_um_tecnico_e_reclassifica_a_oficina() = runBlocking {
        val eletricista = pessoa("ele", Pessoa.PAPEL_TECNICO, "OFI2", Avaria.ELETRICA)
        db.pessoaDao().inserirTodas(listOf(eletricista))

        assertFalha { repositorio.reclassificarOficina(avariaMecanica, Avaria.ELETRICA, tecnico) } // só a engenharia
        assertFalha { repositorio.reclassificarOficina(avariaMecanica, "pintura", engenheiro) }
        assertFalse(repositorio.atribuirAvaria(avariaNaoSei, tecnico.id, engenheiro)) // “Não sei”: primeiro classifica-se
        assertFalse(repositorio.atribuirAvaria(avariaMecanica, eletricista.id, engenheiro)) // técnico de outra oficina
        assertFalse(repositorio.atribuirAvaria(avariaMecanica, tecnico.id, tecnico.copy(papel = Pessoa.PAPEL_ENGENHEIRO))) // papel relido da BD

        assertTrue(repositorio.atribuirAvaria(avariaMecanica, tecnico.id, engenheiro))
        assertEquals(tecnico.id, db.avariaDao().obter(avariaMecanica)!!.atribuidaAId)

        // Muda de oficina: a atribuição cai e a avaria passa a ser da outra oficina
        assertTrue(repositorio.reclassificarOficina(avariaMecanica, Avaria.ELETRICA, engenheiro))
        val mudada = db.avariaDao().obter(avariaMecanica)!!
        assertEquals(Avaria.ELETRICA, mudada.especialidade)
        assertNull(mudada.atribuidaAId)
        assertTrue(repositorio.atribuirAvaria(avariaMecanica, eletricista.id, engenheiro))
    }

    @Test
    fun a_engenharia_nao_altera_nada() = runBlocking {
        // Tudo o que escreve é recusado (exceção) ...
        val engenhariaFalsa = engenheiro.copy(papel = Pessoa.PAPEL_TECNICO) // mesmo mudando o papel no objeto: relido da BD
        for (quem in listOf(engenheiro, engenhariaFalsa)) {
            assertFalha { repositorio.criarAvaria(avaria("EQ", Avaria.MECANICA), quem) }
            assertFalha { repositorio.editarAvaria(db.avariaDao().obter(avariaMecanica)!!, quem) }
            assertFalha { repositorio.eliminarAvaria(db.avariaDao().obter(avariaMecanica)!!, quem) }
            assertFalha { repositorio.assumir(avariaMecanica, quem, null) }
            assertFalha { repositorio.registarParagem("EQ", "Limpeza", quem) }
            assertFalha { repositorio.registarLeitura("P", 1.0, quem) }
            assertFalha { repositorio.registarProducao("EQ", "M", 1.0, quem) }
            assertFalha { repositorio.avisarChefe("SEC", "Pressão", quem) }
        }
        // ... e nada mudou na base de dados.
        assertEquals(Avaria.PENDENTE, db.avariaDao().obter(avariaMecanica)!!.estado)
        assertEquals(3, db.avariaDao().obterDaFabrica(null, Avaria.ESTADOS).first().size)
        assertNull(db.paragemDao().obterAtivaDoEquipamento("EQ"))

        // Autorizações: nem aprovar, nem recusar, nem encerrar, nem pedir, nem executar.
        repositorio.assumir(avariaMecanica, tecnico, null)
        repositorio.pedirAutorizacao(avariaMecanica, Autorizacao.QUENTE, "Soldar o suporte", tecnico)
        val id = db.autorizacaoDao().obterAbertasDaAvaria(avariaMecanica).single().id
        val engenhariaComoChefe = engenheiro.copy(papel = Pessoa.PAPEL_CHEFE, postoId = "SEC")
        assertEquals(SEM_PERMISSAO, repositorio.aprovarAutorizacao(id, engenheiro, "3001", true))
        assertEquals(SEM_PERMISSAO, repositorio.aprovarAutorizacao(id, engenhariaComoChefe, "3001", true))
        assertEquals(SEM_PERMISSAO, repositorio.recusarAutorizacao(id, engenheiro, "Não"))
        assertEquals(SEM_PERMISSAO, repositorio.encerrarAutorizacao(id, engenheiro, "3001", true))
        assertEquals(SEM_PERMISSAO, repositorio.pedirAutorizacao(avariaEletrica, Autorizacao.ALTURA, "Trocar as facas", engenheiro))
        assertEquals(SEM_PERMISSAO, repositorio.iniciarTrabalho(id, engenheiro, null))
        assertEquals(Autorizacao.PEDIDA, db.autorizacaoDao().obter(id)!!.estado)

        // Avisos: não responde (não é da secção).
        repositorio.avisarChefe("SEC", "Pressão", operador)
        val aviso = repositorio.avisosPorResponder("SEC").first().single().aviso
        assertFalse(repositorio.responderAviso(aviso.id, engenheiro))
        assertEquals(1, repositorio.avisosPorResponder("SEC").first().size)
    }

    // ---------- Chefe de oficina: só a sua especialidade ----------

    @Test
    fun o_chefe_de_oficina_so_ve_a_sua_especialidade_e_as_nao_sei() = runBlocking {
        fun ids(quem: Pessoa, secao: String? = null) =
            runBlocking { repositorio.avariasDeConsulta(quem, secao, Avaria.ESTADOS).first().map { it.avaria.id }.toSet() }
        assertEquals(setOf(avariaMecanica, avariaNaoSei), ids(chefeMecanica))
        assertEquals(setOf(avariaEletrica, avariaNaoSei), ids(chefeEletrica))

        // Não consegue alargar o que vê: nem escolhendo a secção da outra especialidade ...
        assertEquals(setOf(avariaMecanica, avariaNaoSei), ids(chefeMecanica, "SEC2"))
        // ... nem mudando a especialidade no objeto (é relida da base de dados).
        val falso = chefeMecanica.copy(especialidade = Avaria.ELETRICA)
        assertEquals(setOf(avariaMecanica, avariaNaoSei), ids(falso))
        // Um chefe de secção não tem especialidade: não vê nada nesta consulta.
        assertEquals(0, repositorio.avariasDeConsulta(chefe, null, Avaria.ESTADOS).first().size)
    }

    @Test
    fun o_chefe_de_oficina_so_consulta() = runBlocking {
        repositorio.assumir(avariaMecanica, tecnico, null)
        repositorio.pedirAutorizacao(avariaMecanica, Autorizacao.QUENTE, "Soldar o suporte", tecnico)
        val id = db.autorizacaoDao().obterAbertasDaAvaria(avariaMecanica).single().id
        // A autorização é da secção SEC: o chefe da oficina (posto OFI) não a decide, mesmo com o PIN certo.
        assertEquals(SEM_PERMISSAO, repositorio.aprovarAutorizacao(id, chefeMecanica, "2401", true))
        assertEquals(SEM_PERMISSAO, repositorio.recusarAutorizacao(id, chefeMecanica, "Não"))

        repositorio.avisarChefe("SEC", "Pressão", operador)
        val aviso = repositorio.avisosPorResponder("SEC").first().single().aviso
        assertFalse(repositorio.responderAviso(aviso.id, chefeMecanica))
        assertTrue(repositorio.responderAviso(aviso.id, chefe)) // o chefe da secção responde
    }

    // ---------- Auxiliares ----------

    private fun avaria(equipamentoId: String, especialidade: String) = Avaria(
        codigo = "", equipamentoId = equipamentoId, titulo = "Encravamento", descricao = "", prioridade = Avaria.MEDIA,
        especialidade = especialidade, estado = Avaria.PENDENTE, reportadaPorId = operador.id, dataCriacao = System.currentTimeMillis()
    )

    private fun pessoa(id: String, papel: String, postoId: String?, especialidade: String? = null, pin: String? = null) =
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
}
