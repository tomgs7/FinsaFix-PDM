package pt.ubi.di.pdm.finsafix.ui.preventiva

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
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Preventiva
import pt.ubi.di.pdm.finsafix.data.PreventivaResumo
import pt.ubi.di.pdm.finsafix.databinding.FragmentListaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.lista.Acao
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

class PreventivaViewModel(application: Application) : ViewModelBase(application) {
    val planos: LiveData<List<PreventivaResumo>> = repositorio.preventivas().paraLiveData(emptyList())

    fun registarExecucao(id: Long, quem: Pessoa) {
        executar { avisar(if (repositorio.registarExecucao(id, quem)) R.string.preventiva_registada else R.string.erro_preventiva) }
    }
}

/** “Preventiva” do técnico: os planos da fábrica com o estado de cada um e “Registar execução” nos da sua especialidade. */
class PreventivaFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PreventivaViewModel by viewModels()
    private val adapter = LinhaAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return

        binding.textoIntro.setText(R.string.preventiva_intro)
        binding.filtros.isVisible = false
        binding.textoVazio.setText(R.string.preventiva_vazia)
        binding.lista.adapter = adapter
        binding.lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.planos.observe(viewLifecycleOwner) { lista ->
            val agora = System.currentTimeMillis()
            // Os vencidos primeiro, depois os que estão a vencer
            val ordenada = lista.sortedBy { it.preventiva.estado(agora).ordinal }
            adapter.submitList(ordenada.map { linhaDe(it, pessoa, agora) })
            binding.textoVazio.isVisible = lista.isEmpty()
            val vencidas = lista.count { it.preventiva.estado(agora) == Preventiva.Estado.VENCIDA }
            val aVencer = lista.count { it.preventiva.estado(agora) == Preventiva.Estado.A_VENCER }
            binding.textoResumo.text = getString(R.string.preventiva_resumo, vencidas, aVencer, lista.size - vencidas - aVencer)
        }
        mostrarErrosDe(viewModel)
    }

    private fun linhaDe(resumo: PreventivaResumo, pessoa: Pessoa, agora: Long): Linha {
        val plano = resumo.preventiva
        val estado = when (plano.estado(agora)) {
            Preventiva.Estado.VENCIDA -> EstiloAvaria.Estilo(R.string.preventiva_vencida, R.drawable.ic_alert, R.color.if_error_text)
            Preventiva.Estado.A_VENCER -> EstiloAvaria.Estilo(R.string.preventiva_a_vencer, R.drawable.ic_clock, R.color.if_warn_text)
            Preventiva.Estado.EM_DIA -> EstiloAvaria.Estilo(R.string.preventiva_em_dia, R.drawable.ic_check_circle, R.color.if_ok_text)
        }
        val ultima = getString(
            R.string.preventiva_ultima, Datas.dataHora(plano.ultimaData),
            plano.horasDesdeUltima(agora), plano.intervaloH
        )
        val acoes = if (plano.especialidade == pessoa.especialidade) {
            listOf(Acao(getString(R.string.preventiva_registar)) { viewModel.registarExecucao(plano.id, pessoa) })
        } else emptyList()
        return Linha(
            id = plano.id.toString(),
            estado = estado,
            titulo = plano.titulo,
            sub = listOf(resumo.equipamentoNome, resumo.secaoNome).joinToString(EstiloAvaria.SEPARADOR),
            extra = ultima + (resumo.ultimaPorNome?.let { " · $it" } ?: ""),
            acoes = acoes
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
