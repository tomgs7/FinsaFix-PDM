package pt.ubi.di.pdm.finsafix.ui.parametros

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Parametro

/**
 * Barra colorida dos limites de um parâmetro: verde dentro do limite, amarelo perto e vermelho fora,
 * com um traço onde está o último valor. A escala vai de metade da amplitude abaixo do mínimo
 * (ou de zero, se só há máximo) até metade da amplitude acima do máximo.
 */
class BarraLimites @JvmOverloads constructor(context: Context, atributos: AttributeSet? = null) : View(context, atributos) {

    /** Uma faixa de cor da escala, de [de] até [ate] (nos valores do parâmetro). */
    private class Zona(val de: Double, val ate: Double, val cor: Int)

    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    private var valor: Double? = null
    private var zonas = emptyList<Zona>()
    private var inicio = 0.0
    private var fim = 1.0

    fun definir(parametro: Parametro, valor: Double?) {
        this.valor = valor
        calcularZonas(parametro)
        invalidate()
    }

    /** Calcula a escala e as zonas, da esquerda para a direita (o intervalo tem zona vermelha e amarela também à esquerda). */
    private fun calcularZonas(p: Parametro) {
        val amplitude = p.maximo - p.minimo
        val margem = amplitude * 0.1
        val intervalo = p.tipoLimite == Parametro.LIMITE_INTERVALO
        inicio = if (intervalo) p.minimo - amplitude / 2 else 0.0
        fim = p.maximo + amplitude / 2
        fun zona(de: Double, ate: Double, cor: Int) = Zona(de, ate, ContextCompat.getColor(context, cor))
        zonas = if (intervalo) {
            listOf(
                zona(inicio, p.minimo, R.color.if_error_text), zona(p.minimo, p.minimo + margem, R.color.if_warn_text),
                zona(p.minimo + margem, p.maximo - margem, R.color.if_ok_text), zona(p.maximo - margem, p.maximo, R.color.if_warn_text),
                zona(p.maximo, fim, R.color.if_error_text)
            )
        } else {
            listOf(
                zona(inicio, p.maximo - margem, R.color.if_ok_text), zona(p.maximo - margem, p.maximo, R.color.if_warn_text),
                zona(p.maximo, fim, R.color.if_error_text)
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        fun x(v: Double) = (((v - inicio) / (fim - inicio)).coerceIn(0.0, 1.0) * width).toFloat()
        val altura = height / 2f
        val topo = (height - altura) / 2
        for (zona in zonas) {
            pincel.color = zona.cor
            canvas.drawRect(x(zona.de), topo, x(zona.ate), topo + altura, pincel)
        }
        valor?.let {
            pincel.color = ContextCompat.getColor(context, R.color.if_on_surface)
            canvas.drawRect(x(it) - 3f, 0f, x(it) + 3f, height.toFloat(), pincel)
        }
    }
}
