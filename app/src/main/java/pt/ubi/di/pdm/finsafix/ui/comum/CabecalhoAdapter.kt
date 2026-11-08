package pt.ubi.di.pdm.finsafix.ui.comum

import pt.ubi.di.pdm.finsafix.util.Numeros
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import pt.ubi.di.pdm.finsafix.databinding.ItemCabecalhoGrupoBinding

/**
 * Um cabeçalho de grupo (“Crítica · Intervir de imediato  1”) para pôr numa ConcatAdapter antes das linhas do grupo.
 * [vazio] é o texto mostrado quando o grupo não tem linhas.
 */
class CabecalhoAdapter(private val titulo: String, private val vazio: String) : RecyclerView.Adapter<CabecalhoAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCabecalhoGrupoBinding) : RecyclerView.ViewHolder(binding.root)

    private var contagem = 0

    fun definirContagem(novaContagem: Int) {
        if (novaContagem == contagem) return
        contagem = novaContagem
        notifyItemChanged(0)
    }

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemCabecalhoGrupoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.textoTitulo.text = titulo
        holder.binding.textoContagem.text = Numeros.formatar(contagem)
        holder.binding.textoVazio.text = vazio
        holder.binding.textoVazio.isVisible = contagem == 0
    }
}
