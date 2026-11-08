package pt.ubi.di.pdm.finsafix.ui.paragens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.ParagemResumo
import pt.ubi.di.pdm.finsafix.databinding.FragmentParagensBinding
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarMensagem
import pt.ubi.di.pdm.finsafix.ui.comum.abrirDetalhe
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** Paragens: registar (operador), retomar produção e ver os últimos 7 dias. */
class ParagensFragment : Fragment() {

    private var _binding: FragmentParagensBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParagensViewModel by viewModels()
    private val adapter = ParagemAdapter(
        aoRetomar = { viewModel.retomar(it.paragem.id) },
        aoAbrirAvaria = { it.paragem.avariaId?.let { id -> abrirDetalhe(id, it.avariaCodigo ?: "") } }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParagensBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return
        val operador = pessoa.papel == Pessoa.PAPEL_OPERADOR
        viewModel.mostrarTodasAsSecoes(!operador)

        // Quem regista paragens é o operador da secção; o técnico só consulta.
        binding.botaoRegistar.isVisible = operador
        binding.botaoRegistar.setOnClickListener { ParagemDialogo().show(childFragmentManager, "paragem") }

        binding.listaParagens.adapter = adapter
        binding.listaParagens.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL)
        )
        viewModel.paragens.observe(viewLifecycleOwner) { paragens ->
            adapter.submitList(paragens)
            binding.textoVazio.isVisible = paragens.isEmpty()
            mostrarKpis(paragens)
        }
        viewModel.mensagem.observe(viewLifecycleOwner) { mensagem ->
            if (mensagem != null) {
                mostrarMensagem(mensagem)
                viewModel.mensagemMostrada()
            }
        }
        mostrarErrosDe(viewModel)
    }

    /** “3 nas últimas 24 h · tempo parado 5 h 20 min · 7 nos últimos 7 dias” */
    private fun mostrarKpis(paragens: List<ParagemResumo>) {
        val agora = System.currentTimeMillis()
        val ultimas24h = paragens.count { it.paragem.inicio >= agora - UM_DIA_MS }
        val tempoParado = paragens.sumOf { (it.paragem.fim ?: agora) - it.paragem.inicio }
        binding.textoKpis.text = getString(R.string.paragens_kpis, ultimas24h, Datas.duracao(requireContext(), tempoParado), paragens.size)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val UM_DIA_MS = 24 * 60 * 60 * 1000L
    }
}
