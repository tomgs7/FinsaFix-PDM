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
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.OrdemResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.databinding.FragmentListaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.lista.Acao
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.ui.producao.ProducaoFragment
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Numeros
import pt.ubi.di.pdm.finsafix.util.Posto
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

class PlanoViewModel(application: Application) : ViewModelBase(application) {

    private val secaoId = Posto.obterId(application)
    val ordens: LiveData<List<OrdemResumo>> = repositorio.ordensDoTurno(secaoId, Datas.turnoAtual()).paraLiveData(emptyList())
    val secao = repositorio.secao(secaoId).paraLiveData(null)

    fun guardar(ajustes: Map<Long, Double>, chefe: Pessoa) {
        executar { avisar(if (repositorio.ajustarPlano(ajustes, chefe)) R.string.plano_guardado else R.string.plano_sem_ajustes) }
    }
}

/** “Plano” do chefe de secção: as ordens do turno em curso, ajuste −5 −1 +1 +5 e “Guardar ajustes”. */
class PlanoFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlanoViewModel by viewModels()
    private val adapter = LinhaAdapter()
    private val ajustes = mutableMapOf<Long, Double>() // ainda não guardados

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val chefe = pessoaOuVoltarAEntrada() ?: return
        binding.filtros.isVisible = false
        binding.textoVazio.setText(R.string.plano_vazio)
        binding.botaoAcao.setText(R.string.plano_guardar)
        binding.botaoAcao.isVisible = true
        binding.botaoAcao.setOnClickListener {
            viewModel.guardar(ajustes.toMap(), chefe)
            ajustes.clear()
        }
        binding.lista.adapter = adapter
        binding.lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.ordens.observe(viewLifecycleOwner) { mostrar(it) }
        viewModel.secao.observe(viewLifecycleOwner) { viewModel.ordens.value?.let { mostrar(it) } }
        mostrarErrosDe(viewModel)
    }

    private fun mostrar(ordens: List<OrdemResumo>) {
        val unidade = getString(ProducaoFragment.nomeDaUnidade(viewModel.secao.value?.unidade ?: ""))
        val total = ordens.sumOf { it.ordem.planeado + (ajustes[it.ordem.id] ?: 0.0) }
        binding.textoIntro.text = getString(R.string.plano_intro, getString(Datas.nomeTurno(Datas.turnoAtual())).lowercase())
        binding.textoResumo.text = getString(R.string.plano_total, Numeros.formatar(total, 0), unidade)
        val registado = ordens.mapNotNull { o -> o.ajustadaPorNome?.let { it to (o.ordem.dataAjuste ?: 0) } }.maxByOrNull { it.second }
        adapter.submitList(ordens.map { resumo ->
            val id = resumo.ordem.id
            fun passo(valor: Double) = Acao(if (valor > 0) "+${valor.toInt()}" else "−${-valor.toInt()}") {
                ajustes[id] = (ajustes[id] ?: 0.0) + valor
                mostrar(ordens)
            }
            Linha(
                id = id.toString(),
                titulo = getString(R.string.plano_ordem, resumo.equipamentoNome, Numeros.formatar(resumo.ordem.planeado + (ajustes[id] ?: 0.0), 0), unidade),
                sub = registado?.takeIf { resumo.ordem.dataAjuste != null }?.let { getString(R.string.plano_ajustes_registados, resumo.ajustadaPorNome ?: "", Datas.hora(resumo.ordem.dataAjuste ?: 0)) },
                acoes = listOf(passo(-5.0), passo(-1.0), passo(1.0), passo(5.0))
            )
        })
        binding.textoVazio.isVisible = ordens.isEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
