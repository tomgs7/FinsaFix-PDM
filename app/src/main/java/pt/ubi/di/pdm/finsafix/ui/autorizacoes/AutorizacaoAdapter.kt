package pt.ubi.di.pdm.finsafix.ui.autorizacoes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.ubi.di.pdm.finsafix.data.AutorizacaoResumo
import pt.ubi.di.pdm.finsafix.databinding.ItemAutorizacaoBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.util.Datas

/** Uma linha do ecrã Validar: tipo e equipamento, o pedido e o estado. */
class AutorizacaoAdapter(private val aoTocar: (AutorizacaoResumo) -> Unit) :
    ListAdapter<AutorizacaoResumo, AutorizacaoAdapter.ViewHolder>(Diferencas) {

    class ViewHolder(val binding: ItemAutorizacaoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemAutorizacaoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val resumo = getItem(position)
        val aut = resumo.autorizacao
        val context = holder.itemView.context
        with(holder.binding) {
            textoTitulo.text = listOf(EstiloAutorizacao.nomes(context, aut.tipo), resumo.equipamentoNome)
                .joinToString(EstiloAvaria.SEPARADOR)
            textoTitulo.setCompoundDrawablesRelativeWithIntrinsicBounds(EstiloAutorizacao.icone(aut.tipos().first()), 0, 0, 0)
            textoPedido.text = listOf(resumo.avariaCodigo, aut.descricao, resumo.pedidaPorNome, Datas.hora(aut.dataPedido))
                .joinToString(EstiloAvaria.SEPARADOR)
            EstiloAutorizacao.mostrarEstado(textoEstado, aut)
            root.setOnClickListener { aoTocar(resumo) }
        }
    }

    private object Diferencas : DiffUtil.ItemCallback<AutorizacaoResumo>() {
        override fun areItemsTheSame(antigo: AutorizacaoResumo, novo: AutorizacaoResumo) = antigo.autorizacao.id == novo.autorizacao.id
        override fun areContentsTheSame(antigo: AutorizacaoResumo, novo: AutorizacaoResumo) = antigo == novo
    }
}
