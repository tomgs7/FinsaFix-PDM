package pt.ubi.di.pdm.finsafix.ui.tecnico

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.databinding.FragmentTecnicoBinding
import pt.ubi.di.pdm.finsafix.ui.comum.AvariaAdapter
import pt.ubi.di.pdm.finsafix.ui.comum.CabecalhoAdapter
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.ui.comum.abrirDetalhe
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.mais.mostrarCartaoPrimeiraVez
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/**
 * "Avarias da oficina" e "Minhas" do técnico. O argumento "apenasMinhas" escolhe o modo:
 * Avarias mostra "Em progresso · para retomar", os filtros e a lista por prioridade;
 * Minhas mostra só as intervenções em curso do técnico.
 */
class TecnicoFragment : Fragment() {

    private var _binding: FragmentTecnicoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TecnicoViewModel by viewModels()
    private val emProgressoAdapter = AvariaAdapter { abrirDetalhe(it) }
    private val avariasAdapter = AvariaAdapter { abrirDetalhe(it) }
    private val concluidasAdapter = AvariaAdapter { abrirDetalhe(it) }

    // prioridade → (cabeçalho, linhas), por ordem de importância
    private val grupos by lazy {
        listOf(
            Avaria.CRITICA to R.string.grupo_critica,
            Avaria.MEDIA to R.string.grupo_media,
            Avaria.BAIXA to R.string.grupo_baixa
        ).associate { (prioridade, titulo) ->
            prioridade to Pair(CabecalhoAdapter(getString(titulo), getString(R.string.grupo_vazio)), AvariaAdapter { abrirDetalhe(it) })
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTecnicoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tecnico = pessoaOuVoltarAEntrada() ?: return
        viewModel.definirTecnico(tecnico)
        val apenasMinhas = arguments?.getBoolean(ARG_APENAS_MINHAS) ?: false
        if (!apenasMinhas) mostrarCartaoPrimeiraVez(binding.root.getChildAt(0) as ViewGroup, tecnico)

        prepararLista(binding.listaEmProgresso, emProgressoAdapter)
        prepararLista(binding.listaAvarias, avariasAdapter)
        // “Na equipa” nas avarias em curso em que sou ajudante (a lista “Minhas” e os grupos das Avarias)
        viewModel.naEquipa.observe(viewLifecycleOwner) { ids ->
            avariasAdapter.definirNaEquipa(ids)
            grupos.forEach { (_, grupo) -> grupo.second.definirNaEquipa(ids) }
        }

        // Em "Minhas" não há secção "Em progresso" nem filtros.
        binding.filtros.isVisible = !apenasMinhas
        if (apenasMinhas) mostrarMinhas() else mostrarPendentes()
        mostrarErrosDe(viewModel)
    }

    /** “Minhas”: a intervenção em curso e as últimas concluídas. */
    private fun mostrarMinhas() {
        binding.textoTituloLista.setText(R.string.tecnico_em_curso)
        binding.textoSemAvarias.setText(R.string.tecnico_sem_intervencao)
        binding.botaoVerPendentes.setOnClickListener { findNavController().navigate(R.id.avariasTecnicoFragment) }
        binding.listaConcluidas.adapter = concluidasAdapter
        binding.listaConcluidas.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        binding.seccaoConcluidas.isVisible = true
        viewModel.minhas.observe(viewLifecycleOwner) {
            mostrarLista(it)
            binding.botaoVerPendentes.isVisible = it.isEmpty()
        }
        viewModel.concluidas.observe(viewLifecycleOwner) {
            concluidasAdapter.submitList(it)
            binding.textoSemConcluidas.isVisible = it.isEmpty()
        }
    }

    /** “Avarias”: o “Por fazer”, os filtros, o trabalho em progresso e as pendentes por prioridade. */
    private fun mostrarPendentes() {
        prepararGrupos()
        ligarPorFazer()
        ligarFiltros()
        viewModel.contagemOficinas.observe(viewLifecycleOwner) { (minha, todas) ->
            binding.chipMinhaOficina.text = getString(R.string.filtro_com_contagem, getString(R.string.filtro_minha_oficina), minha)
            binding.chipTodasOficinas.text = getString(R.string.filtro_com_contagem, getString(R.string.filtro_todas_oficinas), todas)
        }
        viewModel.emProgresso.observe(viewLifecycleOwner) { lista ->
            emProgressoAdapter.submitList(lista)
            binding.seccaoEmProgresso.isVisible = lista.isNotEmpty()
        }
        viewModel.avarias.observe(viewLifecycleOwner) { lista ->
            // A lista já vem por prioridade: reparte-se pelos 3 grupos
            for ((prioridade, grupo) in grupos) {
                val doGrupo = lista.filter { it.avaria.prioridade == prioridade }
                grupo.second.submitList(doGrupo)
                grupo.first.definirContagem(doGrupo.size)
            }
            binding.textoSemAvarias.isVisible = false
        }
    }

    /** “Por fazer” com ícone e ›: preventivas vencidas, peças abaixo do mínimo e parâmetros fora do limite. */
    private fun ligarPorFazer() {
        val adapter = LinhaAdapter()
        binding.listaPorFazer.adapter = adapter
        binding.listaPorFazer.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.porFazer.observe(viewLifecycleOwner) { dados ->
            val aviso = EstiloAvaria.Estilo(R.string.por_fazer_estado, R.drawable.ic_alert, R.color.if_warn_text)
            val linhas = listOfNotNull(
                Linha(
                    id = "pre", estado = aviso, titulo = resources.getQuantityString(R.plurals.por_fazer_preventivas, dados.preventivas, dados.preventivas),
                    aoTocar = { findNavController().navigate(R.id.preventivaFragment) }
                ).takeIf { dados.preventivas > 0 },
                Linha(
                    id = "peca", estado = aviso, titulo = resources.getQuantityString(R.plurals.por_fazer_pecas, dados.pecas, dados.pecas),
                    aoTocar = { findNavController().navigate(R.id.stockFragment) }
                ).takeIf { dados.pecas > 0 },
                Linha(id = "par", estado = aviso, titulo = getString(R.string.por_fazer_parametros, dados.parametros.joinToString(", ")))
                    .takeIf { dados.parametros.isNotEmpty() }
            )
            adapter.submitList(linhas)
            binding.seccaoPorFazer.isVisible = linhas.isNotEmpty()
        }
    }

    /** Os 3 grupos (Crítica, Média, Baixa): um cabeçalho com contagem e as linhas, tudo numa só lista. */
    private fun prepararGrupos() {
        val partes = mutableListOf<RecyclerView.Adapter<*>>()
        for ((prioridade, grupo) in grupos) {
            partes += grupo.first
            partes += grupo.second
        }
        binding.listaAvarias.adapter = ConcatAdapter(partes)
        binding.listaAvarias.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
    }

    private fun prepararLista(lista: RecyclerView, adapter: AvariaAdapter) {
        lista.adapter = adapter
        lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
    }

    private fun ligarFiltros() {
        binding.chipsOficina.setOnCheckedStateChangeListener { _, ids ->
            viewModel.mudarOficina(ids.firstOrNull() == R.id.chipTodasOficinas)
        }
        binding.chipsEstado.setOnCheckedStateChangeListener { _, ids ->
            viewModel.mudarEstados(
                when (ids.firstOrNull()) {
                    R.id.chipPendentes -> listOf(Avaria.PENDENTE)
                    R.id.chipEmCurso -> listOf(Avaria.EM_CURSO)
                    else -> listOf(Avaria.PENDENTE, Avaria.EM_CURSO)
                }
            )
        }
    }

    private fun mostrarLista(avarias: List<AvariaResumo>) {
        avariasAdapter.submitList(avarias)
        binding.textoSemAvarias.isVisible = avarias.isEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_APENAS_MINHAS = "apenasMinhas"
    }
}
