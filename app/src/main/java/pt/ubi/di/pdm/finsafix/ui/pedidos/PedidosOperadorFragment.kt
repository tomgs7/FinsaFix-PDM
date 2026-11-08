package pt.ubi.di.pdm.finsafix.ui.pedidos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Registo
import pt.ubi.di.pdm.finsafix.databinding.DialogPedidoBinding
import pt.ubi.di.pdm.finsafix.databinding.FragmentPedidosOperadorBinding
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.lista.Acao
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** “Pedidos” do operador: pedir ajuda ou material, ver o que está em aberto e confirmar quando foi atendido. */
class PedidosOperadorFragment : Fragment() {

    private var _binding: FragmentPedidosOperadorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PedidosViewModel by viewModels()
    private val abertos = LinhaAdapter()
    private val anteriores = LinhaAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPedidosOperadorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return
        viewModel.definirPosto(pessoa.postoId ?: return)

        for (lista in listOf(binding.listaAbertos, binding.listaAnteriores)) {
            lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        }
        binding.listaAbertos.adapter = abertos
        binding.listaAnteriores.adapter = anteriores
        binding.botaoAjuda.setOnClickListener { novoPedido(Registo.AJUDA, pessoa) }
        binding.botaoMaterial.setOnClickListener { novoPedido(Registo.MATERIAL, pessoa) }

        viewModel.pedidos.observe(viewLifecycleOwner) { lista ->
            val (emAberto, fechados) = lista.partition { it.registo.estado == Registo.ABERTO || it.registo.estado == Registo.A_CAMINHO }
            abertos.submitList(emAberto.map { resumo ->
                val rotulo = if (resumo.registo.tipo == Registo.AJUDA) R.string.pedido_ja_atendido else R.string.pedido_ja_recebido
                // Só quem fez o pedido o confirma.
                val acoes = if (resumo.registo.pessoaId == pessoa.id) listOf(Acao(getString(rotulo)) { viewModel.confirmar(resumo.registo.id, pessoa) }) else emptyList()
                EstiloRegisto.linhaDePedido(requireContext(), resumo, acoes)
            })
            anteriores.submitList(fechados.take(MAX_ANTERIORES).map { EstiloRegisto.linhaDePedido(requireContext(), it, emptyList()) })
            binding.textoSemAbertos.isVisible = emAberto.isEmpty()
            binding.textoSemAnteriores.isVisible = fechados.isEmpty()
        }
        mostrarErrosDe(viewModel)
    }

    /** Janela do pedido: quem chamar (ajuda) ou o material e a quantidade, mais “Urgente”. */
    private fun novoPedido(tipo: String, pessoa: Pessoa) {
        val ajuda = tipo == Registo.AJUDA
        val dialogo = DialogPedidoBinding.inflate(layoutInflater)
        dialogo.destinos.isVisible = ajuda
        dialogo.campoQuantidade.isVisible = !ajuda
        dialogo.campoTexto.setHint(if (ajuda) R.string.pedido_observacao else R.string.pedido_material_dica)
        Registo.DESTINOS.forEachIndexed { indice, destino ->
            dialogo.destinos.addView(RadioButton(requireContext()).apply {
                id = View.generateViewId()
                tag = destino
                text = listOf(getString(EstiloRegisto.destino(destino)), getString(EstiloRegisto.descricaoDestino(destino))).joinToString("\n")
                isChecked = indice == 0
            })
        }
        dialogo.campoTexto.editText?.doAfterTextChanged { dialogo.campoTexto.error = null }
        dialogo.campoQuantidade.editText?.doAfterTextChanged { dialogo.campoQuantidade.error = null }

        val alerta = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (ajuda) R.string.pedido_ajuda_titulo else R.string.pedido_material_titulo)
            .setView(dialogo.root)
            .setPositiveButton(R.string.botao_enviar, null)
            .setNegativeButton(R.string.botao_cancelar, null)
            .create()
        alerta.setOnShowListener {
            alerta.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val texto = dialogo.campoTexto.editText?.text.toString().trim()
                val quantidade = dialogo.campoQuantidade.editText?.text.toString().toIntOrNull()
                val destino = dialogo.destinos.children.filterIsInstance<RadioButton>().firstOrNull { it.isChecked }?.tag as String?
                when {
                    !ajuda && quantidade == null -> dialogo.campoQuantidade.error = getString(R.string.erro_quantidade_pedido)
                    !ajuda && texto.isBlank() -> dialogo.campoTexto.error = getString(R.string.erro_material_pedido)
                    else -> {
                        alerta.dismiss()
                        val conteudo = if (ajuda) texto else getString(R.string.pedido_material_texto, quantidade, texto)
                        viewModel.criar(tipo, destino.takeIf { ajuda }, conteudo, dialogo.interruptorUrgente.isChecked, pessoa)
                    }
                }
            }
        }
        alerta.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MAX_ANTERIORES = 10
    }
}
