package pt.ubi.di.pdm.finsafix.ui.formulario

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.google.android.material.color.MaterialColors
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.ui.comum.escolherSecao
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.databinding.DialogFormularioBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemChipOpcaoBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemEquipamentoEscolhaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.JanelaOuFolha
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarMensagem
import pt.ubi.di.pdm.finsafix.util.Validacao
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/**
 * Reportar avaria em 5 passos, numa janela (tablet) ou folha (telemóvel). Argumento "avariaId":
 * ausente para uma avaria nova, ou o id de uma avaria pendente para a editar.
 */
class FormularioDialogo : JanelaOuFolha() {

    private var _binding: DialogFormularioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FormularioViewModel by viewModels()
    private var aEditar = false

    // Os chips de sintoma são criados no código; guarda-se o que cada um representa.
    private val sintomaPorChip = mutableMapOf<Int, String>()
    private var equipamentos: List<Equipamento> = emptyList()
    private var paradas: Set<String> = emptySet()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogFormularioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return dismiss()

        val avariaId = arguments?.getLong(ARG_AVARIA_ID, FormularioViewModel.SEM_AVARIA) ?: FormularioViewModel.SEM_AVARIA
        aEditar = avariaId != FormularioViewModel.SEM_AVARIA
        viewModel.carregar(avariaId)

        for (sintoma in resources.getStringArray(R.array.sintomas)) adicionarChip(sintoma)
        ligarRespostas()

        binding.botaoAnterior.setOnClickListener { voltar() }
        binding.botaoSeguinte.setOnClickListener {
            if (viewModel.passo.value == FormularioViewModel.TOTAL_PASSOS) viewModel.guardar(pessoa)
            else viewModel.seguinte()
        }
        binding.botaoAlterar.setOnClickListener { viewModel.irParaPasso(1) }
        // O botão "voltar" do sistema recua um passo em vez de fechar logo a janela.
        (requireDialog() as ComponentDialog).onBackPressedDispatcher.addCallback(viewLifecycleOwner) { voltar() }

