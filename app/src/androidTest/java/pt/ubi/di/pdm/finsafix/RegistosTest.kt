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
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.Peca
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Preventiva
import pt.ubi.di.pdm.finsafix.data.Registo
import pt.ubi.di.pdm.finsafix.data.Repositorio
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.util.Seguranca

/** Pedidos, notas, receção do turno, rondas, preventiva e stock, com uma base de dados em memória. */
@RunWith(AndroidJUnit4::class)
class RegistosTest {

    private lateinit var db: AppDatabase
    private lateinit var repositorio: Repositorio

    private val operador = pessoa("op", Pessoa.PAPEL_OPERADOR, "SEC")
    private val outroOperador = pessoa("op2", Pessoa.PAPEL_OPERADOR, "SEC")
    private val chefe = pessoa("chefe", Pessoa.PAPEL_CHEFE, "SEC")
    private val chefeDeOutraSecao = pessoa("chefe2", Pessoa.PAPEL_CHEFE, "SEC2")
    private val mecanico = pessoa("mec", Pessoa.PAPEL_TECNICO, "OFI", Avaria.MECANICA)
    private val eletricista = pessoa("ele", Pessoa.PAPEL_TECNICO, "OFI2", Avaria.ELETRICA)
    private val engenheiro = pessoa("eng", Pessoa.PAPEL_ENGENHEIRO, null)

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
        db.equipamentoDao().inserirTodos(listOf(Equipamento("EQ", "Equipamento 1", "SEC")))
        db.pessoaDao().inserirTodas(listOf(operador, outroOperador, chefe, chefeDeOutraSecao, mecanico, eletricista, engenheiro))
    }

    @After
    fun fecharBaseDeDados() = db.close()

    // ---------- Pedidos ----------

    @Test
    fun o_pedido_de_ajuda_passa_por_aberto_a_caminho_e_atendido() = runBlocking {
        val id = repositorio.criarPedido(Registo.AJUDA, "chefe", "Falta um operador", urgente = true, quem = operador)
        assertEquals(1, repositorio.contarPedidosEmAberto("SEC").first())

        assertTrue(repositorio.responderPedido(id, "Vou já aí", chefe))
        val respondido = db.registoDao().obter(id)!!
        assertEquals(Registo.A_CAMINHO, respondido.estado)
        assertEquals(chefe.id, respondido.tratadoPorId)

        assertTrue(repositorio.confirmarPedido(id, operador)) // “Já fui atendido”
        assertEquals(Registo.ATENDIDO, db.registoDao().obter(id)!!.estado)
        assertEquals(0, repositorio.contarPedidosEmAberto("SEC").first())
        assertFalse(repositorio.confirmarPedido(id, operador)) // já não está aberto
    }

    @Test
    fun so_o_chefe_da_secao_responde_e_fecha() = runBlocking {
        val id = repositorio.criarPedido(Registo.AJUDA, "qualidade", "", urgente = false, quem = operador)
        assertFalse(repositorio.responderPedido(id, "Vou já aí", chefeDeOutraSecao))
        assertFalse(repositorio.responderPedido(id, "Vou já aí", chefeDeOutraSecao.copy(postoId = "SEC"))) // a secção é relida da base de dados
        assertFalse(repositorio.responderPedido(id, "Vou já aí", operador))
        assertFalse(repositorio.fecharPedido(id, chefeDeOutraSecao))
        assertEquals(Registo.ABERTO, db.registoDao().obter(id)!!.estado)
        assertTrue(repositorio.fecharPedido(id, chefe))
        assertEquals(Registo.FECHADO, db.registoDao().obter(id)!!.estado)
    }

    @Test
    fun so_quem_fez_o_pedido_confirma_e_so_o_operador_pede() = runBlocking {
        val id = repositorio.criarPedido(Registo.MATERIAL, null, "10 × Paletes vazias", urgente = false, quem = operador)
        assertFalse(repositorio.confirmarPedido(id, outroOperador))
        assertTrue(repositorio.confirmarPedido(id, operador))
        assertEquals(Registo.RECEBIDO, db.registoDao().obter(id)!!.estado)

        assertFalha { repositorio.criarPedido(Registo.MATERIAL, null, "x", false, mecanico) }
        assertFalha { repositorio.criarPedido(Registo.MATERIAL, null, "x", false, engenheiro) }
        assertFalha { repositorio.criarPedido(Registo.MATERIAL, null, " ", false, operador) } // o material é obrigatório
        assertFalha { repositorio.criarPedido(Registo.AJUDA, "ninguem", "", false, operador) } // destino inválido
        assertEquals(1, repositorio.pedidosDaSecao("SEC").first().size)
    }

    // ---------- Notas e receção do turno ----------

    @Test
    fun a_nota_precisa_de_5_caracteres_e_a_engenharia_nao_escreve() = runBlocking {
        repositorio.criarNota("atencao", "Disco com ruído", operador)
        assertEquals(1, repositorio.notasDoPosto("SEC").first().size)
        assertFalha { repositorio.criarNota("atencao", "Ok", operador) }
        assertFalha { repositorio.criarNota("inventada", "Disco com ruído", operador) }
        assertFalha { repositorio.criarNota("atencao", "Disco com ruído", engenheiro) }
        assertEquals(1, repositorio.contarNotas("SEC", outroOperador.id).first()) // o outro operador tem 1 nota por ler
        assertEquals(0, repositorio.contarNotas("SEC", operador.id).first()) // quem a escreveu, nenhuma
    }

    @Test
    fun a_rececao_do_turno_confirma_se_uma_so_vez_por_pessoa() = runBlocking {
        assertTrue(repositorio.confirmarRececao(operador))
        assertFalse(repositorio.confirmarRececao(operador)) // no mesmo turno
        assertTrue(repositorio.confirmarRececao(outroOperador))
        assertFalha { repositorio.confirmarRececao(engenheiro) }
        assertEquals(2, repositorio.rececoesDoPosto("SEC").first().size)
    }

    // ---------- Rondas ----------

    @Test
    fun a_ronda_e_do_tecnico_e_so_com_pontos_da_sua_especialidade() = runBlocking {
        repositorio.registarRonda("SEC", emptyList(), "", mecanico)
        repositorio.registarRonda("SEC", listOf("Vibração", "Ruído"), "Rolamento a aquecer", mecanico)
        val rondas = repositorio.rondasDa(Avaria.MECANICA).first()
        assertEquals(setOf(Registo.CONFORME, Registo.NAO_CONFORME), rondas.map { it.registo.estado }.toSet())
        assertEquals("Vibração; Ruído", rondas.single { it.registo.estado == Registo.NAO_CONFORME }.registo.extra)
        assertEquals(0, repositorio.rondasDa(Avaria.ELETRICA).first().size)

        assertFalha { repositorio.registarRonda("SEC", listOf("Cabos e calhas sem danos"), "", mecanico) } // ponto da elétrica
        assertFalha { repositorio.registarRonda("SEC", emptyList(), "", operador) }
        assertFalha { repositorio.registarRonda("SEC", emptyList(), "", engenheiro) }
    }

    // ---------- Preventiva e stock ----------

    @Test
    fun a_execucao_da_preventiva_so_a_regista_um_tecnico_da_mesma_especialidade() = runBlocking {
        val antiga = System.currentTimeMillis() - 100 * Preventiva.HORA_MS
        db.preventivaDao().inserirTodas(listOf(Preventiva(equipamentoId = "EQ", titulo = "Lubrificar", especialidade = Avaria.MECANICA, intervaloH = 80, ultimaData = antiga)))
        val id = db.preventivaDao().obterTodas().first().single().preventiva.id
        assertEquals(Preventiva.Estado.VENCIDA, db.preventivaDao().obter(id)!!.estado(System.currentTimeMillis()))

        assertFalse(repositorio.registarExecucao(id, eletricista)) // outra especialidade
        assertFalha { repositorio.registarExecucao(id, operador) }
        assertTrue(repositorio.registarExecucao(id, mecanico))
        val feita = db.preventivaDao().obter(id)!!
        assertEquals(mecanico.id, feita.ultimaPorId)
        assertEquals(Preventiva.Estado.EM_DIA, feita.estado(System.currentTimeMillis()))
    }

    @Test
    fun o_stock_nunca_fica_negativo_e_a_reposicao_pede_se_uma_vez() = runBlocking {
        db.pecaDao().inserirTodas(listOf(Peca(id = "P1", nome = "Cinta", equipamentoId = "EQ", especialidade = Avaria.MECANICA, minimo = 4, stock = 3)))

        assertTrue(repositorio.pedirReposicao("P1", mecanico))
        assertFalse(repositorio.pedirReposicao("P1", mecanico)) // já pedida
        assertFalse(repositorio.pedirReposicao("P1", eletricista)) // outra especialidade
        assertEquals(mecanico.id, db.pecaDao().obter("P1")!!.pedidaPorId)

        assertTrue(repositorio.ajustarStock("P1", -5, mecanico))
        assertEquals(0, db.pecaDao().obter("P1")!!.stock) // não passa de zero
        assertFalse(repositorio.ajustarStock("P1", 1, eletricista))
        assertFalha { repositorio.ajustarStock("P1", 1, engenheiro) }

        repositorio.ajustarStock("P1", 5, mecanico) // chega ao mínimo: a reposição fecha-se
        val reposta = db.pecaDao().obter("P1")!!
        assertEquals(5, reposta.stock)
        assertNull(reposta.pedidaPorId)
        assertFalse(repositorio.pedirReposicao("P1", mecanico)) // já não está abaixo do mínimo
    }

    @Test
    fun a_peca_substituida_desconta_do_stock_so_na_intervencao_do_proprio_tecnico() = runBlocking {
        db.pecaDao().inserirTodas(listOf(Peca(id = "P1", nome = "Cinta", equipamentoId = "EQ", especialidade = Avaria.MECANICA, minimo = 2, stock = 3)))
        val avariaId = repositorio.criarAvaria(
            Avaria(
                codigo = "", equipamentoId = "EQ", titulo = "Cinta rota", descricao = "", prioridade = Avaria.MEDIA, especialidade = Avaria.MECANICA,
                estado = Avaria.PENDENTE, reportadaPorId = operador.id, dataCriacao = System.currentTimeMillis()
            ),
            operador
        )
        assertFalha { repositorio.registarPecaUsada(avariaId, "P1", 1, mecanico) } // ainda não assumiu a intervenção
        repositorio.assumir(avariaId, mecanico, null)

        assertFalha { repositorio.registarPecaUsada(avariaId, "P1", 1, eletricista) } // não é o técnico da intervenção
        assertFalha { repositorio.registarPecaUsada(avariaId, "P1", 0, mecanico) } // quantidade inválida
        assertFalse(repositorio.registarPecaUsada(avariaId, "P1", 4, mecanico)) // só há 3 em stock
        assertEquals(3, db.pecaDao().obter("P1")!!.stock)

        assertTrue(repositorio.registarPecaUsada(avariaId, "P1", 2, mecanico))
        assertEquals(1, db.pecaDao().obter("P1")!!.stock)
        assertEquals(2, repositorio.pecasDaAvaria(avariaId).first().single().usada.quantidade)
    }

    // ---------- Auxiliares ----------

    // ---------- Checklist de arranque, passagens de turno e validação de rondas ----------

    @Test
    fun a_checklist_de_arranque_e_uma_por_turno_e_so_o_operador_a_faz() = runBlocking {
        assertEquals(0, repositorio.contarChecklistsDoTurno("SEC").first())
        assertFalha { repositorio.registarChecklist(List(6) { true }, mecanico) }
        assertFalha { repositorio.registarChecklist(List(5) { true }, operador) } // são 6 pontos

        assertTrue(repositorio.registarChecklist(listOf(true, true, false, true, true, true), operador))
        assertFalse(repositorio.registarChecklist(List(6) { true }, outroOperador)) // já foi feita neste turno
        val checklist = db.registoDao().obterDaSecao("SEC", listOf(Registo.CHECKLIST)).first().single().registo
        assertEquals(Registo.NAO_CONFORME, checklist.estado)
        assertEquals(1, repositorio.contarPorValidar("SEC").first())

        // Só o chefe da secção a vê; depois deixa de estar por validar
        assertFalse(repositorio.validarChecklist(checklist.id, chefeDeOutraSecao))
        assertTrue(repositorio.validarChecklist(checklist.id, chefe))
        assertEquals(Registo.VALIDADA, db.registoDao().obter(checklist.id)!!.estado)
        assertEquals(0, repositorio.contarPorValidar("SEC").first())
    }

    @Test
    fun a_passagem_de_turno_so_a_confirma_o_chefe_da_secao() = runBlocking {
        assertTrue(repositorio.confirmarRececao(operador))
        val id = db.registoDao().obterDaSecao("SEC", listOf(Registo.RECECAO)).first().single().registo.id
        assertFalse(repositorio.confirmarPassagem(id, chefeDeOutraSecao))
        assertFalse(repositorio.confirmarPassagem(id, operador))
        assertTrue(repositorio.confirmarPassagem(id, chefe))
        assertFalse(repositorio.confirmarPassagem(id, chefe)) // já confirmada
        assertEquals(Registo.CONFIRMADA, db.registoDao().obter(id)!!.estado)
    }

    @Test
    fun so_o_chefe_de_oficina_da_especialidade_valida_uma_ronda() = runBlocking {
        val chefeMecanica = pessoa("ofmec", Pessoa.PAPEL_CHEFE, "OFI", Avaria.MECANICA)
        val chefeEletrica = pessoa("ofele", Pessoa.PAPEL_CHEFE, "OFI2", Avaria.ELETRICA)
        db.pessoaDao().inserirTodas(listOf(chefeMecanica, chefeEletrica))
        repositorio.registarRonda("SEC", listOf(Registo.PONTOS_RONDA.getValue(Avaria.MECANICA).first()), "Vibração forte", mecanico)
        val id = db.registoDao().obterDaSecao("SEC", listOf(Registo.RONDA)).first().single().registo.id

        assertFalse(repositorio.validarRonda(id, chefe)) // chefe de secção
        assertFalse(repositorio.validarRonda(id, chefeEletrica)) // outra especialidade
        assertTrue(repositorio.validarRonda(id, chefeMecanica))
        assertEquals(Registo.VALIDADA, db.registoDao().obter(id)!!.estado)
        assertFalse(repositorio.validarRonda(id, chefeMecanica)) // já validada
    }

    private fun pessoa(id: String, papel: String, postoId: String?, especialidade: String? = null) =
        Pessoa(id, id, "função", papel, postoId, "M", especialidade, Seguranca.hashPin(id, "1234"))

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
