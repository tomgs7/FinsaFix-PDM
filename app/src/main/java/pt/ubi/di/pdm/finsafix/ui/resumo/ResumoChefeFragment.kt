package pt.ubi.di.pdm.finsafix.ui.resumo

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import com.google.android.material.divider.MaterialDividerItemDecoration
import kotlinx.coroutines.flow.combine
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.ParagemResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.ProducaoResumo
import pt.ubi.di.pdm.finsafix.databinding.FragmentListaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.confirmar
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.lista.Acao
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.ui.producao.ProducaoFragment
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Numeros
import pt.ubi.di.pdm.finsafix.util.Posto
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** O turno em curso da secção: o que se produziu e as paragens. */
data class DadosDoTurno(val producao: List<ProducaoResumo>, val paragens: List<ParagemResumo>, val unidade: String, val planeado: Double = 0.0)

class ResumoChefeViewModel(application: Application) : ViewModelBase(application) {

    private val secaoId = Posto.obterId(application)

    val dados: LiveData<DadosDoTurno> = combine(
        repositorio.producaoDoTurno(secaoId, Datas.turnoAtual(), Datas.inicioDoTurnoAtual()),
        repositorio.paragensRecentes(secaoId, Datas.inicioDoTurnoAtual()),
        repositorio.secao(secaoId),
        repositorio.ordensDoTurno(secaoId, Datas.turnoAtual())
    ) { producao, paragens, secao, ordens -> DadosDoTurno(producao, paragens, secao?.unidade ?: "", ordens.sumOf { it.ordem.planeado }) }
        .paraLiveData(DadosDoTurno(emptyList(), emptyList(), ""))

    fun anular(id: Long, chefe: Pessoa) {
        executar { if (!repositorio.anularProducao(id, chefe)) avisar(R.string.erro_anular_producao) else avisar(R.string.producao_anulada) }
    }
}

/** “Resumo” do chefe de secção: produzido, paragens e tempo parado do turno, paragens por causa e registos com “Anular”. */
class ResumoChefeFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResumoChefeViewModel by viewModels()
    private val adapter = LinhaAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val chefe = pessoaOuVoltarAEntrada() ?: return
        binding.filtros.isVisible = false
        binding.textoVazio.setText(R.string.resumo_sem_registos)
        binding.lista.adapter = adapter
        binding.lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.dados.observe(viewLifecycleOwner) { mostrar(it, chefe) }
        mostrarErrosDe(viewModel)
    }

    private fun mostrar(dados: DadosDoTurno, chefe: Pessoa) {
        val agora = System.currentTimeMillis()
        val inicio = Datas.inicioDoTurnoAtual()
        val tempoParado = dados.paragens.sumOf { (it.paragem.fim ?: agora) - maxOf(it.paragem.inicio, inicio) }
        val unidade = getString(ProducaoFragment.nomeDaUnidade(dados.unidade))
        val produzido = dados.producao.sumOf { it.producao.quantidade }
        binding.textoResumo.text = buildString {
            append(getString(R.string.resumo_chefe_kpis, Numeros.formatar(produzido, 0), unidade, dados.paragens.size, Datas.duracao(requireContext(), tempoParado)))
            append(getString(R.string.resumo_contra_plano, Numeros.formatar(dados.planeado, 0), if (dados.planeado > 0) (produzido * 100 / dados.planeado).toInt() else 0))
        }
        val porCausa = dados.paragens.groupingBy { it.paragem.causa }.eachCount().entries.joinToString("\n") { "${it.key} · ${it.value}" }
            .ifEmpty { getString(R.string.resumo_sem_paragens) }
        binding.textoIntro.text = getString(R.string.resumo_chefe_intro, porCausa)
        adapter.submitList(dados.producao.map { resumo ->
            Linha(
                id = resumo.producao.id.toString(),
                titulo = getString(R.string.resumo_registo, resumo.equipamentoNome, Numeros.formatar(resumo.producao.quantidade, 0), unidade),
                sub = getString(R.string.registado_por, resumo.pessoaNome, Datas.hora(resumo.producao.data)),
                acoes = listOf(Acao(getString(R.string.resumo_anular)) {
                    confirmar(R.string.resumo_anular_titulo, getString(R.string.resumo_anular_mensagem), R.string.resumo_anular) {
                        viewModel.anular(resumo.producao.id, chefe)
                    }
                })
            )
        })
        binding.textoVazio.isVisible = dados.producao.isEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
