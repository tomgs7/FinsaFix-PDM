package pt.ubi.di.pdm.finsafix.ui.consulta

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.google.android.material.divider.MaterialDividerItemDecoration
import kotlinx.coroutines.flow.combine
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.data.Indicadores
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Preventiva
import pt.ubi.di.pdm.finsafix.data.PreventivaResumo
import pt.ubi.di.pdm.finsafix.databinding.FragmentListaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** Os dados do Resumo da oficina: avarias da especialidade e preventivas. */
data class DadosOficina(val avarias: List<AvariaResumo>, val preventivas: List<PreventivaResumo>)

class ResumoOficinaViewModel(application: Application) : ViewModelBase(application) {

    private val pessoa = MutableLiveData<Pessoa>()

    val dados: LiveData<DadosOficina> = pessoa.switchMap { quem ->
        combine(repositorio.avariasDeConsulta(quem, null, Avaria.ESTADOS), repositorio.preventivas()) { avarias, preventivas ->
            DadosOficina(avarias, preventivas.filter { it.preventiva.especialidade == quem.especialidade })
        }.paraLiveData(DadosOficina(emptyList(), emptyList()))
    }

    fun definirPessoa(quem: Pessoa) {
        if (pessoa.value != quem) pessoa.value = quem
    }
}

/** “Resumo” do chefe de oficina: concluídas neste turno, tempo médio de reparação, preventivas feitas e em aberto. */
class ResumoOficinaFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResumoOficinaViewModel by viewModels()
    private val adapter = LinhaAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.definirPessoa(pessoaOuVoltarAEntrada() ?: return)
        binding.filtros.visibility = View.GONE
        binding.textoIntro.setText(R.string.resumo_oficina_listas)
        binding.lista.adapter = adapter
        binding.lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.dados.observe(viewLifecycleOwner) { mostrar(it) }
        mostrarErrosDe(viewModel)
    }

    private fun mostrar(dados: DadosOficina) {
        val agora = System.currentTimeMillis()
        val inicioTurno = Datas.inicioDoTurnoAtual()
        val concluidas = dados.avarias.filter { it.avaria.estado == Avaria.RESOLVIDA && (it.avaria.dataFim ?: 0) >= inicioTurno }
        val emAberto = dados.avarias.count { it.avaria.estado != Avaria.RESOLVIDA }
        val vencidas = dados.preventivas.filter { it.preventiva.estado(agora) == Preventiva.Estado.VENCIDA }
        val feitas = dados.preventivas.count { it.preventiva.ultimaData >= inicioTurno }
        val mttr = Indicadores.resumo(dados.avarias.map { it.avaria }).mttrMs
        binding.textoResumo.text = getString(
            R.string.resumo_oficina_kpis, concluidas.size, mttr?.let { Datas.duracao(requireContext(), it) } ?: getString(R.string.indicador_sem_dados),
            feitas, emAberto
        )
        val linhas = concluidas.map {
            Linha(
                id = "a" + it.avaria.id, titulo = it.avaria.titulo,
                sub = listOf(it.avaria.codigo, it.equipamentoNome, it.tecnicoNome?.let { nome -> EstiloAvaria.tecnicoComEquipa(requireContext(), nome, it.ajudantes) } ?: "").filter { texto -> texto.isNotBlank() }.joinToString(EstiloAvaria.SEPARADOR)
            )
        } + vencidas.map {
            Linha(
                id = "p" + it.preventiva.id,
                estado = EstiloAvaria.Estilo(R.string.preventiva_vencida, R.drawable.ic_alert, R.color.if_error_text),
                titulo = it.preventiva.titulo, sub = listOf(it.equipamentoNome, it.secaoNome).joinToString(EstiloAvaria.SEPARADOR)
            )
        }
        adapter.submitList(linhas)
        binding.textoVazio.setText(R.string.resumo_oficina_vazio)
        binding.textoVazio.visibility = if (linhas.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
