package pt.ubi.di.pdm.finsafix.ui.comum

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.databinding.ItemPorTratarBinding

/** Uma linha de “Por tratar”: o texto já escrito, se é urgente (vermelho) e o que abre ao tocar. */
class LinhaPorTratar(val id: String, val texto: String, val urgente: Boolean = false, val aoTocar: () -> Unit)

/** As linhas de “Por tratar” do chefe de secção e do chefe de oficina: ícone + texto + seta (sempre ícone, texto e cor). */
class PorTratarAdapter : ListAdapter<LinhaPorTratar, PorTratarAdapter.ViewHolder>(Diferencas) {

    class ViewHolder(val binding: ItemPorTratarBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemPorTratarBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val linha = getItem(position)
        val contexto = holder.itemView.context
        with(holder.binding) {
            texto.text = linha.texto
            icone.setImageResource(if (linha.urgente) R.drawable.ic_stop_x else R.drawable.ic_alert)
            icone.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(contexto, if (linha.urgente) R.color.if_error_text else R.color.if_warn_text))
            root.setOnClickListener { linha.aoTocar() }
        }
    }

    private object Diferencas : DiffUtil.ItemCallback<LinhaPorTratar>() {
        override fun areItemsTheSame(antiga: LinhaPorTratar, nova: LinhaPorTratar) = antiga.id == nova.id
        override fun areContentsTheSame(antiga: LinhaPorTratar, nova: LinhaPorTratar) = antiga.texto == nova.texto && antiga.urgente == nova.urgente
    }
}
