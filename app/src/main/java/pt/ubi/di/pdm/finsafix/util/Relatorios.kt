package pt.ubi.di.pdm.finsafix.util

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/** Uma linha de qualquer tabela do relatório (avaria, paragem ou preventiva): as mesmas cinco colunas. */
data class LinhaRelatorio(val data: String, val id: String, val equipamento: String, val detalhe: String, val duracao: String) {
    fun celulas() = listOf(data, id, equipamento, detalhe, duracao)
}

/** Uma tabela do relatório. [tipo] vai na primeira coluna do CSV (“Avaria”); [titulo] é o título da secção (“Avarias (3)”). */
data class TabelaRelatorio(val tipo: String, val titulo: String, val linhas: List<LinhaRelatorio>)

/** Tudo o que se mostra e se exporta, já em texto (os textos vêm de strings.xml, montados no ViewModel). */
data class Relatorio(
    val titulo: String,
    val secao: String,
    val periodo: String,
    val rodape: String,
    val textoPagina: String,
    val colunaTipo: String,
    val colunas: List<String>,
    val tituloIndicadores: String,
    val numeros: List<Pair<String, String>>,
    val tabelas: List<TabelaRelatorio>,
    val temDados: Boolean
)

/** Escrita dos relatórios: CSV (texto) e PDF (a classe PdfDocument do Android, sem bibliotecas). */
object Relatorios {

    /** O BOM no início faz o Excel em português abrir o UTF-8 com os acentos certos. */
    private const val BOM = "\uFEFF"

    /** Uma célula entre aspas se tiver ponto e vírgula, aspas ou mudança de linha (as aspas duplicam-se). */
    fun celulaCsv(valor: String): String =
        if (valor.any { it == ';' || it == '"' || it == '\n' }) "\"" + valor.replace("\"", "\"\"") + "\"" else valor

    /** Cabeçalho, uma linha por indicador e uma por registo; células separadas por ponto e vírgula. */
    fun paraCsv(r: Relatorio): String {
        val linhas = mutableListOf(listOf(r.colunaTipo) + r.colunas)
        r.numeros.forEach { linhas += listOf(r.tituloIndicadores, "", "", "", it.first, it.second) }
        r.tabelas.forEach { tabela -> tabela.linhas.forEach { linhas += listOf(tabela.tipo) + it.celulas() } }
        return BOM + linhas.joinToString("\n") { linha -> linha.joinToString(";") { celulaCsv(it) } }
    }

    // ---------- PDF (A4 em pontos: 595 × 842) ----------

    private const val LARGURA = 595
    private const val ALTURA = 842
    private const val MARGEM = 40f
    private const val LARGURA_UTIL = LARGURA - 2 * MARGEM
    private const val ALTURA_LINHA = 16f
    // Cores como números (e não Color.parseColor) para o objeto poder ser usado nos testes unitários
    private val PRETO = 0xFF000000.toInt()
    private val VERMELHO = 0xFFC8102E.toInt()
    private val CINZA_CLARO = 0xFFF2F2F2.toInt()
    private val CINZA_TEXTO = 0xFF555555.toInt()

    /** Largura de cada coluna da tabela, em fração da largura útil (Data, ID, Equipamento, Detalhe, Duração). */
    private val FRACOES = floatArrayOf(0.15f, 0.14f, 0.25f, 0.31f, 0.15f)

    private fun tinta(tamanho: Float, negrito: Boolean = false, cor: Int = PRETO) = Paint().apply {
        isAntiAlias = true
        textSize = tamanho
        if (negrito) typeface = Typeface.DEFAULT_BOLD
        color = cor
    }

    /** Corta o texto para caber na largura, com reticências (o texto inteiro está no CSV). */
    private fun caber(tinta: Paint, texto: String, largura: Float): String {
        if (tinta.measureText(texto) <= largura) return texto
        val cabem = tinta.breakText(texto, true, largura - tinta.measureText("…"), null)
        return texto.take(cabem) + "…"
    }

    /** As folhas do PDF: quando já não cabe mais uma linha abre a seguinte; cada folha acaba com o número da página. */
    private class Folhas(private val pdf: PdfDocument, private val textoPagina: String) {
        private var numero = 0
        private var folha: PdfDocument.Page? = null
        var y = MARGEM
        val canvas get() = folha!!.canvas

