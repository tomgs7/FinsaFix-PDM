package pt.ubi.di.pdm.finsafix.ui.comum

import androidx.core.widget.TextViewCompat
import android.content.res.ColorStateList
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Parametro

/**
 * Como se mostram os estados, as prioridades e as especialidades.
 * Regra do sistema de design: o estado vê-se sempre com ícone + texto + cor; só o crítico tem fundo vermelho.
 */
object EstiloAvaria {

    /** “Carlos Mendes +1”: o responsável e, havendo ajudantes, quantos são. */
    fun tecnicoComEquipa(context: android.content.Context, nome: String, ajudantes: Int): String =
        if (ajudantes > 0) context.getString(R.string.tecnico_com_equipa, nome, ajudantes) else nome

    /** Separador dos textos curtos numa linha ("Equipamento · Pessoa · 10:30"). */
    const val SEPARADOR = " · "

    data class Estilo(@param:StringRes val texto: Int, @param:DrawableRes val icone: Int, @param:ColorRes val cor: Int)

    fun estado(estado: String): Estilo = when (estado) {
        Avaria.EM_CURSO -> Estilo(R.string.estado_em_curso, R.drawable.ic_construction, R.color.if_warn_text)
        Avaria.RESOLVIDA -> Estilo(R.string.estado_resolvida, R.drawable.ic_check_circle, R.color.if_ok_text)
        else -> Estilo(R.string.estado_pendente, R.drawable.ic_clock, R.color.if_on_surface_variant)
    }

    /** Estado de uma leitura de parâmetro: dentro (verde), perto (amarelo) ou fora (vermelho) do limite. */
    fun estadoParametro(estado: Parametro.Estado): Estilo = when (estado) {
        Parametro.Estado.DENTRO -> Estilo(R.string.estado_dentro, R.drawable.ic_check_circle, R.color.if_ok_text)
        Parametro.Estado.PERTO -> Estilo(R.string.estado_perto, R.drawable.ic_alert, R.color.if_warn_text)
        Parametro.Estado.FORA -> Estilo(R.string.estado_fora, R.drawable.ic_stop_x, R.color.if_error_text)
    }

    @StringRes
    fun prioridade(prioridade: String): Int = when (prioridade) {
        Avaria.CRITICA -> R.string.prioridade_critica
        Avaria.MEDIA -> R.string.prioridade_media
        else -> R.string.prioridade_baixa
    }

    @StringRes
    fun especialidade(especialidade: String): Int = when (especialidade) {
        Avaria.MECANICA -> R.string.especialidade_mecanica
        Avaria.ELETRICA -> R.string.especialidade_eletrica
        else -> R.string.especialidade_nao_sei
    }

    /** Escreve o texto com o ícone à esquerda, ambos na cor do estilo. */
    fun aplicar(textView: TextView, estilo: Estilo) {
        val cor = ContextCompat.getColor(textView.context, estilo.cor)
        textView.setText(estilo.texto)
        textView.setTextColor(cor)
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(estilo.icone, 0, 0, 0)
        TextViewCompat.setCompoundDrawableTintList(textView, ColorStateList.valueOf(cor))
    }

    @DrawableRes
    fun iconeEspecialidade(especialidade: String): Int = when (especialidade) {
        Avaria.MECANICA -> R.drawable.ic_wrench
        Avaria.ELETRICA -> R.drawable.ic_bolt
        else -> R.drawable.ic_help
    }

    /** Etiqueta de prioridade: a crítica tem ícone e fundo vermelho claro, as outras são só texto. */
    fun aplicarPrioridade(textView: TextView, prioridade: String) {
        val context = textView.context
        textView.setText(prioridade(prioridade))
        if (prioridade == Avaria.CRITICA) {
            val cor = ContextCompat.getColor(context, R.color.if_on_error_container)
            textView.setBackgroundResource(R.drawable.fundo_critico_claro)
            textView.setTextColor(cor)
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_stop_x, 0, 0, 0)
            textView.compoundDrawablePadding = (4 * context.resources.displayMetrics.density).toInt()
            TextViewCompat.setCompoundDrawableTintList(textView, ColorStateList.valueOf(cor))
        } else {
            textView.background = null
            textView.setTextColor(ContextCompat.getColor(context, R.color.if_on_surface_variant))
            textView.setCompoundDrawablesRelative(null, null, null, null)
        }
    }
}
