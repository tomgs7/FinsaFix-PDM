package pt.ubi.di.pdm.finsafix

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.ubi.di.pdm.finsafix.data.AppDatabase
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Registo
import pt.ubi.di.pdm.finsafix.data.Repositorio
import pt.ubi.di.pdm.finsafix.data.Secao

/** Os ecrãs Equipa e Turno do chefe de oficina: que técnicos e que equipas aparecem, e as notas entre chefes (base de dados em memória). */
@RunWith(AndroidJUnit4::class)
class EquipaOficinaTest {

    private lateinit var db: AppDatabase
    private lateinit var repositorio: Repositorio

    private val operador = pessoa("op", Pessoa.PAPEL_OPERADOR, "SEC", null)
    private val carlos = pessoa("carlos", Pessoa.PAPEL_TECNICO, "OFI-MEC", Avaria.MECANICA)
    private val joaquim = pessoa("joaquim", Pessoa.PAPEL_TECNICO, "OFI-MEC", Avaria.MECANICA)
    private val pedro = pessoa("pedro", Pessoa.PAPEL_TECNICO, "OFI-ELE", Avaria.ELETRICA)
    private val joao = pessoa("joao", Pessoa.PAPEL_TECNICO, "OFI-ELE", Avaria.ELETRICA)
    private val chefeManha = pessoa("chefe_m", Pessoa.PAPEL_CHEFE, "OFI-MEC", Avaria.MECANICA, turno = "M")
    private val chefeTarde = pessoa("chefe_t", Pessoa.PAPEL_CHEFE, "OFI-MEC", Avaria.MECANICA, turno = "T")

    @Before
    fun criarBaseDeDados() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repositorio = Repositorio(context, db)
        db.secaoDao().inserirTodas(
            listOf(
                Secao("SEC", "Secção", "t", Secao.TIPO_SECAO),
                Secao("OFI-MEC", "Oficina Mecânica", "", Secao.TIPO_OFICINA),
                Secao("OFI-ELE", "Oficina Elétrica", "", Secao.TIPO_OFICINA)
            )
        )
        db.equipamentoDao().inserirTodos(listOf(Equipamento("EQ", "Equipamento", "SEC")))
        db.pessoaDao().inserirTodas(listOf(operador, carlos, joaquim, pedro, joao, chefeManha, chefeTarde))
    }

    @After
    fun fecharBaseDeDados() = db.close()

    @Test
    fun a_equipa_mostra_so_os_tecnicos_da_oficina_e_a_equipa_certa_de_cada_avaria() = runBlocking {
        val mecanica = repositorio.criarAvaria(novaAvaria(Avaria.MECANICA), operador)
        val eletrica = repositorio.criarAvaria(novaAvaria(Avaria.ELETRICA), operador)
        repositorio.assumir(mecanica, carlos, null)
        repositorio.juntarAIntervencao(mecanica, joaquim, lotoConfirmado = true)
        repositorio.assumir(eletrica, pedro, null)
        repositorio.juntarAIntervencao(eletrica, joao, lotoConfirmado = true)

        // Os técnicos do posto da oficina mecânica: só os mecânicos
        val tecnicos = repositorio.pessoasDoPosto("OFI-MEC").first().filter { it.papel == Pessoa.PAPEL_TECNICO }
        assertEquals(listOf("carlos", "joaquim"), tecnicos.map { it.id }.sorted())

        // As equipas das intervenções da oficina mecânica: cada técnico na sua avaria, sem os da elétrica
        val equipas = repositorio.equipasDasOficinas(listOf(Avaria.MECANICA, Avaria.NAO_SEI)).first()
        assertEquals(setOf("carlos", "joaquim"), equipas.map { it.participacao.tecnicoId }.toSet())
        assertTrue(equipas.all { it.participacao.avariaId == mecanica })
        // e a elétrica vê as suas
        val eletricas = repositorio.equipasDasOficinas(listOf(Avaria.ELETRICA, Avaria.NAO_SEI)).first()
        assertEquals(setOf("pedro", "joao"), eletricas.map { it.participacao.tecnicoId }.toSet())

        // Os eventos (as atualizações) são só das avarias em curso da oficina
        val eventos = repositorio.eventosDasAvariasEmCurso(listOf(Avaria.MECANICA, Avaria.NAO_SEI)).first()
        assertTrue(eventos.isNotEmpty() && eventos.all { it.evento.avariaId == mecanica })
        assertTrue(eventos.any { it.pessoaNome == "carlos" })
    }

    @Test
    fun uma_nota_da_manha_aparece_ao_chefe_da_tarde_e_desaparece_depois_de_confirmada() = runBlocking {
        repositorio.criarNota(Registo.NOTA_ATENCAO, "Vigiar o variador da prensa MDF", chefeManha)

        // Aparece ao chefe da tarde, com o número do menu; não aparece a quem a escreveu nem aos técnicos
        assertEquals(1, repositorio.notasParaChefe("OFI-MEC", chefeTarde.id).first().size)
        assertTrue(repositorio.notasParaChefe("OFI-MEC", chefeManha.id).first().isEmpty())
        assertTrue(repositorio.notasDoPosto("OFI-MEC").first().isEmpty())
        assertEquals(0, repositorio.contarNotas("OFI-MEC", carlos.id).first())

        // Depois de “Li as notas” a receção fica confirmada e a nota desaparece
        assertTrue(repositorio.confirmarRececao(chefeTarde))
        assertTrue(repositorio.notasParaChefe("OFI-MEC", chefeTarde.id).first().isEmpty())
        val rececao = repositorio.rececoesDoPosto("OFI-MEC").first().single().registo
        assertEquals(Registo.CONFIRMADA, rececao.estado)
        assertEquals(chefeTarde.id, rececao.tratadoPorId)

        // Uma nota nova, escrita depois, volta a aparecer
        delay(5)
        repositorio.criarNota(Registo.NOTA_PENDENTE, "Falta confirmar a peça", chefeManha)
        assertEquals(1, repositorio.notasParaChefe("OFI-MEC", chefeTarde.id).first().size)
    }

    @Test
    fun as_notas_dos_tecnicos_continuam_visiveis_a_todos_e_nao_vao_para_o_chefe() = runBlocking {
        repositorio.criarNota(Registo.NOTA_INFORMACAO, "Óleo reposto na prensa", carlos)
        assertEquals(1, repositorio.notasDoPosto("OFI-MEC").first().size)
        assertFalse(repositorio.notasParaChefe("OFI-MEC", chefeTarde.id).first().any { it.registo.pessoaId == carlos.id })
    }

    private fun novaAvaria(especialidade: String) = Avaria(
        codigo = "", equipamentoId = "EQ", titulo = "Encravamento", descricao = "", prioridade = Avaria.MEDIA,
        especialidade = especialidade, estado = Avaria.PENDENTE, reportadaPorId = operador.id, dataCriacao = System.currentTimeMillis()
    )

    private fun pessoa(id: String, papel: String, posto: String, especialidade: String?, turno: String = "M") =
        Pessoa(id, id, "função", papel, posto, turno, especialidade, null)
}
