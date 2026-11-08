package pt.ubi.di.pdm.finsafix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ubi.di.pdm.finsafix.util.LinhaRelatorio
import pt.ubi.di.pdm.finsafix.util.Relatorio
import pt.ubi.di.pdm.finsafix.util.Relatorios
import pt.ubi.di.pdm.finsafix.util.TabelaRelatorio

class RelatoriosCsvTest {

    private fun relatorio(linhas: List<LinhaRelatorio>, numeros: List<Pair<String, String>> = emptyList()) = Relatorio(
        titulo = "Relatório de manutenção", secao = "Toda a fábrica", periodo = "01/11/2026 a 08/11/2026", rodape = "", textoPagina = "Página %d",
        colunaTipo = "Tipo", colunas = listOf("Data", "ID", "Equipamento", "Detalhe", "Duração"),
        tituloIndicadores = "Indicadores", numeros = numeros,
        tabelas = listOf(TabelaRelatorio("Avaria", "Avarias (${linhas.size})", linhas)), temDados = linhas.isNotEmpty()
    )

    @Test
    fun comecaComBomUtf8EComOCabecalho() {
        val csv = Relatorios.paraCsv(relatorio(emptyList()))
        assertTrue(csv.startsWith("\uFEFF"))
        assertEquals("Tipo;Data;ID;Equipamento;Detalhe;Duração", csv.removePrefix("\uFEFF").lines().first())
        // O BOM em UTF-8 são os bytes EF BB BF
        assertEquals(listOf(0xEF, 0xBB, 0xBF), csv.toByteArray(Charsets.UTF_8).take(3).map { it.toInt() and 0xFF })
    }

    @Test
    fun separaAsCelulasComPontoEVirgulaEMantemOsAcentos() {
        val linha = LinhaRelatorio("07/11/2026", "AV-2026-0418", "Serra Schelling", "Correia partida", "1 h 11 min")
        val csv = Relatorios.paraCsv(relatorio(listOf(linha))).removePrefix("\uFEFF").lines()
        assertEquals("Avaria;07/11/2026;AV-2026-0418;Serra Schelling;Correia partida;1 h 11 min", csv[1])
        assertTrue(Relatorios.paraCsv(relatorio(listOf(linha.copy(detalhe = "Avaria elétrica na ligação")))).contains("Avaria elétrica na ligação"))
    }

    @Test
    fun poeAspasQuandoOTextoTemPontoEVirgulaOuAspas() {
        val linha = LinhaRelatorio("07/11/2026", "", "Serra", "Rolamento; \"fixe\"", "")
        val csv = Relatorios.paraCsv(relatorio(listOf(linha))).removePrefix("\uFEFF").lines()
        assertEquals("Avaria;07/11/2026;;Serra;\"Rolamento; \"\"fixe\"\"\";", csv[1])
    }

    @Test
    fun osIndicadoresVaoEmLinhasProprias() {
        val csv = Relatorios.paraCsv(relatorio(emptyList(), listOf("Avarias" to "3", "MTBF" to "2 h"))).removePrefix("\uFEFF").lines()
        assertEquals("Indicadores;;;;Avarias;3", csv[1])
        assertEquals("Indicadores;;;;MTBF;2 h", csv[2])
    }
}
