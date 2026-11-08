package pt.ubi.di.pdm.finsafix.ui.pedidos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.databinding.FragmentPedidosBinding
import pt.ubi.di.pdm.finsafix.ui.avisos.AvisoAdapter
import pt.ubi.di.pdm.finsafix.ui.avisos.AvisoViewModel
import androidx.fragment.app.viewModels
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Registo
import pt.ubi.di.pdm.finsafix.data.RegistoResumo
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.pedirTexto
import pt.ubi.di.pdm.finsafix.ui.lista.Acao
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.ui.comum.redesenharACada30s
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** Chefe de secção: os avisos da secção à espera de resposta, os mais antigos primeiro. */
class PedidosFragment : Fragment() {

    private var _binding: FragmentPedidosBinding? = null
    private val binding get() = _binding!!

    // O mesmo ViewModel do banner (da Activity): já sabe o posto e responde aos avisos.
    private val avisos: AvisoViewModel by activityViewModels()
    private val pedidos: PedidosViewModel by viewModels()
    private val adapterAjuda = LinhaAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPedidosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val chefe = pessoaOuVoltarAEntrada() ?: return

        val adapter = AvisoAdapter { aviso -> avisos.responder(aviso, chefe) }
        binding.listaPedidos.adapter = adapter
        binding.listaPedidos.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        avisos.porResponder.observe(viewLifecycleOwner) { lista ->
            adapter.submitList(lista)
            binding.textoSemPedidos.isVisible = lista.isEmpty()
        }

        // Pedidos de ajuda dos operadores: o chefe responde com uma frase feita (ou outra) e fecha o pedido.
        pedidos.definirPosto(chefe.postoId ?: return)
        binding.listaAjuda.adapter = adapterAjuda
        binding.listaAjuda.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        pedidos.pedidos.observe(viewLifecycleOwner) { lista ->
            val emAberto = lista.filter {
                it.registo.tipo == Registo.AJUDA && (it.registo.estado == Registo.ABERTO || it.registo.estado == Registo.A_CAMINHO)
            }
            adapterAjuda.submitList(emAberto.map { resumo -> EstiloRegisto.linhaDePedido(requireContext(), resumo, acoesDoChefe(resumo, chefe)) })
            binding.textoSemAjuda.isVisible = emAberto.isEmpty()
        }
        mostrarErrosDe(pedidos)

        redesenharACada30s(adapter) // o destaque dos 3 minutos depende só da hora
        mostrarErrosDe(avisos)
    }

    private fun acoesDoChefe(resumo: RegistoResumo, chefe: Pessoa): List<Acao> {
        val id = resumo.registo.id
        val respostas = listOf(R.string.pedido_resp_ja, R.string.pedido_resp_10, R.string.pedido_resp_qualidade, R.string.pedido_resp_avancar)
        return respostas.map { resposta -> Acao(getString(resposta)) { pedidos.responder(id, getString(resposta), chefe) } } + listOf(
            Acao(getString(R.string.pedido_resp_outra)) {
                pedirTexto(
                    R.string.pedido_resposta_titulo, R.string.pedido_resposta_dica, R.string.botao_enviar,
                    validar = { if (it.isBlank()) getString(R.string.erro_resposta_pedido) else null }
                ) { texto -> pedidos.responder(id, texto, chefe) }
            },
            Acao(getString(R.string.pedido_fechar)) { pedidos.fechar(id, chefe) }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
