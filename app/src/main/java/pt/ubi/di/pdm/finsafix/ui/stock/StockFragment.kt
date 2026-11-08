package pt.ubi.di.pdm.finsafix.ui.stock

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Peca
import pt.ubi.di.pdm.finsafix.data.PecaResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.databinding.FragmentListaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.lista.Acao
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

class StockViewModel(application: Application) : ViewModelBase(application) {
    val pecas: LiveData<List<PecaResumo>> = repositorio.pecas().paraLiveData(emptyList())

    fun ajustar(id: String, variacao: Int, quem: Pessoa) {
        executar { if (!repositorio.ajustarStock(id, variacao, quem)) avisar(R.string.erro_stock) }
    }

    fun pedirReposicao(id: String, quem: Pessoa) {
        executar { avisar(if (repositorio.pedirReposicao(id, quem)) R.string.reposicao_pedida else R.string.erro_stock) }
    }
}

/** “Stock” do técnico: peças da oficina, com −1, +1, +5 e “Pedir reposição” nas da sua especialidade. */
class StockFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StockViewModel by viewModels()
    private val adapter = LinhaAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return

        binding.textoIntro.setText(R.string.stock_intro)
        binding.filtros.isVisible = false
        binding.textoVazio.setText(R.string.stock_vazio)
        binding.lista.adapter = adapter
        binding.lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.pecas.observe(viewLifecycleOwner) { lista ->
            // As que precisam de reposição primeiro
            adapter.submitList(lista.sortedBy { it.peca.estado().ordinal }.map { linhaDe(it, pessoa) })
            binding.textoVazio.isVisible = lista.isEmpty()
            val abaixo = lista.count { it.peca.estado() <= Peca.Estado.ABAIXO }
            binding.textoResumo.text = resources.getQuantityString(R.plurals.stock_resumo, abaixo, abaixo, lista.size)
        }
        mostrarErrosDe(viewModel)
    }

    private fun linhaDe(resumo: PecaResumo, pessoa: Pessoa): Linha {
        val peca = resumo.peca
        val estado = when (peca.estado()) {
            Peca.Estado.RUTURA -> EstiloAvaria.Estilo(R.string.stock_rutura, R.drawable.ic_alert, R.color.if_error_text)
            Peca.Estado.ABAIXO -> EstiloAvaria.Estilo(R.string.stock_abaixo, R.drawable.ic_alert, R.color.if_warn_text)
            Peca.Estado.NO_LIMITE -> EstiloAvaria.Estilo(R.string.stock_no_limite, R.drawable.ic_info, R.color.if_info_text)
            Peca.Estado.OK -> EstiloAvaria.Estilo(R.string.stock_ok, R.drawable.ic_check_circle, R.color.if_ok_text)
        }
        val acoes = if (peca.especialidade == pessoa.especialidade) {
            listOfNotNull(
                Acao("−1") { viewModel.ajustar(peca.id, -1, pessoa) },
                Acao("+1") { viewModel.ajustar(peca.id, 1, pessoa) },
                Acao("+5") { viewModel.ajustar(peca.id, 5, pessoa) },
                Acao(getString(R.string.stock_pedir)) { viewModel.pedirReposicao(peca.id, pessoa) }
                    .takeIf { !peca.pedida && peca.stock < peca.minimo }
            )
        } else emptyList()
        return Linha(
            id = peca.id,
            estado = estado,
            titulo = peca.nome,
            sub = getString(R.string.stock_linha, peca.stock, peca.minimo, resumo.secaoNome),
            extra = if (peca.pedida) getString(
                R.string.stock_pedida_por, resumo.pedidaPorNome ?: "", Datas.dataHora(peca.dataPedido ?: 0)
            ) else null,
            acoes = acoes
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
