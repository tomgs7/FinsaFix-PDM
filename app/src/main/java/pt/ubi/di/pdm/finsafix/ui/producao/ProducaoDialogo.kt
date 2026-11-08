package pt.ubi.di.pdm.finsafix.ui.producao

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.ui.comum.escolherSecao
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.databinding.DialogProducaoBinding
import pt.ubi.di.pdm.finsafix.ui.comum.JanelaOuFolha
import pt.ubi.di.pdm.finsafix.ui.comum.pedirNumero
import pt.ubi.di.pdm.finsafix.ui.producao.ProducaoFragment.Companion.decimaisDe
import pt.ubi.di.pdm.finsafix.ui.producao.ProducaoFragment.Companion.nomeDaUnidade
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Numeros
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** Registar produção numa janela (tablet) ou folha (telemóvel): equipamento, turno, quantidade pelo teclado e confirmar. */
class ProducaoDialogo : JanelaOuFolha() {

    private var _binding: DialogProducaoBinding? = null
    private val binding get() = _binding!!

    // O ViewModel é o do ecrã Produção, que abriu a janela
    private val viewModel: ProducaoViewModel by viewModels({ requireParentFragment() })
    private val equipamentoPorBotao = mutableMapOf<Int, Equipamento>()
    private var unidade = ""
    private var unidadeCodigo = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogProducaoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return dismiss()

        binding.grupoEquipamentos.setOnCheckedChangeListener { _, id ->
            viewModel.equipamentoId = equipamentoPorBotao[id]?.id
            binding.textoErro.isVisible = false
        }
        binding.grupoTurno.check(BOTAO_DO_TURNO.getValue(viewModel.turno))
        binding.grupoTurno.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) viewModel.turno = BOTAO_DO_TURNO.entries.first { it.value == id }.key
        }
        binding.campoQuantidade.setOnClickListener {
            pedirNumero(getString(R.string.producao_quantidade), unidade, decimaisDe(unidadeCodigo), info = { "" }) {
                viewModel.quantidade = it
                mostrarQuantidade()
                // Nos tabuleiros pergunta-se também os cortes (pode cancelar: é opcional)
                if (unidadeCodigo in listOf("tab", "tabc")) {
                    pedirNumero(getString(R.string.producao_cortes), getString(R.string.producao_cortes_unidade), 0, info = { getString(R.string.producao_cortes_opcional) }) { cortes ->
                        viewModel.cortes = cortes.toInt()
                    }
                }
            }
        }
        binding.botaoRegistar.setOnClickListener { confirmar(pessoa) }
        binding.botaoOutraSecao.setOnClickListener { escolherOutraSecao() }

        viewModel.secao.observe(viewLifecycleOwner) { secao ->
            unidadeCodigo = secao?.unidade ?: ""
            unidade = getString(nomeDaUnidade(unidadeCodigo))
            mostrarQuantidade()
        }
        viewModel.equipamentos.observe(viewLifecycleOwner) { criarBotoes(it) }
        viewModel.secoes.observe(viewLifecycleOwner) { }
    }

    private fun criarBotoes(equipamentos: List<Equipamento>) {
        binding.grupoEquipamentos.removeAllViews()
        equipamentoPorBotao.clear()
        for (equipamento in equipamentos) {
            val botao = MaterialRadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = equipamento.nome
                minHeight = resources.getDimensionPixelSize(R.dimen.if_touch_min)
                setTextAppearance(R.style.TextAppearance_FinsaFix_Body)
            }
            equipamentoPorBotao[botao.id] = equipamento
            binding.grupoEquipamentos.addView(botao)
            if (equipamento.id == viewModel.equipamentoId) binding.grupoEquipamentos.check(botao.id)
        }
    }

    private fun mostrarQuantidade() {
        val binding = _binding ?: return
        binding.textoQuantidade.text = viewModel.quantidade?.let { Numeros.formatar(it, decimaisDe(unidadeCodigo)) }
            ?: getString(R.string.valor_vazio)
        binding.textoUnidade.text = unidade
    }

    /** “Registar noutra secção”: escolhe a secção; os equipamentos passam a ser os dela. */
    private fun escolherOutraSecao() {
        val secoes = viewModel.secoes.value.orEmpty().filter { it.tipo == Secao.TIPO_SECAO }
        escolherSecao(getString(R.string.outra_secao_titulo), secoes) { it?.let { s -> viewModel.escolherSecao(s.id) } }
    }

    /** Valida (mostra o que falta) e pede confirmação antes de gravar; depois fecha a janela. */
    private fun confirmar(pessoa: Pessoa) {
        val falta = viewModel.oQueFalta()
        binding.textoErro.isVisible = falta != null
        if (falta != null) {
            binding.textoErro.setText(falta)
            return
        }
        val equipamento = equipamentoPorBotao.values.first { it.id == viewModel.equipamentoId }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.producao_confirmar_titulo)
            .setMessage(
                getString(
                    R.string.producao_confirmar_mensagem,
                    Numeros.formatar(viewModel.quantidade!!, decimaisDe(unidadeCodigo)), unidade,
                    equipamento.nome, getString(Datas.nomeTurno(viewModel.turno))
                )
            )
            .setPositiveButton(R.string.producao_registar) { _, _ ->
                viewModel.registar(pessoa)
                dismiss()
            }
            .setNegativeButton(R.string.botao_cancelar, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        val BOTAO_DO_TURNO = mapOf("M" to R.id.botaoManha, "T" to R.id.botaoTarde, "N" to R.id.botaoNoite)
    }
}
