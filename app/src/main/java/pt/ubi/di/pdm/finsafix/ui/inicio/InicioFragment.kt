package pt.ubi.di.pdm.finsafix.ui.inicio

import pt.ubi.di.pdm.finsafix.ui.comum.redesenharACada30s
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.data.Turno
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.databinding.FragmentInicioBinding
import pt.ubi.di.pdm.finsafix.ui.avisos.mostrarAvisosRecentes
import pt.ubi.di.pdm.finsafix.ui.comum.AvariaAdapter
import pt.ubi.di.pdm.finsafix.ui.comum.BannerParagem
import pt.ubi.di.pdm.finsafix.ui.comum.LinhaPorTratar
import pt.ubi.di.pdm.finsafix.ui.comum.PorTratarAdapter
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.formulario.FormularioDialogo
import pt.ubi.di.pdm.finsafix.ui.comum.abrirDetalhe
import pt.ubi.di.pdm.finsafix.ui.producao.ProducaoFragment
import pt.ubi.di.pdm.finsafix.ui.mais.mostrarCartaoPrimeiraVez
import pt.ubi.di.pdm.finsafix.util.Numeros
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** Início do operador: estado dos equipamentos, Reportar avaria e avarias abertas da secção. */
class InicioFragment : Fragment() {

    private var _binding: FragmentInicioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InicioViewModel by viewModels()
    private val equipamentoAdapter = EquipamentoAdapter()
    private val avariaAdapter = AvariaAdapter { abrirDetalhe(it) }
    private lateinit var banner: BannerParagem

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInicioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return
        if (pessoa.papel == Pessoa.PAPEL_OPERADOR) {
            mostrarCartaoPrimeiraVez((binding.root.getChildAt(0)) as ViewGroup, pessoa)
            ligarPorFazer()
        }
        if (pessoa.papel == Pessoa.PAPEL_CHEFE) ligarChefia()
        // O chefe vê o mesmo ecrã (o estado da secção), mas não regista produção.
        binding.botaoProducao.isVisible = pessoa.papel != Pessoa.PAPEL_CHEFE

