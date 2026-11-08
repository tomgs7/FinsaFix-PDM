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
import pt.ubi.di.pdm.finsafix.data.Aviso
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.Parametro
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Repositorio
import pt.ubi.di.pdm.finsafix.data.Secao
import java.time.LocalDate

/**
 * Testes do Repositório com uma base de dados em memória: cada teste começa vazio e nada fica gravado.
 * Correm no emulador (pasta androidTest) porque o Room precisa do SQLite do Android.
 */
@RunWith(AndroidJUnit4::class)
class RepositorioTest {

    private lateinit var db: AppDatabase
    private lateinit var repositorio: Repositorio

    private val operador = pessoa("op", Pessoa.PAPEL_OPERADOR, null)
    private val tecnico = pessoa("tec", Pessoa.PAPEL_TECNICO, Avaria.MECANICA)
    private val outroTecnico = pessoa("tec2", Pessoa.PAPEL_TECNICO, Avaria.MECANICA)

    @Before
    fun criarBaseDeDados() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repositorio = Repositorio(context, db)
        db.secaoDao().inserirTodas(listOf(Secao("SEC", "Secção", "t", Secao.TIPO_SECAO)))
        db.equipamentoDao().inserirTodos(listOf(Equipamento("EQ", "Equipamento", "SEC")))
        db.pessoaDao().inserirTodas(listOf(operador, tecnico, outroTecnico))
    }

    @After
    fun fecharBaseDeDados() = db.close()

    @Test
    fun criar_da_o_codigo_a_partir_do_id_e_regista_o_evento() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(), operador)

        val gravada = db.avariaDao().obter(id)!!
        assertEquals(String.format("AV-%d-%04d", LocalDate.now().year, id), gravada.codigo)
        assertEquals(1, db.eventoAvariaDao().obterDaAvariaLista(id).size)
    }

    @Test
    fun assumir_poe_em_curso_e_recusa_uma_segunda_intervencao() = runBlocking {
        val primeira = repositorio.criarAvaria(novaAvaria(), operador)
        val segunda = repositorio.criarAvaria(novaAvaria(), operador)

        assertEquals(Repositorio.ResultadoAssumir.OK, repositorio.assumir(primeira, tecnico, null))
        assertEquals(Avaria.EM_CURSO, db.avariaDao().obter(primeira)!!.estado)
        assertEquals(Repositorio.ResultadoAssumir.JA_TEM_INTERVENCAO, repositorio.assumir(segunda, tecnico, null))
        assertEquals(Repositorio.ResultadoAssumir.JA_NAO_ESTA_PENDENTE, repositorio.assumir(primeira, outroTecnico, null))
    }

    @Test
    fun assumir_uma_avaria_nao_sei_grava_a_classificacao() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(especialidade = Avaria.NAO_SEI), operador)
        repositorio.assumir(id, tecnico, Avaria.ELETRICA)
        assertEquals(Avaria.ELETRICA, db.avariaDao().obter(id)!!.especialidade)
    }

    @Test
    fun concluir_passa_a_resolvida_e_so_o_tecnico_da_intervencao_pode() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(), operador)
        repositorio.assumir(id, tecnico, null)

        assertFalha { repositorio.concluir(id, outroTecnico, retomarProducao = false) }
        repositorio.concluir(id, tecnico, retomarProducao = false)

        val avaria = db.avariaDao().obter(id)!!
        assertEquals(Avaria.RESOLVIDA, avaria.estado)
        assertNotNull(avaria.dataFim)
    }

    @Test
    fun retomar_nao_conta_o_tempo_em_que_esteve_em_progresso() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(), operador)
        repositorio.assumir(id, tecnico, null)
        repositorio.deixarEmProgresso(id, tecnico, "Chefe de turno", Avaria.RETOMA_TURNO, "Falta de peça", "Falta a peça")

        // Simula que a avaria ficou parada durante uma hora.
        val hora = 3_600_000L
        val emEspera = db.avariaDao().obter(id)!!
        db.avariaDao().atualizar(emEspera.copy(progressoData = emEspera.progressoData!! - hora))
        repositorio.assumir(id, outroTecnico, null)

        val retomada = db.avariaDao().obter(id)!!
        assertTrue(retomada.tempoParadoMs >= hora)
        assertNull(retomada.progressoData)
        assertEquals("Falta a peça", retomada.progressoNota) // a nota continua visível
    }

    @Test
    fun eliminar_apaga_a_cronologia_e_restaurar_volta_a_por_tudo() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(), operador)
        val avaria = db.avariaDao().obter(id)!!

        val eliminada = repositorio.eliminarAvaria(avaria, operador)
        assertNull(db.avariaDao().obter(id))
        assertEquals(0, db.eventoAvariaDao().obterDaAvariaLista(id).size) // CASCADE

        repositorio.restaurarAvaria(eliminada)
        assertEquals(avaria, db.avariaDao().obter(id))
        assertEquals(eliminada.eventos.size, db.eventoAvariaDao().obterDaAvariaLista(id).size)
    }

    @Test
    fun uma_avaria_critica_abre_uma_paragem_que_desaparece_e_volta_com_o_desfazer() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(prioridade = Avaria.CRITICA), operador)
        assertEquals(1, db.paragemDao().obterDaAvaria(id).size)

        val eliminada = repositorio.eliminarAvaria(db.avariaDao().obter(id)!!, operador)
        assertNull(db.paragemDao().obterAtivaDoEquipamento("EQ"))

        repositorio.restaurarAvaria(eliminada)
        assertEquals(id, db.paragemDao().obterAtivaDoEquipamento("EQ")!!.avariaId)
    }

    @Test
    fun concluir_e_retomar_termina_a_paragem() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(prioridade = Avaria.CRITICA), operador)
        repositorio.assumir(id, tecnico, null)
        repositorio.concluir(id, tecnico, retomarProducao = true)
        assertNull(db.paragemDao().obterAtivaDoEquipamento("EQ"))
    }

    @Test
    fun nao_se_retoma_a_producao_com_a_avaria_por_resolver() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(prioridade = Avaria.CRITICA), operador)
        val paragem = db.paragemDao().obterAtivaDoEquipamento("EQ")!!
        assertFalha { repositorio.retomarProducao(paragem.id, operador) }

        repositorio.assumir(id, tecnico, null)
        repositorio.concluir(id, tecnico, retomarProducao = false) // "Só concluir"
        repositorio.retomarProducao(paragem.id, operador) // agora já pode
        assertNull(db.paragemDao().obterAtivaDoEquipamento("EQ"))
    }

    @Test
    fun cada_parametro_mostra_a_leitura_mais_recente() = runBlocking {
        db.parametroDao().inserirTodas(listOf(Parametro("SEC-p", "SEC", "Pressão", "bar", 5.5, 7.0, 1, Parametro.LIMITE_INTERVALO)))
        repositorio.registarLeitura("SEC-p", 6.0, operador)
        Thread.sleep(5) // datas diferentes
        repositorio.registarLeitura("SEC-p", 6.4, operador)
        assertEquals(6.4, repositorio.parametrosDaSecao("SEC").first().single().valor!!, 0.0)
    }

    @Test
    fun o_total_do_turno_so_conta_esse_turno() = runBlocking {
        repositorio.registarProducao("EQ", "M", 30.0, operador)
        repositorio.registarProducao("EQ", "T", 46.0, operador)
        val tarde = repositorio.producaoDoTurno("SEC", "T", 0).first()
        assertEquals(listOf(46.0), tarde.map { it.producao.quantidade })
    }

    @Test
    fun o_aviso_do_tecnico_chega_a_secao_e_responder_parar_abre_a_paragem() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(), operador)
        assertFalha { repositorio.avisarSecao(id, Aviso.PARAR, tecnico) } // só avisa quem tem a intervenção em curso
        repositorio.assumir(id, tecnico, null)

        assertTrue(repositorio.avisarSecao(id, Aviso.PARAR, tecnico))
        assertFalse(repositorio.avisarSecao(id, Aviso.PARAR, tecnico)) // igual e ainda por responder
        val aviso = repositorio.avisoUrgente("SEC").first()!!.aviso
        assertEquals("SEC", aviso.secaoId)

        assertTrue(repositorio.responderAviso(aviso.id, operador))
        assertFalse(repositorio.responderAviso(aviso.id, operador)) // já respondido
        assertEquals(id, db.paragemDao().obterAtivaDoEquipamento("EQ")!!.avariaId)
        assertNull(repositorio.avisoUrgente("SEC").first())
    }

    @Test
    fun responder_a_um_pedido_antigo_nao_abre_uma_paragem_falsa() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(), operador)
        repositorio.assumir(id, tecnico, null)
        repositorio.avisarSecao(id, Aviso.PARAR, tecnico)
        repositorio.concluir(id, tecnico, retomarProducao = false) // a avaria acaba antes de o operador responder

        val aviso = repositorio.avisoUrgente("SEC").first()!!.aviso
        assertTrue(repositorio.responderAviso(aviso.id, operador)) // o banner fecha...
        assertNull(db.paragemDao().obterAtivaDoEquipamento("EQ")) // ...mas a máquina não fica "parada"
    }

    @Test
    fun avisar_o_chefe_nao_cria_banner_mas_aparece_nos_avisos_recentes() = runBlocking {
        repositorio.avisarChefe("SEC", "Pressão: 8,0 bar", operador)
        assertNull(repositorio.avisoUrgente("SEC").first())
        assertEquals(1, repositorio.avisosRecentes("SEC").first().size)
    }

    @Test
    fun nao_ha_duas_paragens_ativas_no_mesmo_equipamento() = runBlocking {
        assertTrue(repositorio.registarParagem("EQ", "Limpeza", operador))
        assertFalse(repositorio.registarParagem("EQ", "Limpeza", operador))
        repositorio.retomarProducao(db.paragemDao().obterAtivaDoEquipamento("EQ")!!.id, operador)
        assertTrue(repositorio.registarParagem("EQ", "Limpeza", operador))
    }

    @Test
    fun nao_se_elimina_uma_avaria_que_ja_comecou_a_ser_tratada() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(), operador)
        repositorio.assumir(id, tecnico, null)
        assertFalha { repositorio.eliminarAvaria(db.avariaDao().obter(id)!!, operador) }
    }

    @Test
    fun a_chave_estrangeira_impede_uma_avaria_de_um_equipamento_que_nao_existe() = runBlocking {
        assertFalha { db.avariaDao().inserir(novaAvaria(equipamentoId = "NAO-EXISTE").copy(codigo = "X")) }
    }

    @Test
    fun o_codigo_da_avaria_e_unico() = runBlocking {
        db.avariaDao().inserir(novaAvaria().copy(codigo = "AV-1"))
        assertFalha { db.avariaDao().inserir(novaAvaria().copy(codigo = "AV-1")) }
    }

    @Test
    fun editar_so_muda_os_campos_do_formulario_e_so_quem_reportou() = runBlocking {
        // Erro corrigido: guardar uma edição "antiga" repunha o estado pendente e apagava o técnico.
        val id = repositorio.criarAvaria(novaAvaria(), operador)
        val aberta = db.avariaDao().obter(id)!! // o que o formulário de edição abriu
        repositorio.assumir(id, tecnico, null) // entretanto, o técnico assume
        assertFalha { repositorio.editarAvaria(aberta.copy(titulo = "Outro título"), operador) }
        val atual = db.avariaDao().obter(id)!!
        assertEquals(Avaria.EM_CURSO, atual.estado)
        assertEquals(tecnico.id, atual.tecnicoId)
        assertEquals("Encravamento", atual.titulo)
    }

    @Test
    fun outro_operador_nao_edita_a_avaria_de_quem_reportou() = runBlocking {
        val id = repositorio.criarAvaria(novaAvaria(), operador)
        val outro = pessoa("op2", Pessoa.PAPEL_OPERADOR, null)
        db.pessoaDao().inserirTodas(listOf(outro))
        assertFalha { repositorio.editarAvaria(db.avariaDao().obter(id)!!.copy(titulo = "Alterada"), outro) }
        repositorio.editarAvaria(db.avariaDao().obter(id)!!.copy(titulo = "Corrigida"), operador) // quem reportou pode
        assertEquals("Corrigida", db.avariaDao().obter(id)!!.titulo)
    }

    private fun novaAvaria(
        equipamentoId: String = "EQ",
        especialidade: String = Avaria.MECANICA,
        prioridade: String = Avaria.MEDIA
    ) = Avaria(
        codigo = "", equipamentoId = equipamentoId, titulo = "Encravamento", descricao = "",
        prioridade = prioridade, especialidade = especialidade, estado = Avaria.PENDENTE,
        reportadaPorId = operador.id, dataCriacao = System.currentTimeMillis()
    )

    private fun pessoa(id: String, papel: String, especialidade: String?) =
        Pessoa(id, id, "função", papel, "SEC", "M", especialidade, null)

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
