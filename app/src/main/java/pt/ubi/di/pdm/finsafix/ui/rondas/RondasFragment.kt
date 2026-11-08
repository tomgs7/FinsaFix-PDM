package pt.ubi.di.pdm.finsafix.ui.rondas

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.ui.comum.Opcao
import pt.ubi.di.pdm.finsafix.ui.comum.Grupo
import pt.ubi.di.pdm.finsafix.ui.comum.EscolhaConfig
import pt.ubi.di.pdm.finsafix.ui.comum.escolher
import pt.ubi.di.pdm.finsafix.ui.comum.escolherSecao
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Registo
import pt.ubi.di.pdm.finsafix.data.RegistoResumo
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.databinding.FragmentListaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.pedirTexto
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Validacao
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

class RondasViewModel(application: Application) : ViewModelBase(application) {
    private val especialidade = MutableLiveData<String>()
    val rondas: LiveData<List<RegistoResumo>> = especialidade.switchMap { repositorio.rondasDa(it).paraLiveData(emptyList()) }
    val secoes: LiveData<List<Secao>> = repositorio.secoes().paraLiveData(emptyList())

    fun definirEspecialidade(valor: String) {
        if (especialidade.value != valor) especialidade.value = valor
    }

    fun registar(secaoId: String, naoConformes: List<String>, observacao: String, quem: Pessoa) {
        executar {
            repositorio.registarRonda(secaoId, naoConformes, observacao, quem)
            avisar(R.string.ronda_registada)
        }
    }
}

/** “Rondas” do técnico: escolhe a secção, marca o que não está conforme e vê as últimas rondas da sua especialidade. */
class RondasFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RondasViewModel by viewModels()
    private val adapter = LinhaAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return
        viewModel.definirEspecialidade(pessoa.especialidade ?: return)

        binding.textoIntro.setText(R.string.rondas_intro)
        binding.textoResumo.isVisible = false
        binding.filtros.isVisible = false
        binding.botaoAcao.setText(R.string.ronda_fazer)
        binding.botaoAcao.isVisible = true
        binding.botaoAcao.setOnClickListener { escolherSecao(pessoa) }
        binding.textoVazio.setText(R.string.rondas_vazio)
        binding.lista.adapter = adapter
        binding.lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.rondas.observe(viewLifecycleOwner) { lista ->
            adapter.submitList(lista.map { linhaDe(it) })
            binding.textoVazio.isVisible = lista.isEmpty()
        }
        viewModel.secoes.observe(viewLifecycleOwner) { } // a LiveData só tem valor enquanto é observada
        mostrarErrosDe(viewModel)
    }

    private fun linhaDe(resumo: RegistoResumo): Linha {
        val registo = resumo.registo
        val conforme = registo.estado == Registo.CONFORME
        return Linha(
            id = registo.id.toString(),
            estado = if (conforme) EstiloAvaria.Estilo(R.string.ronda_conforme, R.drawable.ic_check_circle, R.color.if_ok_text)
            else EstiloAvaria.Estilo(R.string.ronda_nao_conforme, R.drawable.ic_alert, R.color.if_warn_text),
            titulo = getString(R.string.ronda_titulo, resumo.secaoNome),
            sub = listOf(resumo.pessoaNome, Datas.dataHora(registo.dataCriacao)).joinToString(EstiloAvaria.SEPARADOR),
            extra = listOfNotNull(registo.extra, registo.texto.ifBlank { null }).joinToString("\n").ifBlank { null }
        )
    }

    /** 1.º passo: a secção (só secções, não oficinas). */
    private fun escolherSecao(pessoa: Pessoa) {
        val secoes = viewModel.secoes.value.orEmpty().filter { it.tipo == Secao.TIPO_SECAO }
        escolherSecao(getString(R.string.ronda_secao_titulo), secoes) { it?.let { s -> marcarPontos(pessoa, s) } }
    }

    /** 2.º passo: os pontos que não estão conformes (nenhum marcado = tudo conforme). */
    private fun marcarPontos(pessoa: Pessoa, secao: Secao) {
        val pontos = Registo.PONTOS_RONDA[pessoa.especialidade].orEmpty()
        escolher(
            EscolhaConfig(
                titulo = getString(R.string.ronda_pontos_titulo, secao.nome),
                grupos = listOf(Grupo(null, pontos.mapIndexed { indice, ponto -> Opcao(indice.toString(), ponto) })),
                multipla = true,
                textoConfirmar = getString(R.string.botao_seguinte),
                exigeAlteracao = false,
                aoConfirmar = { marcados ->
                    val naoConformes = pontos.filterIndexed { indice, _ -> indice.toString() in marcados }
                    if (naoConformes.isEmpty()) viewModel.registar(secao.id, emptyList(), "", pessoa)
                    else observacao(pessoa, secao, naoConformes)
                }
            )
        )
    }

    /** 3.º passo (só com anomalias): a observação, com pelo menos 5 caracteres. */
    private fun observacao(pessoa: Pessoa, secao: Secao, naoConformes: List<String>) {
        pedirTexto(
            R.string.ronda_observacao_titulo, R.string.ronda_observacao_dica, R.string.ronda_registar,
            validar = { if (Validacao.notaValida(it)) null else getString(R.string.erro_nota) }
        ) { texto -> viewModel.registar(secao.id, naoConformes, texto, pessoa) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
