package pt.ubi.di.pdm.finsafix.ui.instrucoes

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.ui.comum.escolherSecao
import pt.ubi.di.pdm.finsafix.data.Instrucao
import pt.ubi.di.pdm.finsafix.data.InstrucaoResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.databinding.FragmentListaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

class InstrucoesEngenhariaViewModel(application: Application) : ViewModelBase(application) {
    val instrucoes: LiveData<List<InstrucaoResumo>> = repositorio.todasAsInstrucoes().paraLiveData(emptyList())
    val secoes: LiveData<List<Secao>> = repositorio.secoes().paraLiveData(emptyList())

    fun validar(id: String, aprovar: Boolean, eng: Pessoa) {
        executar { avisar(if (repositorio.validarInstrucao(id, aprovar, eng)) (if (aprovar) R.string.instrucao_aprovada else R.string.instrucao_correcao_pedida) else R.string.erro_decisao) }
    }
}

/** “Instruções” da engenharia: por validar, correção pedida e aprovadas, com pesquisa, filtro de secção e janela de revisão. */
class InstrucoesEngenhariaFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InstrucoesEngenhariaViewModel by viewModels()
    private val adapter = LinhaAdapter()
    private var estado = Instrucao.POR_VALIDAR
    private var secao: Secao? = null
    private var pesquisa = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val eng = pessoaOuVoltarAEntrada() ?: return
        binding.textoIntro.setText(R.string.instrucoes_eng_intro)
        binding.textoResumo.isVisible = false
        binding.campoPesquisa.isVisible = true
        binding.textoPesquisa.doAfterTextChanged {
            pesquisa = it?.toString().orEmpty()
            mostrar(eng)
        }
        binding.botaoAcao.isVisible = true
        binding.botaoAcao.setOnClickListener { escolherSecao(eng) }
        for (e in listOf(Instrucao.POR_VALIDAR, Instrucao.CORRECAO, Instrucao.VALIDADA)) {
            binding.filtros.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                tag = e
                isCheckable = true
                isChecked = e == estado
                setOnClickListener {
                    estado = e
                    mostrar(eng)
                }
            })
        }
        binding.filtros.isSingleSelection = true
        binding.lista.adapter = adapter
        binding.lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.secoes.observe(viewLifecycleOwner) { }
        viewModel.instrucoes.observe(viewLifecycleOwner) { mostrar(eng) }
        mostrarErrosDe(viewModel)
    }

    private fun mostrar(eng: Pessoa) {
        val todas = viewModel.instrucoes.value.orEmpty()
        binding.botaoAcao.text = secao?.nome ?: getString(R.string.consulta_todas_secoes)
        // Os chips mostram a contagem de cada estado (“Por validar 4”)
        for (i in 0 until binding.filtros.childCount) {
            val chip = binding.filtros.getChildAt(i) as Chip
            val nome = when (chip.tag) {
                Instrucao.POR_VALIDAR -> R.string.instrucao_por_validar
                Instrucao.CORRECAO -> R.string.instrucao_correcao
                else -> R.string.instrucao_aprovadas
            }
            chip.text = getString(R.string.filtro_com_contagem, getString(nome), todas.count { it.instrucao.estado == chip.tag })
        }
        val termo = pesquisa.trim().lowercase()
        val visiveis = todas.filter { it.instrucao.estado == estado && (secao == null || it.instrucao.secaoId == secao?.id) &&
            (termo.isEmpty() || it.instrucao.titulo.lowercase().contains(termo)) }
        adapter.submitList(visiveis.map { resumo ->
            val i = resumo.instrucao
            Linha(
                id = i.id, titulo = i.titulo,
                sub = listOf(resumo.secaoNome, resources.getQuantityString(R.plurals.instrucao_resumo, i.listaDePassos().size, i.listaDePassos().size, i.duracaoMin)).joinToString(EstiloAvaria.SEPARADOR),
                aoTocar = { rever(resumo, eng) }
            )
        })
        binding.textoVazio.setText(R.string.instrucoes_eng_vazio)
        binding.textoVazio.isVisible = visiveis.isEmpty()
    }

    /** Janela de revisão: os passos e “Aprovar” ou “Pedir correção”. */
    private fun rever(resumo: InstrucaoResumo, eng: Pessoa) {
        val i = resumo.instrucao
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(i.titulo)
            .setMessage(i.listaDePassos().mapIndexed { n, passo -> "${n + 1}. $passo" }.joinToString("\n\n"))
            .setPositiveButton(R.string.instrucao_aprovar) { _, _ -> viewModel.validar(i.id, true, eng) }
            .setNegativeButton(R.string.instrucao_pedir_correcao) { _, _ -> viewModel.validar(i.id, false, eng) }
            .setNeutralButton(R.string.botao_fechar, null)
            .show()
    }

    private fun escolherSecao(eng: Pessoa) {
        val secoes = viewModel.secoes.value.orEmpty().filter { it.tipo == Secao.TIPO_SECAO }
        escolherSecao(getString(R.string.consulta_escolher_secao), secoes, secao?.id, comTodas = true) {
            secao = it
            mostrar(eng)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
