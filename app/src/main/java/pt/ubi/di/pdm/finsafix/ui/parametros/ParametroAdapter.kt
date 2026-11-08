package pt.ubi.di.pdm.finsafix.ui.parametros

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Parametro
import pt.ubi.di.pdm.finsafix.data.ParametroComLeitura
import pt.ubi.di.pdm.finsafix.databinding.ItemParametroBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Numeros

/** Cartão de um parâmetro: limite, estado, último valor (toque para o teclado) e quem o registou. */
class ParametroAdapter(
    private val aoTocar: (Parametro) -> Unit,
    private val aoAvisarChefe: (ParametroComLeitura) -> Unit
) : ListAdapter<ParametroComLeitura, ParametroAdapter.ViewHolder>(Diferencas) {

    class ViewHolder(val binding: ItemParametroBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemParametroBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val p = item.parametro
        val context = holder.itemView.context
        with(holder.binding) {
            textoNome.text = p.nome
            textoLimite.text = descreverLimite(context, p)
            textoUnidade.text = p.unidade
            barraLimites.definir(p, item.valor)
            val estado = item.valor?.let { p.estado(it) }
            textoEstado.isVisible = estado != null
            estado?.let { EstiloAvaria.aplicar(textoEstado, EstiloAvaria.estadoParametro(it)) }
            textoAviso.isVisible = estado == Parametro.Estado.FORA
            botaoAvisarChefe.isVisible = estado == Parametro.Estado.FORA
            botaoAvisarChefe.setOnClickListener { aoAvisarChefe(item) }
            textoValor.text = item.valor?.let { Numeros.formatar(it, p.decimais) } ?: context.getString(R.string.valor_vazio)
            textoRegistado.text = if (item.data != null) {
                context.getString(R.string.registado_por, item.pessoaNome ?: "", Datas.hora(item.data))
            } else {
                context.getString(R.string.sem_leitura)
            }
            // O cartão todo abre o teclado (alvo grande); o campo de valor também, com o efeito de toque.
            root.setOnClickListener { aoTocar(p) }
            campoValor.setOnClickListener { aoTocar(p) }
        }
    }

    private object Diferencas : DiffUtil.ItemCallback<ParametroComLeitura>() {
        override fun areItemsTheSame(antigo: ParametroComLeitura, novo: ParametroComLeitura) =
            antigo.parametro.id == novo.parametro.id
        override fun areContentsTheSame(antigo: ParametroComLeitura, novo: ParametroComLeitura) = antigo == novo
    }

    companion object {
        /** "Limite máx. 4,0 %" ou "Limite 5,5 – 7,0 bar". */
        fun descreverLimite(context: Context, p: Parametro): String =
            if (p.tipoLimite == Parametro.LIMITE_MAXIMO) {
                context.getString(R.string.limite_maximo, Numeros.formatar(p.maximo, p.decimais), p.unidade)
            } else {
                context.getString(
                    R.string.limite_intervalo, Numeros.formatar(p.minimo, p.decimais), Numeros.formatar(p.maximo, p.decimais), p.unidade
                )
            }
    }
}
