package pt.ubi.di.pdm.finsafix.ui.inicio

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.databinding.ItemEquipamentoBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.util.Datas

/** Cartão "Os meus equipamentos": nome e estado (ícone + texto + cor; só "Parada" tem fundo). */
class EquipamentoAdapter : ListAdapter<EquipamentoComEstado, EquipamentoAdapter.ViewHolder>(Diferencas) {

    class ViewHolder(val binding: ItemEquipamentoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEquipamentoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            textoNome.text = item.equipamento.nome
            val estilo = when (item.estado) {
                EstadoEquipamento.OPERACIONAL ->
                    EstiloAvaria.Estilo(R.string.equipamento_operacional, R.drawable.ic_check_circle, R.color.if_ok_text)
                EstadoEquipamento.AVARIA_MENOR ->
                    EstiloAvaria.Estilo(R.string.equipamento_avaria_menor, R.drawable.ic_alert, R.color.if_warn_text)
                EstadoEquipamento.PARADA ->
                    EstiloAvaria.Estilo(R.string.equipamento_parada, R.drawable.ic_stop_x, R.color.if_on_error_container)
            }
            EstiloAvaria.aplicar(textoEstado, estilo)
            // “Parada · 14 min”: a duração calcula-se ao desenhar a linha
            item.paradaDesde?.let {
                textoEstado.text = root.context.getString(
                    R.string.equipamento_parada_tempo, Datas.duracao(root.context, System.currentTimeMillis() - it)
                )
            }
            if (item.estado == EstadoEquipamento.PARADA) {
                textoEstado.setBackgroundResource(R.drawable.fundo_parada)
            } else {
                textoEstado.background = null
            }
        }
    }

    private object Diferencas : DiffUtil.ItemCallback<EquipamentoComEstado>() {
        override fun areItemsTheSame(antigo: EquipamentoComEstado, novo: EquipamentoComEstado) =
            antigo.equipamento.id == novo.equipamento.id
        override fun areContentsTheSame(antigo: EquipamentoComEstado, novo: EquipamentoComEstado) = antigo == novo
    }
}
