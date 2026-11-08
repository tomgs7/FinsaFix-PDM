package pt.ubi.di.pdm.finsafix.util

import java.text.NumberFormat
import java.util.Locale

/** Números como em Portugal: vírgula decimal (3,8) e o número de casas decimais pedido. */
object Numeros {

    private val PORTUGUES = Locale.forLanguageTag("pt-PT")

    fun formatar(valor: Double, decimais: Int): String =
        NumberFormat.getNumberInstance(PORTUGUES).apply {
            minimumFractionDigits = decimais
            maximumFractionDigits = decimais
        }.format(valor)

    fun formatar(valor: Int): String = formatar(valor.toDouble(), 0)

    /** "3,8" → 3.8; texto vazio ou inválido → null. */
    fun ler(texto: String): Double? = texto.replace(',', '.').toDoubleOrNull()
}
