package pt.ubi.di.pdm.finsafix.ui.consulta

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import pt.ubi.di.pdm.finsafix.R

/** Gráfico de barras simples (uma barra por dia) desenhado à mão, sem biblioteca: “Avarias por dia”. */
class GraficoBarras @JvmOverloads constructor(context: Context, atributos: AttributeSet? = null) : View(context, atributos) {

    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    private var valores = IntArray(0)

    fun definir(novosValores: IntArray) {
        valores = novosValores
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (valores.isEmpty()) return
        val maximo = maxOf(valores.max(), 1)
        val largura = width.toFloat() / valores.size
        val margem = largura * 0.15f
        // Linha de base e barras: o dia de hoje (a última) destaca-se
        pincel.color = ContextCompat.getColor(context, R.color.if_on_surface_variant)
        canvas.drawRect(0f, height - 2f, width.toFloat(), height.toFloat(), pincel)
        for ((i, valor) in valores.withIndex()) {
            if (valor == 0) continue
            pincel.color = ContextCompat.getColor(context, if (i == valores.lastIndex) R.color.if_primary else R.color.if_on_surface_variant)
            val topo = (height - 2f) * (1 - valor.toFloat() / maximo)
            canvas.drawRect(i * largura + margem, topo, (i + 1) * largura - margem, height - 2f, pincel)
        }
    }
}
