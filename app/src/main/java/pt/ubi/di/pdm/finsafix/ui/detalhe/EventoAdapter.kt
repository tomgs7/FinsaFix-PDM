package pt.ubi.di.pdm.finsafix.ui.detalhe

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.ubi.di.pdm.finsafix.data.EventoAvaria
import pt.ubi.di.pdm.finsafix.databinding.ItemEventoBinding
import pt.ubi.di.pdm.finsafix.util.Datas

/** Uma linha da cronologia: o que aconteceu e quando. */
class EventoAdapter : ListAdapter<EventoAvaria, EventoAdapter.ViewHolder>(Diferencas) {

    class ViewHolder(val binding: ItemEventoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemEventoBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val evento = getItem(position)
        holder.binding.textoEvento.text = evento.texto
        holder.binding.textoData.text = Datas.dataHora(evento.data)
    }

    private object Diferencas : DiffUtil.ItemCallback<EventoAvaria>() {
        override fun areItemsTheSame(antigo: EventoAvaria, novo: EventoAvaria) = antigo.id == novo.id
        override fun areContentsTheSame(antigo: EventoAvaria, novo: EventoAvaria) = antigo == novo
    }
}
