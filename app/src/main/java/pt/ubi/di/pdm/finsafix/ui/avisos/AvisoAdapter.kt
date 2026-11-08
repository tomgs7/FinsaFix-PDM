package pt.ubi.di.pdm.finsafix.ui.avisos

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Aviso
import pt.ubi.di.pdm.finsafix.data.AvisoResumo
import pt.ubi.di.pdm.finsafix.databinding.ItemAvisoBinding
import pt.ubi.di.pdm.finsafix.databinding.ParteAvisosRecentesBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.util.Datas

/**
 * Uma linha de aviso: o que foi avisado, por quem e se já tem resposta.
 * Com [aoResponder] (ecrã Pedidos do chefe) cada aviso por responder tem o botão de resposta.
 */
class AvisoAdapter(private val aoResponder: ((AvisoResumo) -> Unit)? = null) :
    ListAdapter<AvisoResumo, AvisoAdapter.ViewHolder>(Diferencas) {

    class ViewHolder(val binding: ItemAvisoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemAvisoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val resumo = getItem(position)
        val aviso = resumo.aviso
        val context = holder.itemView.context
        with(holder.binding) {
            val estilo = when (aviso.tipo) {
                Aviso.PARAR -> EstiloAvaria.Estilo(R.string.aviso_parar, R.drawable.ic_stop_x, R.color.if_error_text)
                Aviso.ARRANCAR -> EstiloAvaria.Estilo(R.string.aviso_arrancar, R.drawable.ic_play, R.color.if_ok_text)
                Aviso.A_CAMINHO -> EstiloAvaria.Estilo(R.string.aviso_a_caminho, R.drawable.ic_run, R.color.if_info_text)
                Aviso.ESPERA_PECA -> EstiloAvaria.Estilo(R.string.aviso_espera_peca, R.drawable.ic_clock, R.color.if_warn_text)
                Aviso.DEMORA -> EstiloAvaria.Estilo(R.string.aviso_demora, R.drawable.ic_timer, R.color.if_warn_text)
                else -> EstiloAvaria.Estilo(R.string.aviso_chefe_fora_limite, R.drawable.ic_alert, R.color.if_warn_text)
            }
            EstiloAvaria.aplicar(textoTipo, estilo)
            if (aviso.tipo == Aviso.FORA_LIMITE) textoTipo.text = context.getString(R.string.aviso_chefe_fora_limite, aviso.texto ?: "")

            textoQuem.text = listOfNotNull(resumo.equipamentoNome, resumo.criadoPorNome, Datas.hora(aviso.dataCriacao))
                .joinToString(EstiloAvaria.SEPARADOR)

            val porResponder = aviso.dataResposta == null
            botaoResponder.isVisible = aoResponder != null && porResponder
            botaoResponder.setText(
                when (aviso.tipo) {
                    Aviso.PARAR -> R.string.botao_maquina_parada
                    Aviso.ARRANCAR -> R.string.botao_percebido
                    else -> R.string.botao_visto
                }
            )
            botaoResponder.setOnClickListener { aoResponder?.invoke(resumo) }

            val esperaMs = System.currentTimeMillis() - aviso.dataCriacao
            if (aviso.dataResposta != null) {
                textoResposta.text = context.getString(
                    R.string.aviso_respondido, resumo.respondidoPorNome ?: "", Datas.hora(aviso.dataResposta)
                )
                textoResposta.setTextColor(ContextCompat.getColor(context, R.color.if_ok_text))
            } else if (esperaMs >= Aviso.DESTAQUE_MS) {
                val minutos = (esperaMs / 60_000).toInt()
                textoResposta.text = context.resources.getQuantityString(R.plurals.aviso_sem_resposta, minutos, minutos)
                textoResposta.setTextColor(ContextCompat.getColor(context, R.color.if_error_text))
            } else {
                textoResposta.setText(R.string.aviso_por_responder)
                textoResposta.setTextColor(ContextCompat.getColor(context, R.color.if_warn_text))
            }
        }
    }

    private object Diferencas : DiffUtil.ItemCallback<AvisoResumo>() {
        override fun areItemsTheSame(antigo: AvisoResumo, novo: AvisoResumo) = antigo.aviso.id == novo.aviso.id
        override fun areContentsTheSame(antigo: AvisoResumo, novo: AvisoResumo) = antigo == novo
    }

}

/** Liga a lista "Avisos recentes" (a mesma no Início e no Histórico) aos [avisos] do ViewModel. */
fun Fragment.mostrarAvisosRecentes(parte: ParteAvisosRecentesBinding, avisos: LiveData<List<AvisoResumo>>) {
    val adapter = AvisoAdapter()
    parte.listaAvisos.adapter = adapter
    parte.listaAvisos.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
    avisos.observe(viewLifecycleOwner) {
        adapter.submitList(it)
        parte.textoSemAvisos.isVisible = it.isEmpty()
    }
}
