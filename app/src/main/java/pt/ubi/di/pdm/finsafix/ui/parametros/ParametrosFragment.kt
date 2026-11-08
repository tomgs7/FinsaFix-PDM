package pt.ubi.di.pdm.finsafix.ui.parametros

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.ui.comum.escolherSecao
import pt.ubi.di.pdm.finsafix.data.LeituraResumo
import pt.ubi.di.pdm.finsafix.data.Parametro
import pt.ubi.di.pdm.finsafix.data.ParametroComLeitura
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.databinding.FragmentParametrosBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.confirmar
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarMensagem
import pt.ubi.di.pdm.finsafix.ui.comum.pedirNumero
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Numeros
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** Controlo do turno › Parâmetros: o operador regista o que lê no painel; a app nunca altera a máquina. */
class ParametrosFragment : Fragment() {

    private var _binding: FragmentParametrosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParametrosViewModel by viewModels()
    private val ultimas = LinhaAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParametrosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return

        val adapter = ParametroAdapter(aoTocar = { pedirValor(it, pessoa) }, aoAvisarChefe = { avisarChefe(it, pessoa) })
        // 1 coluna no telemóvel, 2 no tablet (values-sw600dp/integers.xml).
        binding.listaParametros.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.colunas_parametros))
        binding.listaParametros.adapter = adapter

        binding.listaUltimas.adapter = ultimas
        binding.botaoOutraSecao.setOnClickListener { escolherOutraSecao() }
        binding.botaoChecklist.setOnClickListener { findNavController().navigate(R.id.checklistFragment) }
        viewModel.secoes.observe(viewLifecycleOwner) { mostrarTitulo() }
        viewModel.secaoAtual.observe(viewLifecycleOwner) { mostrarTitulo() }
        viewModel.parametros.observe(viewLifecycleOwner) { parametros ->
            adapter.submitList(parametros)
            binding.textoSemParametros.isVisible = parametros.isEmpty()
            mostrarResumo(parametros)
        }
        viewModel.ultimas.observe(viewLifecycleOwner) { lista ->
            ultimas.submitList(lista.map { linhaDeLeitura(it) })
            binding.textoSemUltimas.isVisible = lista.isEmpty()
        }
        viewModel.mensagem.observe(viewLifecycleOwner) { mensagem ->
            if (mensagem != null) {
                mostrarMensagem(mensagem)
                viewModel.mensagemMostrada()
            }
        }
        mostrarErrosDe(viewModel)
    }

    /** Teclado numérico; a linha de informação mostra o limite e o estado do valor enquanto se escreve. */
    /** “Leituras do turno · Schelling” */
    private fun mostrarTitulo() {
        val nome = viewModel.secoes.value?.find { it.id == viewModel.secaoAtual.value }?.nome ?: ""
        binding.textoTituloLeituras.text = getString(R.string.parametros_titulo, nome)
    }

    /** “2 dentro do limite · 1 perto · 0 fora” */
    private fun mostrarResumo(parametros: List<ParametroComLeitura>) {
        val estados = parametros.mapNotNull { item -> item.valor?.let { item.parametro.estado(it) } }
        binding.textoResumoParametros.text = getString(
            R.string.parametros_resumo,
            estados.count { it == Parametro.Estado.DENTRO }, estados.count { it == Parametro.Estado.PERTO }, estados.count { it == Parametro.Estado.FORA }
        )
    }

    private fun linhaDeLeitura(resumo: LeituraResumo) = Linha(
        id = resumo.leitura.id.toString(),
        titulo = getString(
            R.string.parametros_leitura, resumo.parametroNome, Numeros.formatar(resumo.leitura.valor, resumo.decimais), resumo.unidade
        ),
        sub = getString(R.string.registado_por, resumo.pessoaNome, Datas.hora(resumo.leitura.data))
    )

    private fun escolherOutraSecao() {
        val secoes = viewModel.secoes.value.orEmpty().filter { it.tipo == Secao.TIPO_SECAO }
        escolherSecao(getString(R.string.outra_secao_titulo), secoes) { it?.let { s -> viewModel.escolherSecao(s.id) } }
    }

    private fun pedirValor(p: Parametro, pessoa: Pessoa) {
        val limite = ParametroAdapter.descreverLimite(requireContext(), p)
        pedirNumero(
            titulo = p.nome,
            unidade = p.unidade,
            decimais = p.decimais,
            permitirNegativo = p.minimo < 0, // ex.: desvio de espessura, de −0,15 a 0,15 mm
            info = { valor ->
                if (valor == null) limite
                else getString(R.string.limite_e_estado, limite, getString(EstiloAvaria.estadoParametro(p.estado(valor)).texto))
            }
        ) { valor ->
            if (p.improvavel(valor)) confirmarImprovavel(p, valor, pessoa) else viewModel.registar(p.id, valor, pessoa)
        }
    }

    /** O aviso fica na lista de avisos da secção, para o chefe o ver e responder. */
    private fun avisarChefe(item: ParametroComLeitura, pessoa: Pessoa) {
        val p = item.parametro
        val texto = getString(R.string.aviso_texto_parametro, p.nome, Numeros.formatar(item.valor ?: return, p.decimais), p.unidade)
        confirmar(R.string.avisar_chefe_titulo, getString(R.string.avisar_chefe_mensagem, texto), R.string.aviso_enviar) {
            viewModel.avisarChefe(texto, pessoa)
        }
    }

    /** Um valor muito longe dos limites quase sempre é um erro de escrita: pede-se confirmação. */
    private fun confirmarImprovavel(p: Parametro, valor: Double, pessoa: Pessoa) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.improvavel_titulo)
            .setMessage(getString(R.string.improvavel_mensagem, Numeros.formatar(valor, p.decimais), p.unidade))
            .setPositiveButton(R.string.botao_esta_correto) { _, _ -> viewModel.registar(p.id, valor, pessoa) }
            .setNegativeButton(R.string.botao_corrigir) { _, _ -> pedirValor(p, pessoa) }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
