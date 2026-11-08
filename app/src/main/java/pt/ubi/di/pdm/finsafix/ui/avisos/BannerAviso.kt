package pt.ubi.di.pdm.finsafix.ui.avisos

import androidx.core.widget.TextViewCompat
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Aviso
import pt.ubi.di.pdm.finsafix.data.AvisoResumo
import pt.ubi.di.pdm.finsafix.databinding.BannerAvisoBinding
import pt.ubi.di.pdm.finsafix.util.Datas

/** Mostra (ou esconde, se [aviso] for nulo) o banner: "Preciso da máquina parada" a vermelho, "Pode arrancar" a verde. */
fun BannerAvisoBinding.mostrar(aviso: AvisoResumo?, aoResponder: (AvisoResumo) -> Unit) {
    root.isVisible = aviso != null
    if (aviso == null) return
    val context = root.context
    val parar = aviso.aviso.tipo == Aviso.PARAR
    val cor = ContextCompat.getColor(context, if (parar) R.color.if_on_error else R.color.if_on_ok_container)

    root.setCardBackgroundColor(ContextCompat.getColor(context, if (parar) R.color.if_error else R.color.if_ok_container))
    titulo.setText(if (parar) R.string.aviso_parar else R.string.aviso_arrancar)
    titulo.setTextColor(cor)
    titulo.setCompoundDrawablesRelativeWithIntrinsicBounds(if (parar) R.drawable.ic_stop_x else R.drawable.ic_play, 0, 0, 0)
    TextViewCompat.setCompoundDrawableTintList(titulo, ColorStateList.valueOf(cor))
    detalhe.text = context.getString(
        R.string.aviso_detalhe, aviso.equipamentoNome ?: "", aviso.criadoPorNome, Datas.hora(aviso.aviso.dataCriacao)
    )
    detalhe.setTextColor(cor)
    botao.setText(if (parar) R.string.botao_maquina_parada else R.string.botao_percebido)
    botao.setTextColor(cor)
    botao.strokeColor = ColorStateList.valueOf(cor)
    botao.setOnClickListener { aoResponder(aviso) }
}
