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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.ubi.di.pdm.finsafix.data.AppDatabase
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.Indicadores
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Repositorio
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoJuntar
import pt.ubi.di.pdm.finsafix.data.Secao

/** Vários técnicos na mesma avaria: cada regra de entrar, sair, concluir e atribuir uma equipa (base de dados em memória). */
@RunWith(AndroidJUnit4::class)
class EquipaTest {

    private lateinit var db: AppDatabase
    private lateinit var repositorio: Repositorio

    private val operador = pessoa("op", Pessoa.PAPEL_OPERADOR, null)
    private val carlos = pessoa("carlos", Pessoa.PAPEL_TECNICO, Avaria.MECANICA)
    private val joaquim = pessoa("joaquim", Pessoa.PAPEL_TECNICO, Avaria.MECANICA)
    private val mario = pessoa("mario", Pessoa.PAPEL_TECNICO, Avaria.MECANICA)
    private val joao = pessoa("joao", Pessoa.PAPEL_TECNICO, Avaria.ELETRICA)
    private val rui = pessoa("rui", Pessoa.PAPEL_TECNICO, Avaria.MECANICA)
    private val chefe = pessoa("chefe", Pessoa.PAPEL_CHEFE, Avaria.MECANICA)

    @Before
    fun criarBaseDeDados() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repositorio = Repositorio(context, db)
        db.secaoDao().inserirTodas(listOf(Secao("SEC", "Secção", "t", Secao.TIPO_SECAO)))
        db.equipamentoDao().inserirTodos(listOf(Equipamento("EQ", "Equipamento", "SEC")))
        db.pessoaDao().inserirTodas(listOf(operador, carlos, joaquim, mario, joao, rui, chefe))
    }

    @After
    fun fecharBaseDeDados() = db.close()

    @Test
    fun juntar_a_uma_avaria_pendente_e_recusado() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(), operador)
        assertEquals(ResultadoJuntar.NAO_ESTA_EM_CURSO, repositorio.juntarAIntervencao(id, joaquim, lotoConfirmado = true))
        assertTrue(db.avariaTecnicoDao().obterDaAvaria(id).isEmpty())
    }

    @Test
    fun juntar_sem_os_3_pontos_loto_e_recusado() = runBlocking {
        val id = emCurso()
        assertEquals(ResultadoJuntar.SEM_LOTO, repositorio.juntarAIntervencao(id, joaquim, lotoConfirmado = false))
        assertEquals(listOf(carlos.id), equipa(id)) // só o responsável
    }

    @Test
    fun quem_ja_tem_outra_intervencao_nao_se_junta() = runBlocking {
        val id = emCurso()
        val outra = repositorio.criarAvaria(novaAvaria(), operador)
        repositorio.assumir(outra, joaquim, null)
        assertEquals(ResultadoJuntar.JA_TEM_INTERVENCAO, repositorio.juntarAIntervencao(id, joaquim, lotoConfirmado = true))
        // e quem é ajudante de uma avaria também não pode assumir outra
        val terceira = repositorio.criarAvaria(novaAvaria(), operador)
        assertEquals(ResultadoJuntar.OK, repositorio.juntarAIntervencao(id, mario, lotoConfirmado = true))
        assertEquals(Repositorio.ResultadoAssumir.JA_TEM_INTERVENCAO, repositorio.assumir(terceira, mario, null))
    }

    @Test
    fun a_oficina_conta_exceto_nas_avarias_nao_sei() = runBlocking {
        val mecanica = emCurso()
        assertEquals(ResultadoJuntar.OUTRA_OFICINA, repositorio.juntarAIntervencao(mecanica, joao, lotoConfirmado = true))

        val naoSei = repositorio.criarAvaria(novaAvaria(especialidade = Avaria.NAO_SEI), operador)
        repositorio.assumir(naoSei, mario, null) // fica por classificar
        assertEquals(ResultadoJuntar.OK, repositorio.juntarAIntervencao(naoSei, joao, lotoConfirmado = true))
    }

    @Test
    fun juntar_poe_o_tecnico_na_equipa_e_regista_o_evento() = runBlocking {
        val id = emCurso()
        assertEquals(ResultadoJuntar.OK, repositorio.juntarAIntervencao(id, joaquim, lotoConfirmado = true))
        assertEquals(listOf(carlos.id, joaquim.id), equipa(id))
        val eventos = db.eventoAvariaDao().obterDaAvariaLista(id).map { it.texto }
        assertTrue(eventos.any { it.contains("joaquim") && it.contains("juntou-se") })
        // a avaria continua a ter o carlos como responsável
        assertEquals(carlos.id, db.avariaDao().obter(id)!!.tecnicoId)
    }

    @Test
    fun o_ajudante_sai_e_o_responsavel_nao_sai_por_aqui() = runBlocking {
        val id = emCurso()
        repositorio.juntarAIntervencao(id, joaquim, lotoConfirmado = true)

        assertFalse(repositorio.sairDaIntervencao(id, carlos)) // responsável: Concluir ou Deixar em progresso
        assertTrue(repositorio.sairDaIntervencao(id, joaquim))
        assertEquals(listOf(carlos.id), equipa(id))
        assertFalse(repositorio.sairDaIntervencao(id, joaquim)) // já saiu
        assertTrue(db.eventoAvariaDao().obterDaAvariaLista(id).any { it.texto.contains("joaquim") && it.texto.contains("saiu") })
        // depois de sair fica livre: pode assumir outra avaria
        val outra = repositorio.criarAvaria(novaAvaria(), operador)
        assertEquals(Repositorio.ResultadoAssumir.OK, repositorio.assumir(outra, joaquim, null))
    }

    @Test
    fun concluir_fecha_a_participacao_de_todos() = runBlocking {
        val id = emCurso()
        repositorio.juntarAIntervencao(id, joaquim, lotoConfirmado = true)
        repositorio.concluir(id, carlos, retomarProducao = false)

        assertTrue(equipa(id).isEmpty())
        val todas = db.avariaTecnicoDao().obterDaAvaria(id)
        assertEquals(2, todas.size)
        assertTrue(todas.all { it.dataSaida != null })
    }

    @Test
    fun deixar_em_progresso_fecha_a_participacao_de_todos() = runBlocking {
        val id = emCurso()
        repositorio.juntarAIntervencao(id, joaquim, lotoConfirmado = true)
        assertTrue(repositorio.deixarEmProgresso(id, carlos, "Peça", Avaria.RETOMA_TURNO, "Falta uma peça", "Nota"))

        assertTrue(equipa(id).isEmpty())
        assertTrue(db.avariaTecnicoDao().obterDaAvaria(id).all { it.dataSaida != null })
        assertEquals(null, db.avariaTecnicoDao().avariaAtualDoTecnico(joaquim.id)) // os dois ficam livres
    }

    @Test
    fun eliminar_a_avaria_apaga_a_equipa() = runBlocking {
        val id = emCurso()
        repositorio.juntarAIntervencao(id, joaquim, lotoConfirmado = true)
        assertEquals(2, db.avariaTecnicoDao().obterDaAvaria(id).size)

        db.avariaDao().eliminar(db.avariaDao().obter(id)!!) // CASCADE: as linhas da equipa vão com a avaria
        assertTrue(db.avariaTecnicoDao().obterDaAvaria(id).isEmpty())
    }

    @Test
    fun o_chefe_atribui_responsavel_e_ajudantes_que_entram_ao_assumir() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(), operador)
        assertTrue(repositorio.atribuirEquipa(id, carlos.id, listOf(joaquim.id, mario.id), chefe))

        assertEquals(carlos.id, db.avariaDao().obter(id)!!.atribuidaAId)
        val resumo = db.avariaDao().obterResumo(id).first()!!
        assertEquals(2, resumo.ajudantes)
        // os ajudantes veem a avaria pendente; um técnico que não está na equipa não
        assertEquals(listOf(id), listaDoTecnico(joaquim.id))
        assertTrue(listaDoTecnico(rui.id).isEmpty()) // da mesma oficina mas fora da equipa: não a vê
        assertEquals(Repositorio.ResultadoAssumir.OK, repositorio.assumir(id, carlos, null))
        assertEquals(listOf(carlos.id, joaquim.id, mario.id).sorted(), equipa(id).sorted())
    }

    @Test
    fun atribuir_equipa_valida_as_regras() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(), operador)
        val ocupada = repositorio.criarAvaria(novaAvaria(), operador)
        repositorio.assumir(ocupada, mario, null)

        assertFalse(repositorio.atribuirEquipa(id, carlos.id, listOf(mario.id), chefe)) // ajudante já ocupado
        assertFalse(repositorio.atribuirEquipa(id, carlos.id, listOf(joao.id), chefe)) // outra oficina
        assertFalse(repositorio.atribuirEquipa(id, carlos.id, listOf(carlos.id), chefe)) // o responsável não é ajudante
        assertFalse(repositorio.atribuirEquipa(id, null, listOf(joaquim.id), chefe)) // ajudantes sem responsável
        assertFalse(repositorio.atribuirEquipa(id, carlos.id, listOf(joaquim.id), carlos)) // só o chefe de oficina
        assertTrue(db.avariaTecnicoDao().obterDaAvaria(id).isEmpty())
        assertEquals(null, db.avariaDao().obter(id)!!.atribuidaAId)
    }

    @Test
    fun a_equipa_nao_muda_o_mttr_nem_as_contagens() = runBlocking {
        val id = emCurso()
        repositorio.juntarAIntervencao(id, joaquim, lotoConfirmado = true)
        repositorio.juntarAIntervencao(id, mario, lotoConfirmado = true)
        // três técnicos na avaria, mas a consulta continua a devolver uma só avaria
        assertEquals(1, db.avariaDao().obterDaFabrica(null, Avaria.ESTADOS).first().size)
        assertEquals(2, db.avariaDao().obterResumo(id).first()!!.ajudantes)

        repositorio.concluir(id, carlos, retomarProducao = false)
        val avarias = db.avariaDao().obterDaFabrica(null, Avaria.ESTADOS).first()
        assertEquals(1, avarias.size)
        val avaria = avarias.single().avaria
        assertEquals(1, Indicadores.resumo(listOf(avaria)).resolvidas)
        assertEquals(avaria.duracaoMs(0), Indicadores.mttr(listOf(avaria))) // a duração é a da avaria, não a soma dos técnicos
        assertEquals(1, Indicadores.porEquipamento(avarias).single().avarias)
    }

    // ---------- auxiliares ----------

    /** Cria uma avaria mecânica e o Carlos assume-a (é o responsável). */
    private suspend fun emCurso(): Long {
        val id = repositorio.criarAvaria(novaAvaria(), operador)
        assertEquals(Repositorio.ResultadoAssumir.OK, repositorio.assumir(id, carlos, null))
        return id
    }

    /** Os técnicos que estão agora na avaria (ids). */
    private suspend fun equipa(avariaId: Long) = db.avariaTecnicoDao().obterAtuaisLista(avariaId).map { it.tecnicoId }

    /** As avarias pendentes ou em curso que a lista do técnico mostra (ids). */
    private suspend fun listaDoTecnico(tecnicoId: String) =
        repositorio.avariasDaOficina(listOf(db.pessoaDao().obter(tecnicoId)!!.especialidade!!), Avaria.ESTADOS, tecnicoId).first().map { it.avaria.id }

    private fun novaAvaria(especialidade: String = Avaria.MECANICA) = Avaria(
        codigo = "", equipamentoId = "EQ", titulo = "Encravamento", descricao = "", prioridade = Avaria.MEDIA,
        especialidade = especialidade, estado = Avaria.PENDENTE, reportadaPorId = operador.id, dataCriacao = System.currentTimeMillis()
    )

    private fun pessoa(id: String, papel: String, especialidade: String?) =
        Pessoa(id, id, "função", papel, "SEC", "M", especialidade, null)
}
