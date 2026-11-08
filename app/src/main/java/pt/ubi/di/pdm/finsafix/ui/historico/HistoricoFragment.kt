package pt.ubi.di.pdm.finsafix.ui.historico

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.databinding.FragmentListaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.abrirDetalhe
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** “Histórico”: filtros por tipo com contagem (“Tudo 33”, “Avarias 13”…) e a lista cronológica de tudo. */
class HistoricoFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoricoViewModel by viewModels()
    private val adapter = LinhaAdapter()
    private var filtro: TipoHistorico? = null // nulo = tudo

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return
        viewModel.definirPessoa(pessoa)
        val completo = pessoa.papel != Pessoa.PAPEL_TECNICO // o técnico só vê as avarias resolvidas
        binding.textoIntro.setText(if (completo) R.string.hist_intro else R.string.hist_intro_tecnico)
        binding.textoResumo.isVisible = false
        binding.filtros.isVisible = completo
        if (completo) {
            for (tipo in listOf<TipoHistorico?>(null) + TipoHistorico.entries) {
                binding.filtros.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                    tag = tipo
                    isCheckable = true
                    isChecked = tipo == filtro
                    setOnClickListener {
                        filtro = tipo
                        mostrar()
                    }
                })
            }
        }
        binding.textoVazio.setText(R.string.hist_vazio)
        binding.lista.adapter = adapter
        binding.lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.entradas.observe(viewLifecycleOwner) { mostrar() }
        mostrarErrosDe(viewModel)
    }

    private fun mostrar() {
        val todas = viewModel.entradas.value.orEmpty()
        for (i in 0 until binding.filtros.childCount) {
            val chip = binding.filtros.getChildAt(i) as Chip
            val tipo = chip.tag as TipoHistorico?
            chip.text = getString(R.string.filtro_com_contagem, getString(tipo?.nome ?: R.string.hist_tudo), if (tipo == null) todas.size else todas.count { it.tipo == tipo })
        }
        val visiveis = todas.filter { filtro == null || it.tipo == filtro }
        adapter.submitList(visiveis.mapIndexed { i, e ->
            Linha(
                id = "$i-${e.data}", titulo = e.titulo,
                sub = listOf(getString(e.tipo.nome), e.sub, Datas.dataHora(e.data)).filter { it.isNotBlank() }.joinToString(EstiloAvaria.SEPARADOR),
                aoTocar = e.avariaId?.let { id -> { abrirDetalhe(id, e.codigo ?: "") } }
            )
        })
        binding.textoVazio.isVisible = visiveis.isEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
