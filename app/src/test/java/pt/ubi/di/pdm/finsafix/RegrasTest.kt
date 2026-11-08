package pt.ubi.di.pdm.finsafix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ubi.di.pdm.finsafix.data.Autorizacao
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.util.BloqueioPin
import pt.ubi.di.pdm.finsafix.util.Sessao
import pt.ubi.di.pdm.finsafix.util.Seguranca
import java.io.File

/** Regras sem Android: o bloqueio de PIN e as regras de uma autorização de trabalho. */
class RegrasTest {

    @Test
    fun tres_pins_errados_bloqueiam_30_segundos() {
        var agora = 0L
        val bloqueio = BloqueioPin { agora }
        assertEquals(2, bloqueio.erro())
        assertEquals(1, bloqueio.erro())
        assertEquals(0L, bloqueio.segundosRestantes())
        assertEquals(0, bloqueio.erro()) // terceiro erro: bloqueado
        assertEquals(30L, bloqueio.segundosRestantes())

        agora = 10_000
        assertEquals(20L, bloqueio.segundosRestantes())
        agora = 30_001
        assertEquals(0L, bloqueio.segundosRestantes()) // passou o bloqueio
        assertEquals(2, bloqueio.erro()) // e as tentativas recomeçam
    }

    @Test
    fun o_bloqueio_sobrevive_a_reiniciar_a_app_porque_o_estado_e_guardado() {
        var agora = 0L
        val guardado = BloqueioPin.EmMemoria() // o que ficou em SharedPreferences
        val antes = BloqueioPin(guardado) { agora }
        antes.erro()
        antes.erro()
        // A app reinicia: um BloqueioPin novo, com o mesmo estado guardado. O terceiro erro ainda conta e bloqueia.
        val depois = BloqueioPin(guardado) { agora }
        assertEquals(0, depois.erro())
        assertEquals(30L, BloqueioPin(guardado) { agora }.segundosRestantes())
        agora = 31_000
        assertEquals(0L, BloqueioPin(guardado) { agora }.segundosRestantes())
    }

    @Test
    fun os_tempos_de_sessao_seguem_a_especificacao() {
        fun pessoa(papel: String) = Pessoa("id", "Nome", "f", papel, null, "M", null, null)
        val minuto = 60_000L
        for (papel in listOf(Pessoa.PAPEL_OPERADOR, Pessoa.PAPEL_TECNICO)) {
            assertEquals(10 * minuto, Sessao.inatividadeMs(pessoa(papel))) // 10 min sem uso ...
            assertEquals(minuto, Sessao.respostaMs(pessoa(papel))) // ... depois "Ainda é X?" durante 60 s
        }
        for (papel in listOf(Pessoa.PAPEL_CHEFE, Pessoa.PAPEL_ENGENHEIRO)) {
            val total = Sessao.inatividadeMs(pessoa(papel)) + Sessao.respostaMs(pessoa(papel))
            assertEquals(3 * minuto, total) // 3 min no total ...
            assertEquals(30_000L, Sessao.respostaMs(pessoa(papel))) // ... com aviso 30 s antes
        }
    }

    @Test
    fun um_pin_certo_volta_a_dar_as_3_tentativas() {
        val bloqueio = BloqueioPin { 0L }
        bloqueio.erro()
        bloqueio.erro()
        bloqueio.acerto()
        assertEquals(2, bloqueio.erro())
    }

    @Test
    fun a_vigilancia_de_fogo_so_conta_no_trabalho_a_quente() {
        val agora = 10 * Autorizacao.VIGILANCIA_MS
        val quente = autorizacao(Autorizacao.QUENTE, terminado = agora - 20 * 60_000L)
        assertEquals(40 * 60_000L, quente.vigilanciaRestanteMs(agora))
        assertEquals(0L, autorizacao(Autorizacao.QUENTE, terminado = agora - 61 * 60_000L).vigilanciaRestanteMs(agora))
        assertEquals(0L, autorizacao(Autorizacao.CONSIGNACAO, terminado = agora).vigilanciaRestanteMs(agora))
        assertEquals(0L, autorizacao(Autorizacao.QUENTE, terminado = null).vigilanciaRestanteMs(agora))
    }