        viewModel.equipamentos.observe(viewLifecycleOwner) {
            equipamentos = it
            criarCartoesDeEquipamento()
        }
        viewModel.paradas.observe(viewLifecycleOwner) {
            paradas = it
            criarCartoesDeEquipamento()
        }
        viewModel.secoes.observe(viewLifecycleOwner) { }
        viewModel.passo.observe(viewLifecycleOwner) { mostrarPasso(it) }
        viewModel.rascunho.observe(viewLifecycleOwner) { mostrarRascunho(it) }
        viewModel.guardado.observe(viewLifecycleOwner) { guardado ->
            if (guardado) {
                mostrarMensagem(if (aEditar) R.string.alteracoes_guardadas else R.string.avaria_reportada)
                dismiss()
            }
        }
        mostrarErrosDe(viewModel)
    }

    /** Cada toque passa a resposta ao ViewModel; o ecrã é depois atualizado a partir do rascunho. */
    private fun ligarRespostas() {
        binding.grupoTipo.addOnButtonCheckedListener { _, idBotao, marcado ->
            if (!marcado) return@addOnButtonCheckedListener
            viewModel.escolherEspecialidade(
                when (idBotao) {
                    R.id.botaoMecanica -> Avaria.MECANICA
                    R.id.botaoEletrica -> Avaria.ELETRICA
                    else -> Avaria.NAO_SEI
                }
            )
        }
        binding.grupoSintomas.setOnCheckedStateChangeListener { _, ids ->
            viewModel.escolherSintoma(ids.firstOrNull()?.let { sintomaPorChip[it] })
        }
        binding.editDescricao.doAfterTextChanged { viewModel.escreverDescricao(it?.toString() ?: "") }
        binding.grupoUrgencia.addOnButtonCheckedListener { _, idBotao, marcado ->
            if (!marcado) return@addOnButtonCheckedListener
            viewModel.escolherPrioridade(
                when (idBotao) {
                    R.id.botaoCritica -> Avaria.CRITICA
                    R.id.botaoMedia -> Avaria.MEDIA
                    else -> Avaria.BAIXA
                }
            )
        }
        binding.grupoParou.addOnButtonCheckedListener { _, idBotao, marcado ->
            if (marcado) viewModel.escolherMaquinaParou(idBotao == R.id.botaoParouSim)
        }
    }

    private fun voltar() {
        if (!viewModel.anterior()) dismiss()
    }

    /** Um cartão por equipamento (2 colunas, com o estado) e o último, “Schelling · Outra secção”. */
    private fun criarCartoesDeEquipamento() {
        val grelha = binding.grelhaEquipamentos
        grelha.removeAllViews()
        for (equipamento in equipamentos) {
            val cartao = ItemEquipamentoEscolhaBinding.inflate(layoutInflater, grelha, false)
            cartao.textoNome.text = equipamento.nome
            val parada = equipamento.id in paradas
            EstiloAvaria.aplicar(
                cartao.textoEstado,
                if (parada) EstiloAvaria.Estilo(R.string.equipamento_ja_parada, R.drawable.ic_stop_x, R.color.if_error_text)
                else EstiloAvaria.Estilo(R.string.equipamento_a_trabalhar, R.drawable.ic_check_circle, R.color.if_ok_text)
            )
            cartao.root.tag = equipamento.id
            cartao.root.setOnClickListener { viewModel.escolherEquipamento(equipamento.id) }
            grelha.addView(cartao.root, parametrosDaGrelha())
        }
        val outra = ItemEquipamentoEscolhaBinding.inflate(layoutInflater, grelha, false)
        outra.textoNome.text = getString(R.string.equipamento_outra_secao, viewModel.secaoAtual.value?.nome ?: "")
        outra.textoEstado.isVisible = false
        outra.root.isCheckable = false
        outra.root.setOnClickListener { escolherOutraSecao() }
        grelha.addView(outra.root, parametrosDaGrelha())
        viewModel.rascunho.value?.let { mostrarRascunho(it) }
    }

    private fun parametrosDaGrelha() = GridLayout.LayoutParams(
        GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
    ).apply { width = 0 }

    private fun escolherOutraSecao() {
        val secoes: List<Secao> = viewModel.secoes.value.orEmpty().filter { it.tipo == Secao.TIPO_SECAO }
        escolherSecao(getString(R.string.outra_secao_titulo), secoes) { it?.let(viewModel::escolherSecao) }
    }

    private fun adicionarChip(sintoma: String) {
        val chip = ItemChipOpcaoBinding.inflate(layoutInflater, binding.grupoSintomas, false).root
        chip.id = View.generateViewId()
        chip.text = sintoma
        sintomaPorChip[chip.id] = sintoma
        binding.grupoSintomas.addView(chip)
    }

    private fun mostrarPasso(passo: Int) {
        // Barra de 5 segmentos: coloridos até ao passo atual
        val ativo = MaterialColors.getColor(binding.barraSegmentos, androidx.appcompat.R.attr.colorPrimary)
        val inativo = MaterialColors.getColor(binding.barraSegmentos, com.google.android.material.R.attr.colorOutlineVariant)
        for (i in 0 until binding.barraSegmentos.childCount) {
            binding.barraSegmentos.getChildAt(i).setBackgroundColor(if (i < passo) ativo else inativo)
        }
        binding.textoPasso.text = getString(R.string.formulario_passo, passo, FormularioViewModel.TOTAL_PASSOS)
        binding.textoPergunta.setText(PERGUNTAS[passo - 1])

        binding.grelhaEquipamentos.isVisible = passo == 1
        binding.grupoTipo.isVisible = passo == 2
        binding.passoSintoma.isVisible = passo == 3
        binding.passoUrgencia.isVisible = passo == 4
        binding.cartaoResumo.isVisible = passo == 5

        binding.botaoAnterior.setText(if (passo == 1) R.string.botao_cancelar else R.string.botao_anterior)
        binding.botaoSeguinte.setText(
            when {
                passo < FormularioViewModel.TOTAL_PASSOS -> R.string.botao_seguinte
                aEditar -> R.string.botao_guardar_alteracoes
                else -> R.string.botao_enviar
            }
        )
        viewModel.rascunho.value?.let { mostrarRascunho(it) }
    }

    /** Mostra as respostas, o que falta responder e ativa o "Seguinte" só quando o passo está completo. */
    private fun mostrarRascunho(rascunho: Rascunho) {
        // Marca as escolhas (útil na edição e ao rodar o ecrã)
        for (i in 0 until binding.grelhaEquipamentos.childCount) {
            val cartao = binding.grelhaEquipamentos.getChildAt(i) as com.google.android.material.card.MaterialCardView
            cartao.isChecked = cartao.tag == rascunho.equipamentoId
        }
        when (rascunho.especialidade) {
            Avaria.MECANICA -> binding.grupoTipo.check(R.id.botaoMecanica)
            Avaria.ELETRICA -> binding.grupoTipo.check(R.id.botaoEletrica)
            Avaria.NAO_SEI -> binding.grupoTipo.check(R.id.botaoNaoSei)
        }
        rascunho.sintoma?.let { sintoma ->
            // Na edição, o título pode não ser um dos sintomas da lista: acrescenta-se como mais uma opção.
            if (sintoma !in sintomaPorChip.values) adicionarChip(sintoma)
            sintomaPorChip.entries.find { it.value == sintoma }?.let { binding.grupoSintomas.check(it.key) }
        }
        if (binding.editDescricao.text.toString() != rascunho.descricao) {
            binding.editDescricao.setText(rascunho.descricao)
        }
        when (rascunho.prioridade) {
            Avaria.BAIXA -> binding.grupoUrgencia.check(R.id.botaoBaixa)
            Avaria.MEDIA -> binding.grupoUrgencia.check(R.id.botaoMedia)
            Avaria.CRITICA -> binding.grupoUrgencia.check(R.id.botaoCritica)
        }
        // “A máquina parou?” só faz sentido numa avaria nova e crítica
        binding.perguntaParou.isVisible = rascunho.prioridade == Avaria.CRITICA && !aEditar
        binding.grupoParou.check(if (rascunho.maquinaParou) R.id.botaoParouSim else R.id.botaoParouNao)

        binding.campoDescricao.error =
            if (Validacao.descricaoValida(rascunho.descricao)) null else getString(R.string.erro_descricao)

        val passo = viewModel.passo.value ?: 1
        val valido = viewModel.passoValido(passo, rascunho)
        binding.botaoSeguinte.isEnabled = valido
        // No passo 3, com o sintoma escolhido, o único erro possível já aparece no campo da descrição.
        val ajuda = AJUDAS.getOrNull(passo - 1)
        binding.textoAjuda.isVisible = !valido && ajuda != null && !(passo == 3 && rascunho.sintoma != null)
        ajuda?.let { binding.textoAjuda.setText(it) }
        if (passo == FormularioViewModel.TOTAL_PASSOS) binding.textoResumo.text = resumo(rascunho)
    }

    private fun resumo(rascunho: Rascunho): String {
        val equipamento = equipamentos.find { it.id == rascunho.equipamentoId }?.nome ?: ""
        return getString(
            R.string.formulario_resumo,
            equipamento,
            rascunho.especialidade?.let { getString(EstiloAvaria.especialidade(it)) } ?: "",
            rascunho.sintoma ?: "",
            rascunho.descricao.ifBlank { getString(R.string.sem_descricao) },
            rascunho.prioridade?.let { getString(EstiloAvaria.prioridade(it)) } ?: ""
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_AVARIA_ID = "avariaId"

        /** Reportar (sem argumento) ou editar (com o id de uma avaria pendente). */
        fun novo(avariaId: Long = FormularioViewModel.SEM_AVARIA) = FormularioDialogo().apply {
            arguments = Bundle().apply { putLong(ARG_AVARIA_ID, avariaId) }
        }

        private val PERGUNTAS = listOf(
            R.string.pergunta_equipamento,
            R.string.pergunta_tipo,
            R.string.pergunta_sintoma,
            R.string.pergunta_urgencia,
            R.string.pergunta_confirmar
        )

        // Mensagens da secção 7 da especificação, mostradas enquanto falta a resposta.
        private val AJUDAS = listOf(
            R.string.erro_equipamento,
            R.string.erro_tipo,
            R.string.erro_sintoma,
            R.string.erro_prioridade
        )
    }
}
