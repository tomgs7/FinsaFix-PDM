package pt.ubi.di.pdm.finsafix.ui.progresso

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.ChipGroup
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.databinding.FragmentProgressoBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemChipOpcaoBinding
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarMensagem
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** Deixar em progresso. Recebe o argumento "avariaId". */
class ProgressoFragment : Fragment() {

    private var _binding: FragmentProgressoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProgressoViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProgressoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tecnico = pessoaOuVoltarAEntrada() ?: return
        val avariaId = requireArguments().getLong(ARG_AVARIA_ID)

        criarOpcoes(binding.grupoOrdem, resources.getStringArray(R.array.quem_deu_ordem), viewModel.ordem) {
            viewModel.ordem = it
        }
        criarOpcoes(binding.grupoMotivo, resources.getStringArray(R.array.motivos_progresso), viewModel.motivo) {
            viewModel.motivo = it
        }
        when (viewModel.retoma) {
            Avaria.RETOMA_TURNO -> binding.grupoRetoma.check(R.id.botaoProximoTurno)
            Avaria.RETOMA_DIA -> binding.grupoRetoma.check(R.id.botaoAmanha)
        }
        binding.grupoRetoma.addOnButtonCheckedListener { _, idBotao, marcado ->
            if (marcado) viewModel.retoma = if (idBotao == R.id.botaoAmanha) Avaria.RETOMA_DIA else Avaria.RETOMA_TURNO
        }
        binding.editNota.doAfterTextChanged {
            viewModel.nota = it?.toString() ?: ""
            if (viewModel.notaValida()) binding.campoNota.error = null
        }

        // O botão está sempre ativo: ao tocar, mostra o que falta (mensagens de erro claras).
        binding.botaoGuardar.setOnClickListener {
            binding.textoAjuda.isVisible = !viewModel.escolhasFeitas()
            binding.campoNota.error = if (viewModel.notaValida()) null else getString(R.string.erro_nota)
            viewModel.guardar(avariaId, tecnico)
        }
        viewModel.guardado.observe(viewLifecycleOwner) { guardado ->
            if (guardado) {
                mostrarMensagem(R.string.intervencao_em_progresso)
                findNavController().popBackStack()
            }
        }
        mostrarErrosDe(viewModel)
    }

    /** Cria um chip por opção; [escolhida] volta a ficar marcada depois de rodar o ecrã. */
    private fun criarOpcoes(grupo: ChipGroup, opcoes: Array<String>, escolhida: String?, aoEscolher: (String?) -> Unit) {
        val opcaoPorChip = mutableMapOf<Int, String>()
        for (opcao in opcoes) {
            val chip = ItemChipOpcaoBinding.inflate(layoutInflater, grupo, false).root
            chip.id = View.generateViewId()
            chip.text = opcao
            opcaoPorChip[chip.id] = opcao
            grupo.addView(chip)
            if (opcao == escolhida) grupo.check(chip.id)
        }
        grupo.setOnCheckedStateChangeListener { _, ids -> aoEscolher(ids.firstOrNull()?.let { opcaoPorChip[it] }) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_AVARIA_ID = "avariaId"
    }
}