    @Test
    fun so_a_vigilancia_de_fogo_deixa_concluir_com_a_autorizacao_aberta() {
        assertTrue(autorizacao(Autorizacao.QUENTE, Autorizacao.PEDIDA).bloqueiaConclusao())
        assertTrue(autorizacao(Autorizacao.QUENTE, Autorizacao.APROVADA).bloqueiaConclusao())
        assertTrue(autorizacao(Autorizacao.QUENTE, Autorizacao.EM_CURSO).bloqueiaConclusao())
        assertFalse(autorizacao(Autorizacao.QUENTE, Autorizacao.EM_CURSO, terminado = 1).bloqueiaConclusao()) // vigilância
        assertTrue(autorizacao(Autorizacao.CONSIGNACAO, Autorizacao.EM_CURSO, terminado = 1).bloqueiaConclusao())
        assertFalse(autorizacao(Autorizacao.QUENTE, Autorizacao.ENCERRADA).bloqueiaConclusao())
        assertFalse(autorizacao(Autorizacao.QUENTE, Autorizacao.RECUSADA).bloqueiaConclusao())
    }

    @Test
    fun so_a_consignacao_e_o_bloqueio_pedem_cadeado() {
        assertTrue(autorizacao(Autorizacao.CONSIGNACAO).exigeCadeado())
        assertTrue(autorizacao(Autorizacao.BLOQUEIO).exigeCadeado())
        assertFalse(autorizacao(Autorizacao.QUENTE).exigeCadeado())
        assertFalse(autorizacao(Autorizacao.ALTURA).exigeCadeado())
    }

    @Test
    fun o_json_de_demonstracao_so_tem_o_hash_dos_pins() {
        // Erro corrigido: o JSON copiado para a app tinha os PINs em texto ("pinDemo").
        val texto = File("src/main/assets/dados-demo.json").readText()
        assertFalse(Regex("\"pin[A-Za-z]*Demo\"").containsMatchIn(texto))
        assertTrue(texto.contains(Seguranca.hashPin("ch_schel", "2001"))) // o PIN de demonstração do README
    }

    @Test
    fun o_nome_curto_cumprimenta_pelo_primeiro_nome_ou_pelo_titulo_e_nome() {
        fun pessoa(nome: String) = Pessoa("id", nome, "f", Pessoa.PAPEL_OPERADOR, null, "M", null, null)
        assertEquals("Rui", pessoa("Rui Almeida").nomeCurto())
        assertEquals("Eng.ª Marta", pessoa("Eng.ª Marta Ferreira").nomeCurto())
        assertEquals("Eng. Paulo", pessoa("Eng. Paulo Sequeira").nomeCurto())
        assertEquals("Ana", pessoa("Ana").nomeCurto())
    }

    private fun autorizacao(tipo: String, estado: String = Autorizacao.EM_CURSO, terminado: Long? = null) = Autorizacao(
        secaoId = "S", avariaId = 1, tipo = tipo, descricao = "d", estado = estado,
        pedidaPorId = "p", dataPedido = 0, dataTrabalhoTerminado = terminado
    )

    @Test
    fun os_pins_errados_ficam_registados_durante_24_horas() {
        var agora = 1_000_000_000L
        val estado = BloqueioPin.EmMemoria()
        val bloqueio = BloqueioPin(estado) { agora }
        bloqueio.erro()
        agora += 60_000
        bloqueio.erro()
        val umDia = 24 * 60 * 60 * 1000L
        assertEquals(2, estado.errosDesde(agora - umDia).size)
        agora += umDia + 1 // passou mais de um dia: os erros antigos já não contam
        assertEquals(0, estado.errosDesde(agora - umDia).size)
    }
}
