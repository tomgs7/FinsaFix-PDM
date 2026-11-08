package pt.ubi.di.pdm.finsafix.ui.consulta

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import java.io.IOException
import java.io.OutputStream
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.databinding.FragmentRelatoriosBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemRelatorioLinhaBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemRelatorioMaisBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemRelatorioNumeroBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemRelatorioTituloBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.escolherSecao
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarMensagem
import pt.ubi.di.pdm.finsafix.util.Relatorio
import pt.ubi.di.pdm.finsafix.util.Relatorios
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** “Relatórios” da engenharia: configurar, pré-visualizar e exportar em PDF ou CSV (escolhe-se onde guardar; sem permissões). */
class RelatoriosFragment : Fragment() {

    private var _binding: FragmentRelatoriosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RelatoriosViewModel by viewModels()

    // O sistema pergunta onde guardar o ficheiro e devolve o endereço (não é preciso nenhuma permissão)
    private val guardarCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { destino ->
        escrever(destino) { saida, relatorio -> saida.write(Relatorios.paraCsv(relatorio).toByteArray(Charsets.UTF_8)) }
    }
    private val guardarPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destino ->
        escrever(destino) { saida, relatorio -> Relatorios.escreverPdf(getString(R.string.app_name), relatorio, saida) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRelatoriosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.definirPessoa(pessoaOuVoltarAEntrada() ?: return)
        viewModel.secoes.observe(viewLifecycleOwner) { }
        ligarConfiguracao()
        binding.configurar.botaoPdf.setOnClickListener { guardarPdf.launch("relatorio-finsafix.pdf") }
        binding.configurar.botaoCsv.setOnClickListener { guardarCsv.launch("relatorio-finsafix.csv") }
        viewModel.relatorio.observe(viewLifecycleOwner) { mostrar(it) }
        mostrarErrosDe(viewModel)
    }

    /** Cada opção muda o pedido; o ViewModel monta o relatório outra vez e a pré-visualização atualiza sozinha. */
    private fun ligarConfiguracao() {
        val configurar = binding.configurar
        configurar.periodo.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) viewModel.mudar(viewModel.pedidoAtual.copy(dias = if (id == R.id.botao30dias) 30 else 7))
        }
        configurar.botaoSecao.setOnClickListener { escolherSecao() }
        configurar.checkAvarias.setOnCheckedChangeListener { _, marcada -> viewModel.mudar(viewModel.pedidoAtual.copy(avarias = marcada)) }
        configurar.checkParagens.setOnCheckedChangeListener { _, marcada -> viewModel.mudar(viewModel.pedidoAtual.copy(paragens = marcada)) }
        configurar.checkIndicadores.setOnCheckedChangeListener { _, marcada -> viewModel.mudar(viewModel.pedidoAtual.copy(indicadores = marcada)) }
        configurar.checkPreventivas.setOnCheckedChangeListener { _, marcada -> viewModel.mudar(viewModel.pedidoAtual.copy(preventivas = marcada)) }
    }

    private fun mostrar(relatorio: Relatorio) {
        val previsualizacao = binding.previsualizacao
        binding.configurar.botaoSecao.text = relatorio.secao
        // Sem dados não há nada para exportar
        binding.configurar.botaoPdf.isEnabled = relatorio.temDados
        binding.configurar.botaoCsv.isEnabled = relatorio.temDados
        previsualizacao.estadoVazio.isVisible = !relatorio.temDados
        previsualizacao.folha.isVisible = relatorio.temDados
        if (!relatorio.temDados) return

        previsualizacao.textoTitulo.text = relatorio.titulo
        previsualizacao.textoSecaoPeriodo.text = listOf(relatorio.secao, relatorio.periodo).joinToString(EstiloAvaria.SEPARADOR)
        previsualizacao.textoGerado.text = relatorio.rodape
        mostrarNumeros(relatorio)
        mostrarTabelas(relatorio)
    }

    private fun mostrarNumeros(relatorio: Relatorio) {
        val grelha = binding.previsualizacao.numeros
        grelha.removeAllViews()
        grelha.isVisible = relatorio.numeros.isNotEmpty()
        for ((nome, valor) in relatorio.numeros) {
            val item = ItemRelatorioNumeroBinding.inflate(layoutInflater, grelha, false)
            item.nome.text = nome
            item.valor.text = valor
            grelha.addView(item.root)
        }
    }

    /** Uma tabela curta por secção do relatório: título, cabeçalho e as primeiras linhas (o resto vai no ficheiro). */
    private fun mostrarTabelas(relatorio: Relatorio) {
        val tabelas = binding.previsualizacao.tabelas
        tabelas.removeAllViews()
        val c = relatorio.colunas // Data, ID, Equipamento, Detalhe, Duração
        for (tabela in relatorio.tabelas) {
            tabelas.addView(ItemRelatorioTituloBinding.inflate(layoutInflater, tabelas, false).root.apply { text = tabela.titulo })
            if (tabela.linhas.isEmpty()) continue
            tabelas.addView(linha(c[0], c[2], c[3] + EstiloAvaria.SEPARADOR + c[4], cabecalho = true, par = true))
            tabela.linhas.take(LINHAS_NA_PREVISUALIZACAO).forEachIndexed { i, l ->
                tabelas.addView(linha(l.data, l.equipamento, l.detalhe + EstiloAvaria.SEPARADOR + l.duracao, cabecalho = false, par = i % 2 == 0))
            }
            val restantes = tabela.linhas.size - LINHAS_NA_PREVISUALIZACAO
            if (restantes > 0) {
                val mais = ItemRelatorioMaisBinding.inflate(layoutInflater, tabelas, false).root
                mais.text = resources.getQuantityString(R.plurals.relatorio_e_mais, restantes, restantes)
                tabelas.addView(mais)
            }
        }
    }

    /** Uma linha da tabela; as linhas ímpares têm fundo, para se acompanharem na horizontal. */
    private fun linha(data: String, equipamento: String, detalhe: String, cabecalho: Boolean, par: Boolean): View {
        val item = ItemRelatorioLinhaBinding.inflate(layoutInflater, binding.previsualizacao.tabelas, false)
        for ((texto, campo) in listOf(data to item.data, equipamento to item.equipamento, detalhe to item.detalhe)) {
            campo.text = texto
            if (cabecalho) campo.setTypeface(null, Typeface.BOLD)
        }
        if (!par) item.root.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.if_surface_low))
        return item.root
    }

    private fun escolherSecao() {
        val secoes = viewModel.secoes.value.orEmpty().filter { it.tipo == Secao.TIPO_SECAO }
        escolherSecao(getString(R.string.consulta_escolher_secao), secoes, viewModel.pedidoAtual.secaoId, comTodas = true) {
            viewModel.mudar(viewModel.pedidoAtual.copy(secaoId = it?.id))
        }
    }

    /** Escreve o ficheiro no sítio escolhido; um erro (disco cheio, ficheiro recusado) vira uma mensagem, nunca fecha a app. */
    private fun escrever(destino: Uri?, escrita: (OutputStream, Relatorio) -> Unit) {
        if (destino == null) return // o utilizador cancelou
        try {
            val relatorio = viewModel.relatorio.value ?: throw IOException("Sem relatório")
            requireContext().contentResolver.openOutputStream(destino)?.use { escrita(it, relatorio) } ?: throw IOException("Sem destino")
            mostrarMensagem(R.string.relatorio_exportado)
        } catch (e: Exception) {
            Log.e("FinsaFix", "Erro ao escrever o relatório", e)
            mostrarMensagem(R.string.erro_relatorio)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val LINHAS_NA_PREVISUALIZACAO = 5
    }
}