        fun nova() {
            fechar()
            numero++
            folha = pdf.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, numero).create())
            y = MARGEM
        }

        fun garantir(altura: Float) {
            if (folha == null || y + altura > ALTURA - 2 * MARGEM) nova()
        }

        fun fechar() {
            val atual = folha ?: return
            val rodape = tinta(9f, cor = CINZA_TEXTO)
            val texto = String.format(textoPagina, numero)
            atual.canvas.drawText(texto, (LARGURA - rodape.measureText(texto)) / 2, ALTURA - MARGEM, rodape)
            pdf.finishPage(atual)
            folha = null
        }
    }

    /** Cabeçalho (logótipo em texto, título, secção, período), indicadores, tabelas com linhas alternadas e rodapé. */
    fun escreverPdf(logotipo: String, r: Relatorio, destino: OutputStream) {
        val pdf = PdfDocument()
        try {
            val folhas = Folhas(pdf, r.textoPagina)
            folhas.nova()
            desenharCabecalho(folhas, logotipo, r)
            if (r.numeros.isNotEmpty()) desenharIndicadores(folhas, r)
            r.tabelas.forEach { desenharTabela(folhas, r.colunas, it) }
            desenharRodape(folhas, r.rodape)
            folhas.fechar()
            pdf.writeTo(destino)
        } finally {
            pdf.close()
        }
    }

    private fun desenharCabecalho(folhas: Folhas, logotipo: String, r: Relatorio) {
        val direita = tinta(10f, cor = CINZA_TEXTO).apply { textAlign = Paint.Align.RIGHT }
        val titulo = tinta(14f, negrito = true).apply { textAlign = Paint.Align.RIGHT }
        val fimX = LARGURA - MARGEM
        folhas.canvas.drawText(logotipo, MARGEM, folhas.y + 18f, tinta(24f, negrito = true, cor = VERMELHO))
        folhas.canvas.drawText(r.titulo, fimX, folhas.y + 12f, titulo)
        folhas.canvas.drawText("${r.secao} · ${r.periodo}", fimX, folhas.y + 28f, direita)
        folhas.y += 40f
        folhas.canvas.drawLine(MARGEM, folhas.y, fimX, folhas.y, tinta(1f).apply { strokeWidth = 1.5f })
        folhas.y += 18f
    }

    private fun desenharIndicadores(folhas: Folhas, r: Relatorio) {
        folhas.garantir(60f)
        folhas.canvas.drawText(r.tituloIndicadores, MARGEM, folhas.y + 10f, tinta(12f, negrito = true))
        folhas.y += 18f
        val caixa = LARGURA_UTIL / r.numeros.size
        val contorno = tinta(1f).apply { style = Paint.Style.STROKE }
        r.numeros.forEachIndexed { i, (nome, valor) ->
            val x = MARGEM + i * caixa
            folhas.canvas.drawRect(x, folhas.y, x + caixa, folhas.y + 34f, contorno)
            folhas.canvas.drawText(caber(tinta(8f, cor = CINZA_TEXTO), nome, caixa - 8f), x + 5f, folhas.y + 12f, tinta(8f, cor = CINZA_TEXTO))
            folhas.canvas.drawText(caber(tinta(13f, negrito = true), valor, caixa - 8f), x + 5f, folhas.y + 28f, tinta(13f, negrito = true))
        }
        folhas.y += 52f
    }

    private fun desenharTabela(folhas: Folhas, colunas: List<String>, tabela: TabelaRelatorio) {
        folhas.garantir(3 * ALTURA_LINHA + 20f)
        folhas.canvas.drawText(tabela.titulo, MARGEM, folhas.y + 10f, tinta(12f, negrito = true))
        folhas.y += 20f
        linhaDeTabela(folhas, colunas, tinta(9f, negrito = true), fundo = null)
        folhas.canvas.drawLine(MARGEM, folhas.y, LARGURA - MARGEM, folhas.y, tinta(1f))
        tabela.linhas.forEachIndexed { i, linha ->
            folhas.garantir(ALTURA_LINHA)
            linhaDeTabela(folhas, linha.celulas(), tinta(9f), fundo = if (i % 2 == 1) CINZA_CLARO else null)
        }
        folhas.y += 14f
    }

    private fun linhaDeTabela(folhas: Folhas, celulas: List<String>, tinta: Paint, fundo: Int?) {
        if (fundo != null) {
            folhas.canvas.drawRect(MARGEM, folhas.y, LARGURA - MARGEM, folhas.y + ALTURA_LINHA, Paint().apply { color = fundo })
        }
        var x = MARGEM
        celulas.forEachIndexed { i, texto ->
            val largura = LARGURA_UTIL * FRACOES[i]
            folhas.canvas.drawText(caber(tinta, texto, largura - 6f), x + 3f, folhas.y + 11f, tinta)
            x += largura
        }
        folhas.y += ALTURA_LINHA
    }

    private fun desenharRodape(folhas: Folhas, texto: String) {
        folhas.garantir(24f)
        folhas.canvas.drawLine(MARGEM, folhas.y, LARGURA - MARGEM, folhas.y, tinta(0.5f))
        folhas.canvas.drawText(texto, MARGEM, folhas.y + 14f, tinta(9f, cor = CINZA_TEXTO))
    }
}
