package pt.ubi.di.pdm.finsafix.ui.instrucoes

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Instrucao
import pt.ubi.di.pdm.finsafix.data.InstrucaoResumo
import pt.ubi.di.pdm.finsafix.databinding.FragmentListaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

class InstrucoesViewModel(application: Application) : ViewModelBase(application) {
    private val posto = MutableLiveData<String>()
    val instrucoes: LiveData<List<InstrucaoResumo>> = posto.switchMap { repositorio.instrucoesDaSecao(it).paraLiveData(emptyList()) }

    fun definirPosto(id: String) {
        if (posto.value != id) posto.value = id
    }
}

/** “Instruções” do operador: as instruções de trabalho da secção, só para ler (a engenharia é que as valida). */
class InstrucoesFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InstrucoesViewModel by viewModels()
    private val adapter = LinhaAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return
        viewModel.definirPosto(pessoa.postoId ?: return)

        binding.textoIntro.setText(R.string.instrucoes_intro)
        binding.textoResumo.isVisible = false
        binding.filtros.isVisible = false
        binding.textoVazio.setText(R.string.instrucoes_vazio)
        binding.lista.adapter = adapter
        binding.lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.instrucoes.observe(viewLifecycleOwner) { lista ->
            adapter.submitList(lista.map { linhaDe(it) })
            binding.textoVazio.isVisible = lista.isEmpty()
        }
        mostrarErrosDe(viewModel)
    }

    private fun linhaDe(resumo: InstrucaoResumo): Linha {
        val instrucao = resumo.instrucao
        val validada = instrucao.estado == Instrucao.VALIDADA
        return Linha(
            id = instrucao.id,
            estado = if (validada) EstiloAvaria.Estilo(R.string.instrucao_validada, R.drawable.ic_verified, R.color.if_ok_text)
            else if (instrucao.estado == Instrucao.CORRECAO) EstiloAvaria.Estilo(R.string.instrucao_correcao, R.drawable.ic_alert, R.color.if_error_text)
            else EstiloAvaria.Estilo(R.string.instrucao_por_validar, R.drawable.ic_clock, R.color.if_warn_text),
            textoEstado = if (validada) getString(
                R.string.instrucao_validada_por, resumo.validadaPorNome ?: "", Datas.dataHora(instrucao.dataValidacao ?: 0)
            ) else null,
            titulo = instrucao.titulo,
            sub = resources.getQuantityString(
                R.plurals.instrucao_resumo, instrucao.listaDePassos().size, instrucao.listaDePassos().size, instrucao.duracaoMin
            ),
            aoTocar = { mostrar(instrucao) }
        )
    }

    /** Os passos, um por linha, numerados. */
    private fun mostrar(instrucao: Instrucao) {
        val texto = instrucao.listaDePassos().mapIndexed { indice, passo -> "${indice + 1}. $passo" }.joinToString("\n\n")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(instrucao.titulo)
            .setMessage(texto)
            .setPositiveButton(R.string.botao_fechar, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
