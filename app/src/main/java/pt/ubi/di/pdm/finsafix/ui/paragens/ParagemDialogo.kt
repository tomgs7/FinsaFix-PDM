package pt.ubi.di.pdm.finsafix.ui.paragens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.radiobutton.MaterialRadioButton
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.databinding.DialogParagemBinding
import pt.ubi.di.pdm.finsafix.ui.comum.JanelaOuFolha

/** “Porque parou?”: equipamento e causa (as duas são obrigatórias). Usa o ViewModel do ecrã Paragens, que o abriu. */
class ParagemDialogo : JanelaOuFolha() {

    private var _binding: DialogParagemBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParagensViewModel by viewModels({ requireParentFragment() })

    private val equipamentoPorBotao = mutableMapOf<Int, Equipamento>()
    private val causaPorBotao = mutableMapOf<Int, String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogParagemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        for (causa in resources.getStringArray(R.array.causas_paragem)) {
            val botao = novoBotao(causa)
            causaPorBotao[botao.id] = causa
            binding.grupoCausas.addView(botao)
        }
        viewModel.equipamentos.observe(viewLifecycleOwner) { equipamentos ->
            binding.grupoEquipamentos.removeAllViews()
            equipamentoPorBotao.clear()
            for (equipamento in equipamentos) {
                val botao = novoBotao(equipamento.nome)
                equipamentoPorBotao[botao.id] = equipamento
                binding.grupoEquipamentos.addView(botao)
            }
        }
        binding.grupoEquipamentos.setOnCheckedChangeListener { _, _ -> atualizarBotao() }
        binding.grupoCausas.setOnCheckedChangeListener { _, _ -> atualizarBotao() }
        binding.botaoCancelar.setOnClickListener { dismiss() }
        binding.botaoIniciar.setOnClickListener {
            val equipamento = equipamentoPorBotao[binding.grupoEquipamentos.checkedRadioButtonId] ?: return@setOnClickListener
            val causa = causaPorBotao[binding.grupoCausas.checkedRadioButtonId] ?: return@setOnClickListener
            viewModel.registar(equipamento.id, causa)
            dismiss()
        }
        atualizarBotao()
    }

    private fun novoBotao(texto: String) = MaterialRadioButton(requireContext()).apply {
        id = View.generateViewId()
        text = texto
        minHeight = resources.getDimensionPixelSize(R.dimen.if_touch_min)
        setTextAppearance(R.style.TextAppearance_FinsaFix_Body)
    }

    private fun atualizarBotao() {
        binding.botaoIniciar.isEnabled =
            binding.grupoEquipamentos.checkedRadioButtonId in equipamentoPorBotao && binding.grupoCausas.checkedRadioButtonId in causaPorBotao
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