        ligarEquipamentos()
        ligarBotoes()
        ligarListaDeAvarias()
        binding.avisos.ligacaoHistorico.isVisible = true
        binding.avisos.ligacaoHistorico.setOnClickListener { findNavController().navigate(R.id.historicoFragment) }
        mostrarAvisosRecentes(binding.avisos, viewModel.avisos)
        mostrarErrosDe(viewModel)
    }

    /** OP-04: “Por fazer” até haver checklist de arranque neste turno. */
    private fun ligarPorFazer() {
        binding.cartaoPorFazer.setOnClickListener { findNavController().navigate(R.id.checklistFragment) }
        viewModel.checklistFeita.observe(viewLifecycleOwner) { binding.cartaoPorFazer.isVisible = !it }
    }

    /** O banner da paragem e os equipamentos (1 coluna no telemóvel, 3 no tablet: values-sw600dp/integers.xml). */
    private fun ligarEquipamentos() {
        banner = BannerParagem(this, binding.bannerParagem) { findNavController().navigate(R.id.acao_inicio_para_paragens) }
        banner.iniciar()
        viewModel.paragemNoBanner.observe(viewLifecycleOwner) { banner.mostrar(it) }
        binding.listaEquipamentos.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.colunas_equipamentos))
        binding.listaEquipamentos.adapter = equipamentoAdapter
        viewModel.equipamentos.observe(viewLifecycleOwner) {
            equipamentoAdapter.submitList(it)
            binding.textoACarregar.isVisible = it.isEmpty() // sem equipamentos: os dados ainda estão a carregar
        }
        redesenharACada30s(equipamentoAdapter) // a duração “Parada · 14 min” dos equipamentos muda com a hora
    }

    private fun ligarBotoes() {
        binding.botaoReportar.setOnClickListener { FormularioDialogo.novo().show(childFragmentManager, "formulario") }
        binding.botaoProducao.setOnClickListener { findNavController().navigate(R.id.producaoFragment) }
        binding.botaoRegistarParagem.setOnClickListener { findNavController().navigate(R.id.acao_inicio_para_paragens) }
        binding.botaoPedirAjuda.setOnClickListener { findNavController().navigate(R.id.pedidosOperadorFragment) }
    }

    /** As avarias da secção, com a pesquisa e os filtros (Todas, Pendentes, Em curso, Mecânica, Elétrica). */
    private fun ligarListaDeAvarias() {
        binding.listaAvarias.adapter = avariaAdapter
        binding.listaAvarias.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL)
        )
        binding.chipsFiltro.setOnCheckedStateChangeListener { _, idsMarcados ->
            val filtro = when (idsMarcados.firstOrNull()) {
                R.id.chipPendentes -> FiltroAvarias.PENDENTES
                R.id.chipEmCurso -> FiltroAvarias.EM_CURSO
                R.id.chipMecanica -> FiltroAvarias.MECANICA
                R.id.chipEletrica -> FiltroAvarias.ELETRICA
                else -> FiltroAvarias.TODAS
            }
            viewModel.escolherFiltro(filtro)
        }
        binding.textoPesquisa.doAfterTextChanged { viewModel.pesquisar(it?.toString().orEmpty()) }
        viewModel.lista.observe(viewLifecycleOwner) { lista ->
            avariaAdapter.submitList(lista.avarias)
            binding.textoSemAvarias.isVisible = lista.avarias.isEmpty()
            mostrarContagens(lista)
        }
    }

    /** Chefe de secção: “Por tratar” (cada linha abre o ecrã certo) e os operadores de cada turno. */
    private fun ligarChefia() {
        val partes = binding.chefia
        partes.root.isVisible = true
        val porTratar = PorTratarAdapter()
        partes.listaPorTratar.adapter = porTratar
        partes.listaPorTratar.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.porTratar.observe(viewLifecycleOwner) { dados ->
            val pedidos = dados.pedidos + dados.avisos
            val linhas = listOfNotNull(
                LinhaPorTratar("aut", resources.getQuantityString(R.plurals.por_tratar_autorizacoes, dados.autorizacoes, dados.autorizacoes)) {
                    findNavController().navigate(R.id.validarFragment)
                }.takeIf { dados.autorizacoes > 0 },
                // vermelho quando há pedidos de ajuda urgentes não está nos dados; fica como aviso
                LinhaPorTratar("ped", resources.getQuantityString(R.plurals.por_tratar_pedidos, pedidos, pedidos)) {
                    findNavController().navigate(R.id.pedidosFragment)
                }.takeIf { pedidos > 0 },
                LinhaPorTratar("chk", resources.getQuantityString(R.plurals.por_tratar_checklists, dados.checklists, dados.checklists)) {
                    findNavController().navigate(R.id.validarFragment)
                }.takeIf { dados.checklists > 0 },
                LinhaPorTratar("pas", resources.getQuantityString(R.plurals.por_tratar_passagens, dados.passagens, dados.passagens)) {
                    findNavController().navigate(R.id.validarFragment)
                }.takeIf { dados.passagens > 0 }
            )
            porTratar.submitList(linhas)
            partes.textoNadaPorTratar.isVisible = linhas.isEmpty()
        }
        val turnoAtual = Datas.turnoAtual()
        partes.tituloOperadores.text = getString(R.string.chefia_operadores_titulo, getString(Datas.nomeTurno(turnoAtual)).lowercase())
        viewModel.checklistFeita.observe(viewLifecycleOwner) { feita ->
            checklistTexto = getString(if (feita) R.string.chefia_checklist_feita else R.string.chefia_checklist_por_fazer)
            viewModel.pessoas.value?.let { mostrarOperadores(it) }
        }
        viewModel.pessoas.observe(viewLifecycleOwner) { mostrarOperadores(it) }
    }

    private var checklistTexto = ""

    private fun mostrarOperadores(pessoas: List<Pessoa>) {
        val partes = binding.chefia
        val turnoAtual = Datas.turnoAtual()
        // Uma linha por turno (“Manhã · José Martins · Em turno”) e, no fim, a checklist de arranque
        val linhas = Turno.TODOS.map { turno ->
            val nomes = pessoas.filter { it.turno == turno && it.papel == Pessoa.PAPEL_OPERADOR }.joinToString(", ") { it.nome }
                .ifEmpty { getString(R.string.turno_sem_escala) }
            listOfNotNull(
                getString(R.string.chefia_operadores_linha, getString(Datas.nomeTurno(turno)), nomes),
                getString(R.string.chefia_em_turno).takeIf { turno == turnoAtual }
            ).joinToString(EstiloAvaria.SEPARADOR)
        }
        partes.textoOperadores.text = listOf(linhas.joinToString("\n"), checklistTexto).joinToString("\n")
    }

    /** Resumo e contagens nos filtros (“Todas 3”, “Mecânica 2”…). */
    private fun mostrarContagens(lista: ListaAvarias) {
        val c = lista.contagens
        binding.textoResumoAvarias.text = getString(
            R.string.inicio_resumo,
            resources.getQuantityString(R.plurals.resumo_abertas, c[FiltroAvarias.TODAS] ?: 0, c[FiltroAvarias.TODAS] ?: 0),
            c[FiltroAvarias.EM_CURSO] ?: 0,
            resources.getQuantityString(R.plurals.resumo_paragens, lista.paragens, lista.paragens),
            Numeros.formatar(lista.producao, 0), getString(ProducaoFragment.nomeDaUnidade(lista.unidade))
        )
        mapOf(
            binding.chipTodas to Pair(R.string.filtro_todas, FiltroAvarias.TODAS),
            binding.chipPendentes to Pair(R.string.filtro_pendentes, FiltroAvarias.PENDENTES),
            binding.chipEmCurso to Pair(R.string.filtro_em_curso, FiltroAvarias.EM_CURSO),
            binding.chipMecanica to Pair(R.string.filtro_mecanica, FiltroAvarias.MECANICA),
            binding.chipEletrica to Pair(R.string.filtro_eletrica, FiltroAvarias.ELETRICA)
        ).forEach { (chip, dados) ->
            chip.text = getString(R.string.filtro_com_contagem, getString(dados.first), c[dados.second] ?: 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
