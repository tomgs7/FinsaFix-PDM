package pt.ubi.di.pdm.finsafix.ui.parametros

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.databinding.FragmentChecklistBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemPontoSimnaoBinding
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

class ChecklistViewModel(application: Application) : ViewModelBase(application) {
    fun registar(resultados: List<Boolean>, quem: Pessoa, aoTerminar: () -> Unit) {
        executar {
            if (repositorio.registarChecklist(resultados, quem)) {
                avisar(if (resultados.all { it }) R.string.checklist_registada else R.string.checklist_registada_nao_ok)
                aoTerminar()
            } else {
                avisar(R.string.checklist_ja_feita)
            }
        }
    }
}

/** “Checklist de arranque”: 6 pontos com OK / Não OK, “Marcar tudo OK” e o contador “0 de 6”. */
class ChecklistFragment : Fragment() {

    private var _binding: FragmentChecklistBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChecklistViewModel by viewModels()
    private val pontos = mutableListOf<ItemPontoSimnaoBinding>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChecklistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return
        for (texto in resources.getStringArray(R.array.checklist_pontos)) {
            val ponto = ItemPontoSimnaoBinding.inflate(layoutInflater, binding.listaPontos, false)
            ponto.textoPonto.text = texto
            ponto.botaoSim.setText(R.string.checklist_ok)
            ponto.botaoNao.setText(R.string.checklist_nao_ok)
            ponto.grupoSimNao.addOnButtonCheckedListener { _, _, _ -> atualizar() }
            binding.listaPontos.addView(ponto.root)
            pontos += ponto
        }
        binding.botaoTudoOk.setOnClickListener { pontos.forEach { it.grupoSimNao.check(it.botaoSim.id) } }
        binding.botaoRegistar.setOnClickListener {
            val resultados = pontos.map { it.grupoSimNao.checkedButtonId == it.botaoSim.id }
            viewModel.registar(resultados, pessoa) { findNavController().popBackStack() }
        }
        atualizar()
        mostrarErrosDe(viewModel)
    }

    /** “3 de 6” e o botão só fica ativo com todos os pontos respondidos. */
    private fun atualizar() {
        val respondidos = pontos.count { it.grupoSimNao.checkedButtonId != View.NO_ID }
        binding.textoContador.text = getString(R.string.checklist_contador, respondidos, pontos.size)
        binding.botaoRegistar.isEnabled = respondidos == pontos.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
