package pt.ubi.di.pdm.finsafix.ui.consulta

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.ui.comum.escolherSecao
import pt.ubi.di.pdm.finsafix.data.Autorizacao
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.databinding.FragmentConsultaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.AvariaAdapter
import pt.ubi.di.pdm.finsafix.ui.autorizacoes.AutorizacaoAdapter
import pt.ubi.di.pdm.finsafix.ui.comum.abrirDetalhe
import pt.ubi.di.pdm.finsafix.ui.comum.abrirDetalhe
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/**
 * "Avarias" da engenharia (com filtro por secção e estado) e "Oficina" do chefe de oficina (só a sua especialidade).
 * É só consulta: tocar numa avaria abre o detalhe, onde estes perfis não têm botões de ação.
 */
class ConsultaFragment : Fragment() {

    private var _binding: FragmentConsultaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConsultaViewModel by viewModels()
    private val adapter = AvariaAdapter { abrirDetalhe(it) }
    private val adapterAutorizacoes = AutorizacaoAdapter { abrirDetalhe(it.autorizacao.avariaId, it.avariaCodigo) }
    private var verAutorizacoes = false
    private var soAtivas = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConsultaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return
        viewModel.definirPessoa(pessoa)
        val engenharia = pessoa.papel == Pessoa.PAPEL_ENGENHEIRO

        binding.textoAmbito.setText(R.string.consulta_ambito_fabrica)
        binding.botaoSecao.isVisible = engenharia // o chefe de oficina não escolhe secção
        binding.botaoSecao.setOnClickListener { escolherSecao() }
        ligarSeparadores(engenharia)
        ligarFiltroDeOficina(engenharia)

        binding.listaAvarias.adapter = adapter
        binding.listaAvarias.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL)
        )
        viewModel.filtro.observe(viewLifecycleOwner) {
            binding.botaoSecao.text = it.secao?.nome ?: getString(R.string.consulta_todas_secoes)
        }
        viewModel.avarias.observe(viewLifecycleOwner) { avarias ->
            adapter.submitList(avarias)
            binding.textoSemAvarias.isVisible = avarias.isEmpty()
        }
        mostrarErrosDe(viewModel)
    }

    /** Os separadores Avarias (por estado) e, só na engenharia, Autorizações (Ativas e Histórico). */
    private fun ligarSeparadores(engenharia: Boolean) {
        binding.chipAutorizacoes.isVisible = engenharia
        binding.chipAtivas.setOnClickListener { soAtivas = true; mostrarAutorizacoes() }
        binding.chipHistoricoAut.setOnClickListener { soAtivas = false; mostrarAutorizacoes() }
        viewModel.autorizacoes.observe(viewLifecycleOwner) { mostrarAutorizacoes() }
        binding.chipsEstado.setOnCheckedStateChangeListener { _, ids ->
            // No separador Autorizações a lista, os filtros e os chips mudam
            verAutorizacoes = ids.firstOrNull() == R.id.chipAutorizacoes
            binding.chipsAutorizacoes.isVisible = verAutorizacoes
            binding.chipsOficina.isVisible = engenharia && !verAutorizacoes
            binding.listaAvarias.adapter = if (verAutorizacoes) adapterAutorizacoes else adapter
            mostrarAutorizacoes()
            viewModel.mudarEstados(
                when (ids.firstOrNull()) {
                    R.id.chipPendentes -> listOf(Avaria.PENDENTE)
                    R.id.chipEmCurso -> listOf(Avaria.EM_CURSO)
                    R.id.chipResolvidas -> listOf(Avaria.RESOLVIDA)
                    R.id.chipPorAprovar -> listOf(Avaria.RESOLVIDA)
                    else -> Avaria.ESTADOS
                },
                soPorAprovar = ids.firstOrNull() == R.id.chipPorAprovar
            )
        }
    }

    /** Só a engenharia decide e filtra por oficina; o chefe de oficina já vê só a sua. */
    private fun ligarFiltroDeOficina(engenharia: Boolean) {
        binding.chipPorAprovar.isVisible = engenharia
        binding.chipsOficina.isVisible = engenharia
        binding.textoDecisoes.isVisible = engenharia
        binding.chipsOficina.setOnCheckedStateChangeListener { _, ids ->
            viewModel.mudarEspecialidade(
                when (ids.firstOrNull()) {
                    R.id.chipOfMecanica -> Avaria.MECANICA
                    R.id.chipOfEletrica -> Avaria.ELETRICA
                    R.id.chipOfNaoSei -> Avaria.NAO_SEI
                    else -> null
                }
            )
        }
        if (engenharia) viewModel.decisoes.observe(viewLifecycleOwner) { (porClassificar, porAprovar) ->
            binding.textoDecisoes.text = getString(R.string.consulta_decisoes, porClassificar, porAprovar)
        }
    }

    /** Ativas (pedidas, aprovadas e em curso) ou Histórico (recusadas e encerradas), com a contagem nos chips. */
    private fun mostrarAutorizacoes() {
        val binding = _binding ?: return
        val todas = viewModel.autorizacoes.value.orEmpty()
        val ativas = todas.filter { it.autorizacao.estado in listOf(Autorizacao.PEDIDA, Autorizacao.APROVADA, Autorizacao.EM_CURSO) }
        val historico = todas - ativas.toSet()
        binding.chipAtivas.text = getString(R.string.aut_ativas, ativas.size)
        binding.chipHistoricoAut.text = getString(R.string.aut_historico, historico.size)
        if (!verAutorizacoes) return
        val lista = if (soAtivas) ativas else historico
        adapterAutorizacoes.submitList(lista)
        binding.textoSemAvarias.setText(R.string.consulta_sem_autorizacoes)
        binding.textoSemAvarias.isVisible = lista.isEmpty()
    }

    /** "Todas as secções" ou uma das secções (as oficinas não têm avarias). */
    private fun escolherSecao() {
        val secoes = viewModel.secoes.value.orEmpty().filter { it.tipo == Secao.TIPO_SECAO }
        escolherSecao(getString(R.string.consulta_escolher_secao), secoes, viewModel.filtro.value?.secao?.id, comTodas = true) { viewModel.mudarSecao(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
