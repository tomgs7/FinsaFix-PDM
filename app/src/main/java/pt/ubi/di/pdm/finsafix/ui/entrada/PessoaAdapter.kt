package pt.ubi.di.pdm.finsafix.ui.entrada

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.databinding.ItemPessoaBinding
import pt.ubi.di.pdm.finsafix.util.Datas

/** Cartão de uma pessoa no ecrã de entrada: iniciais, nome, função e turno. */
class PessoaAdapter(
    private val aoClicar: (Pessoa) -> Unit
) : ListAdapter<Pessoa, PessoaAdapter.ViewHolder>(Diferencas) {

    class ViewHolder(val binding: ItemPessoaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPessoaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pessoa = getItem(position)
        val context = holder.itemView.context
        with(holder.binding) {
            textoIniciais.text = pessoa.iniciais()
            textoNome.text = pessoa.nome
            textoFuncao.text = context.getString(
                R.string.entrada_funcao_turno, pessoa.funcao, context.getString(Datas.nomeTurno(pessoa.turno))
            )
            textoTurnoAtual.isVisible = pessoa.turno == Datas.turnoAtual()
            root.setOnClickListener { aoClicar(pessoa) }
        }
    }

    private object Diferencas : DiffUtil.ItemCallback<Pessoa>() {
        override fun areItemsTheSame(antiga: Pessoa, nova: Pessoa) = antiga.id == nova.id
        override fun areContentsTheSame(antiga: Pessoa, nova: Pessoa) = antiga == nova
    }
}
