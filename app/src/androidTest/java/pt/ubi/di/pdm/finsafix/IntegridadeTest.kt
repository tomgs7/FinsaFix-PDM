package pt.ubi.di.pdm.finsafix

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.ubi.di.pdm.finsafix.data.AppDatabase
import pt.ubi.di.pdm.finsafix.data.Autorizacao
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Aviso
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.EventoAvaria
import pt.ubi.di.pdm.finsafix.data.Leitura
import pt.ubi.di.pdm.finsafix.data.Paragem
import pt.ubi.di.pdm.finsafix.data.Parametro
import pt.ubi.di.pdm.finsafix.data.Peca
import pt.ubi.di.pdm.finsafix.data.PecaUsada
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Repositorio
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.data.Turno

/**
 * As regras que vivem na própria base de dados (chaves estrangeiras, SET_NULL, CASCADE e transações),
 * testadas sem passar pelo Repositório, com uma base de dados em memória.
 */
@RunWith(AndroidJUnit4::class)
class IntegridadeTest {

    private lateinit var db: AppDatabase

    private val operador = pessoa("op", Pessoa.PAPEL_OPERADOR, "SEC", null)
    private val mecanico = pessoa("mec", Pessoa.PAPEL_TECNICO, "OFI", Avaria.MECANICA)
    private val chefe = pessoa("chefe", Pessoa.PAPEL_CHEFE, "SEC", null)

