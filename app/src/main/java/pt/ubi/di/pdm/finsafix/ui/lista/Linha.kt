package pt.ubi.di.pdm.finsafix.ui.lista

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.databinding.ItemLinhaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria

/** Um botão no fim de uma linha (por exemplo “Já fui atendido” ou “+1”). */
data class Acao(val rotulo: String, val aoTocar: () -> Unit)

/**
 * Uma linha genérica das listas novas (pedidos, instruções, preventiva, stock, rondas, notas):
 * estado com ícone e cor, título, duas linhas de texto e, se for preciso, botões.
 * [textoEstado] substitui o texto do [estado] quando este tem dados (“Fechado por Rui Costa”).
 */
data class Linha(
    val id: String,
    val estado: EstiloAvaria.Estilo? = null,
    val textoEstado: String? = null,
    val titulo: String,
    val sub: String? = null,
    val extra: String? = null,
    val acoes: List<Acao> = emptyList(),
    val aoTocar: (() -> Unit)? = null
)

class LinhaAdapter : ListAdapter<Linha, LinhaAdapter.ViewHolder>(Diferencas) {

    class ViewHolder(val binding: ItemLinhaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemLinhaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val linha = getItem(position)
        with(holder.binding) {
            textoEstado.isVisible = linha.estado != null
            linha.estado?.let {
                EstiloAvaria.aplicar(textoEstado, it)
                linha.textoEstado?.let { texto -> textoEstado.text = texto }
            }
            textoTitulo.text = linha.titulo
            textoSub.text = linha.sub
            textoSub.isVisible = !linha.sub.isNullOrBlank()
            textoExtra.text = linha.extra
            textoExtra.isVisible = !linha.extra.isNullOrBlank()
            acoes.removeAllViews()
            acoes.isVisible = linha.acoes.isNotEmpty()
            linha.acoes.forEach { acao ->
                val chip = Chip(root.context, null, com.google.android.material.R.attr.chipStyle).apply {
                    text = acao.rotulo
                    isCheckable = false
                    minimumHeight = resources.getDimensionPixelSize(R.dimen.if_touch_min)
                    setOnClickListener { acao.aoTocar() }
                }
                acoes.addView(chip)
            }
            root.isClickable = linha.aoTocar != null
            iconeSeta.isVisible = linha.aoTocar != null
            root.setOnClickListener { linha.aoTocar?.invoke() }
        }
    }

    private object Diferencas : DiffUtil.ItemCallback<Linha>() {
        override fun areItemsTheSame(antiga: Linha, nova: Linha) = antiga.id == nova.id
        override fun areContentsTheSame(antiga: Linha, nova: Linha) = antiga == nova
    }
}
