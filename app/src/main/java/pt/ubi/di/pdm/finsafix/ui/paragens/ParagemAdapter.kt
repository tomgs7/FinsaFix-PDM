package pt.ubi.di.pdm.finsafix.ui.paragens

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.ParagemResumo
import pt.ubi.di.pdm.finsafix.databinding.ItemParagemBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.util.Numeros
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.ProducaoPerdida

/** Uma paragem: equipamento, causa, quando e quanto tempo; as ativas têm o botão "Retomar produção". */
class ParagemAdapter(
    private val aoRetomar: (ParagemResumo) -> Unit,
    private val aoAbrirAvaria: (ParagemResumo) -> Unit = {}
) : ListAdapter<ParagemResumo, ParagemAdapter.ViewHolder>(Diferencas) {

    class ViewHolder(val binding: ItemParagemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemParagemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val resumo = getItem(position)
        val paragem = resumo.paragem
        val context = holder.itemView.context
        val ativa = paragem.fim == null
        with(holder.binding) {
            EstiloAvaria.aplicar(
                textoEstado,
                if (ativa) EstiloAvaria.Estilo(R.string.paragem_ativa, R.drawable.ic_stop_x, R.color.if_error_text)
                else EstiloAvaria.Estilo(R.string.paragem_terminada, R.drawable.ic_check_circle, R.color.if_ok_text)
            )
            textoEquipamento.text = context.getString(R.string.detalhe_local, resumo.equipamentoNome, resumo.secaoNome)
            textoCausa.text = resumo.avariaCodigo?.let { context.getString(R.string.paragem_causa_avaria, paragem.causa, it) }
                ?: paragem.causa
            val fim = paragem.fim ?: System.currentTimeMillis()
            textoTempo.text = if (ativa) {
                // “Desde as 14:53 · há 15 min · produção perdida ≈ 2,4 unid.”
                context.getString(R.string.paragem_desde, Datas.hora(paragem.inicio), Datas.duracao(context, fim - paragem.inicio)) +
                    context.getString(R.string.paragem_perdida, Numeros.formatar(ProducaoPerdida.estimar(resumo.secaoId, paragem.inicio, fim), 1))
            } else {
                context.getString(
                    R.string.paragem_entre, Datas.dataHora(paragem.inicio), Datas.hora(fim), Datas.duracao(context, fim - paragem.inicio)
                )
            }
            botaoRetomar.isVisible = resumo.podeRetomar()
            botaoRetomar.setOnClickListener { aoRetomar(resumo) }
            // “Ver AV-…”: toca na linha para abrir a avaria que parou a máquina
            root.setOnClickListener { if (resumo.avariaCodigo != null) aoAbrirAvaria(resumo) }
        }
    }

    private object Diferencas : DiffUtil.ItemCallback<ParagemResumo>() {
        override fun areItemsTheSame(antiga: ParagemResumo, nova: ParagemResumo) = antiga.paragem.id == nova.paragem.id
        override fun areContentsTheSame(antiga: ParagemResumo, nova: ParagemResumo) = antiga == nova
    }
}