    @Before
    fun criarBaseDeDados() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        db.secaoDao().inserirTodas(listOf(Secao("SEC", "Secção 1", "t", Secao.TIPO_SECAO), Secao("OFI", "Oficina", "", Secao.TIPO_OFICINA)))
        db.equipamentoDao().inserirTodos(listOf(Equipamento("EQ", "Equipamento 1", "SEC")))
        db.pessoaDao().inserirTodas(listOf(operador, mecanico, chefe))
    }

    @After
    fun fecharBaseDeDados() = db.close()

    private fun pessoa(id: String, papel: String, posto: String?, especialidade: String?) =
        Pessoa(id, "Pessoa $id", "Função", papel, posto, Turno.MANHA, especialidade, null)

    private fun avaria(codigo: String, tecnicoId: String? = null, atribuidaAId: String? = null, aprovadaPorId: String? = null) = Avaria(
        codigo = codigo, equipamentoId = "EQ", titulo = "Ruído", descricao = "", prioridade = Avaria.MEDIA, especialidade = Avaria.MECANICA,
        estado = Avaria.PENDENTE, reportadaPorId = operador.id, dataCriacao = 1_000L,
        tecnicoId = tecnicoId, atribuidaAId = atribuidaAId, aprovadaPorId = aprovadaPorId
    )

    @Test
    fun eliminar_uma_avaria_apaga_os_filhos_que_nao_fazem_sentido_e_desliga_os_outros() = runBlocking {
        val avariaId = db.avariaDao().inserir(avaria("AV-1"))
        db.eventoAvariaDao().inserir(EventoAvaria(avariaId = avariaId, texto = "Reportada", pessoaId = operador.id, data = 1L))
        db.pecaDao().inserirTodas(listOf(Peca("P1", "Rolamento", "EQ", Avaria.MECANICA, 1, 5)))
        db.pecaUsadaDao().inserir(PecaUsada(avariaId = avariaId, pecaId = "P1", quantidade = 1, tecnicoId = mecanico.id, data = 2L))
        db.autorizacaoDao().inserir(
            Autorizacao(
                secaoId = "SEC", avariaId = avariaId, tipo = Autorizacao.QUENTE, descricao = "Soldar", estado = Autorizacao.PEDIDA,
                pedidaPorId = mecanico.id, dataPedido = 3L
            )
        )
        val avisoId = db.avisoDao().inserir(Aviso(secaoId = "SEC", tipo = Aviso.PARAR, avariaId = avariaId, criadoPorId = mecanico.id, dataCriacao = 4L))
        val paragemId = db.paragemDao().inserir(Paragem(equipamentoId = "EQ", causa = "Avaria", inicio = 5L, avariaId = avariaId))

        db.avariaDao().eliminar(db.avariaDao().obter(avariaId)!!)

        // CASCADE: sem avaria, estes filhos não fazem sentido
        assertTrue(db.eventoAvariaDao().obterDaAvariaLista(avariaId).isEmpty())
        assertTrue(db.pecaUsadaDao().obterDaAvaria(avariaId).first().isEmpty())
        assertTrue(db.autorizacaoDao().obterAbertasDaAvaria(avariaId).isEmpty())
        // SET_NULL: o aviso e a paragem continuam a existir, só deixam de apontar para a avaria
        assertNull(db.avisoDao().obter(avisoId)!!.avariaId)
        assertNull(db.paragemDao().obter(paragemId)!!.avariaId)
    }

    @Test
    fun apagar_uma_pessoa_deixa_as_avaria_sem_tecnico_em_vez_de_falhar() = runBlocking {
        val id = db.avariaDao().inserir(avaria("AV-2", tecnicoId = mecanico.id, atribuidaAId = mecanico.id, aprovadaPorId = chefe.id))

        db.openHelper.writableDatabase.execSQL("DELETE FROM pessoas WHERE id = 'mec'")
        db.openHelper.writableDatabase.execSQL("DELETE FROM pessoas WHERE id = 'chefe'")

        val depois = db.avariaDao().obter(id)!!
        assertNull(depois.tecnicoId)
        assertNull(depois.atribuidaAId)
        assertNull(depois.aprovadaPorId)
        assertEquals(operador.id, depois.reportadaPorId) // o autor é obrigatório e não se perde
    }

    @Test
    fun nao_se_apaga_quem_reportou_uma_avaria() = runBlocking {
        db.avariaDao().inserir(avaria("AV-3"))
        try {
            db.openHelper.writableDatabase.execSQL("DELETE FROM pessoas WHERE id = 'op'")
            fail("A base de dados devia recusar apagar o autor de uma avaria")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            assertNotNull(db.pessoaDao().obter(operador.id))
        }
    }

    @Test
    fun a_base_de_dados_recusa_um_id_de_pessoa_que_nao_existe() = runBlocking {
        try {
            db.avariaDao().inserir(avaria("AV-4", atribuidaAId = "fantasma"))
            fail("atribuidaAId tem de apontar para uma pessoa que existe")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            assertTrue(db.avariaDao().obterDaFabrica(null, Avaria.ESTADOS).first().isEmpty())
        }
    }

    @Test
    fun uma_transacao_que_falha_nao_deixa_nada_a_meio() = runBlocking {
        val resultado = runCatching {
            db.withTransaction {
                db.avariaDao().inserir(avaria("AV-5")) // 1.º passo: corre bem
                db.eventoAvariaDao().inserir(EventoAvaria(avariaId = 9_999, texto = "Evento órfão", pessoaId = null, data = 1L)) // 2.º: falha
            }
        }
        assertTrue(resultado.isFailure)
        // O 1.º passo foi desfeito
        assertTrue(db.avariaDao().obterDaFabrica(null, Avaria.ESTADOS).first().isEmpty())
    }

    @Test
    fun as_paragens_ativas_sao_so_as_que_ainda_nao_terminaram() = runBlocking {
        db.paragemDao().inserir(Paragem(equipamentoId = "EQ", causa = "Terminada", inicio = 1L, fim = 2L))
        db.paragemDao().inserir(Paragem(equipamentoId = "EQ", causa = "Em curso", inicio = 3L))

        val ativas = db.paragemDao().obterAtivas(null).first()
        assertEquals(listOf("Em curso"), ativas.map { it.paragem.causa })
        assertEquals(1, db.paragemDao().obterAtivas("SEC").first().size)
        assertTrue(db.paragemDao().obterAtivas("OFI").first().isEmpty())
    }

    @Test
    fun os_parametros_com_ultima_leitura_filtram_por_seccao_ou_trazem_todos() = runBlocking {
        db.parametroDao().inserirTodas(
            listOf(
                Parametro("P1", "SEC", "Temperatura", "°C", 0.0, 100.0, 1, Parametro.LIMITE_MAXIMO),
                Parametro("P2", "OFI", "Pressão", "bar", 0.0, 10.0, 1, Parametro.LIMITE_MAXIMO)
            )
        )
        db.leituraDao().inserir(Leitura(parametroId = "P1", valor = 20.0, pessoaId = operador.id, data = 1L))
        db.leituraDao().inserir(Leitura(parametroId = "P1", valor = 25.0, pessoaId = operador.id, data = 2L))

        val daSecao = db.parametroDao().obterComUltimaLeitura("SEC").first()
        assertEquals(1, daSecao.size)
        assertEquals(25.0, daSecao.single().valor!!, 0.0) // a leitura mais recente
        assertEquals(2, db.parametroDao().obterComUltimaLeitura(null).first().size)
        assertNull(db.parametroDao().obterComUltimaLeitura(null).first().first { it.parametro.id == "P2" }.valor)
    }

    // ---------- Verificação final: índices usados e limite da descrição ----------

    /** Devolve as linhas do EXPLAIN QUERY PLAN de uma consulta, para ver se usa um índice. */
    private fun plano(sql: String, vararg argumentos: String): String {
        db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql", argumentos).use { c ->
            val linhas = mutableListOf<String>()
            while (c.moveToNext()) linhas.add(c.getString(c.getColumnIndexOrThrow("detail")))
            return linhas.joinToString(" | ")
        }
    }

    @Test
    fun as_rondas_da_oficina_usam_o_indice_do_tipo() {
        // a consulta de RegistoDao.obterRondas: só filtra por tipo (de todas as secções)
        val p = plano("SELECT r.* FROM registos r JOIN pessoas p ON p.id = r.pessoaId WHERE r.tipo = 'ronda' AND p.especialidade = ? ORDER BY r.dataCriacao DESC", "mecanica")
        assertTrue(p, "USING INDEX index_registos_tipo_dataCriacao" in p)
    }

    @Test
    fun as_avarias_do_tecnico_usam_o_indice_do_tecnico_e_do_estado() {
        val concluidas = plano("SELECT * FROM avarias WHERE tecnicoId = ? AND estado = 'resolvida' ORDER BY dataFim DESC LIMIT 20", "mec")
        val emCurso = plano("SELECT COUNT(*) FROM avarias WHERE tecnicoId = ? AND estado = 'em_curso'", "mec")
        assertTrue(concluidas, "index_avarias_tecnicoId_estado" in concluidas)
        assertTrue(emCurso, "index_avarias_tecnicoId_estado" in emCurso)
    }

    @Test
    fun as_paragens_ativas_e_recentes_usam_indices() {
        val ativas = plano("SELECT * FROM paragens p WHERE p.fim IS NULL ORDER BY p.inicio DESC")
        val recentes = plano("SELECT * FROM paragens p WHERE (p.fim IS NULL OR p.inicio >= ?)", "0")
        assertTrue(ativas, "index_paragens_fim" in ativas)
        assertTrue(recentes, "index_paragens_fim" in recentes && "index_paragens_inicio" in recentes)
    }

    @Test
    fun a_descricao_de_300_caracteres_grava_e_a_de_301_e_recusada_sem_gravar_nada() = runBlocking {
        val repositorio = Repositorio(ApplicationProvider.getApplicationContext<Context>(), db)
        val ok = repositorio.criarAvaria(avaria("x").copy(descricao = "a".repeat(300)), operador)
        assertTrue(ok > 0)
        assertEquals(1, db.avariaDao().obterDaFabrica(null, Avaria.ESTADOS).first().size)

        val recusada = runCatching { repositorio.criarAvaria(avaria("y").copy(descricao = "a".repeat(301)), operador) }
        assertTrue(recusada.exceptionOrNull() is IllegalArgumentException)
        // nem a avaria nem a cronologia ficaram a meio
        assertEquals(1, db.avariaDao().obterDaFabrica(null, Avaria.ESTADOS).first().size)

        val edicao = runCatching { repositorio.editarAvaria(db.avariaDao().obter(ok)!!.copy(descricao = "b".repeat(301)), operador) }
        assertTrue(edicao.exceptionOrNull() is IllegalArgumentException)
        assertEquals("a".repeat(300), db.avariaDao().obter(ok)!!.descricao)
    }
}
