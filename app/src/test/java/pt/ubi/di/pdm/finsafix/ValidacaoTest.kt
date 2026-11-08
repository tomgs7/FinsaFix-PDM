package pt.ubi.di.pdm.finsafix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Parametro
import pt.ubi.di.pdm.finsafix.util.Validacao

/** Testes das regras sem Android (correm no computador, sem emulador). */
class ValidacaoTest {

    @Test
    fun pin_tem_4_digitos() {
        assertTrue(Validacao.pinTemFormatoValido("9999"))
        assertFalse(Validacao.pinTemFormatoValido("999"))
        assertFalse(Validacao.pinTemFormatoValido("12a4"))
    }

    @Test
    fun descricao_ate_300_caracteres() {
        assertTrue(Validacao.descricaoValida(""))
        assertTrue(Validacao.descricaoValida("a".repeat(300)))
        assertFalse(Validacao.descricaoValida("a".repeat(301)))
    }

    @Test
    fun nota_tem_pelo_menos_5_caracteres_sem_espacos_nas_pontas() {
        assertTrue(Validacao.notaValida("Falta a peça"))
        assertFalse(Validacao.notaValida("  abc  "))
    }

    @Test
    fun quantidade_de_producao_tem_de_ser_positiva() {
        assertTrue(Validacao.quantidadeValida(46.0))
        assertFalse(Validacao.quantidadeValida(0.0))
        assertFalse(Validacao.quantidadeValida(2_000_000.0))
    }

    @Test
    fun duracao_nao_conta_o_tempo_parado() {
        val hora = 3_600_000L
        val avaria = avaria(dataInicio = 0, dataFim = hora, tempoParadoMs = hora / 2)
        assertEquals(hora / 2, avaria.duracaoMs(agora = 10 * hora))
    }

    @Test
    fun duracao_de_avaria_deixada_em_progresso_para_quando_foi_deixada() {
        val hora = 3_600_000L
        val avaria = avaria(dataInicio = 0, progressoData = hora)
        assertEquals(hora, avaria.duracaoMs(agora = 5 * hora))
    }

    @Test
    fun sem_inicio_nao_ha_duracao() {
        assertNull(avaria().duracaoMs(agora = 1000))
    }

    @Test
    fun estado_de_um_parametro_com_limite_maximo() {
        val desperdicio = Parametro("S-desp", "S", "Desperdício", "%", 0.0, 4.0, 1, Parametro.LIMITE_MAXIMO)
        assertEquals(Parametro.Estado.DENTRO, desperdicio.estado(3.0))
        assertEquals(Parametro.Estado.PERTO, desperdicio.estado(3.8))
        assertEquals(Parametro.Estado.FORA, desperdicio.estado(4.1))
        assertTrue(desperdicio.improvavel(40.0))
        assertFalse(desperdicio.improvavel(5.0))
    }

    @Test
    fun estado_de_um_parametro_com_intervalo() {
        val pressao = Parametro("S-ar", "S", "Pressão", "bar", 5.5, 7.0, 1, Parametro.LIMITE_INTERVALO)
        assertEquals(Parametro.Estado.FORA, pressao.estado(5.0))
        assertEquals(Parametro.Estado.PERTO, pressao.estado(5.6))
        assertEquals(Parametro.Estado.DENTRO, pressao.estado(6.4))
    }

    private fun avaria(
        dataInicio: Long? = null,
        dataFim: Long? = null,
        tempoParadoMs: Long = 0,
        progressoData: Long? = null
    ) = Avaria(
        codigo = "AV-2026-0001", equipamentoId = "E", titulo = "t", descricao = "", prioridade = Avaria.MEDIA,
        especialidade = Avaria.MECANICA, estado = Avaria.EM_CURSO, reportadaPorId = "p", dataCriacao = 0,
        dataInicio = dataInicio, dataFim = dataFim, tempoParadoMs = tempoParadoMs, progressoData = progressoData
    )

    @Test
    fun o_csv_poe_aspas_so_onde_e_preciso() {
        val csv = pt.ubi.di.pdm.finsafix.util.Relatorios::celulaCsv
        assertEquals("a", csv("a"))
        assertEquals("\"x;y\"", csv("x;y"))
        assertEquals("\"dizem \"\"olá\"\"\"", csv("dizem \"olá\""))
    }
}
