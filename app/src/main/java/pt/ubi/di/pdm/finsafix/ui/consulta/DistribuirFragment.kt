package pt.ubi.di.pdm.finsafix.ui.consulta

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
import kotlinx.coroutines.flow.combine
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.ui.comum.PARA_TODOS
import pt.ubi.di.pdm.finsafix.ui.comum.Opcao
import pt.ubi.di.pdm.finsafix.ui.comum.escolherEConfirmar
import pt.ubi.di.pdm.finsafix.ui.comum.EscolhaConfig
import pt.ubi.di.pdm.finsafix.ui.comum.Grupo
import pt.ubi.di.pdm.finsafix.ui.comum.escolher
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.databinding.FragmentListaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.lista.Acao
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** As avarias pendentes da oficina e os técnicos dela, com a carga de cada um (em curso + atribuídas). */
data class DadosDistribuicao(val pendentes: List<AvariaResumo>, val tecnicos: List<Pessoa>, val carga: Map<String, Int>)

class DistribuirViewModel(application: Application) : ViewModelBase(application) {

    private val chefe = MutableLiveData<Pessoa>()

    val dados: LiveData<DadosDistribuicao> = chefe.switchMap { quem ->
        combine(
            repositorio.avariasDeConsulta(quem, null, listOf(Avaria.PENDENTE, Avaria.EM_CURSO)),
            repositorio.pessoasDoPosto(quem.postoId ?: "")
        ) { avarias, pessoas ->
            val tecnicos = pessoas.filter { it.papel == Pessoa.PAPEL_TECNICO }
            val carga = tecnicos.associate { t ->
                t.id to avarias.count { it.avaria.tecnicoId == t.id && it.avaria.estado == Avaria.EM_CURSO || it.avaria.atribuidaAId == t.id && it.avaria.estado == Avaria.PENDENTE }
            }
            // Só as da sua especialidade: as “Não sei” primeiro têm de ser classificadas
            DadosDistribuicao(avarias.filter { it.avaria.estado == Avaria.PENDENTE && it.avaria.especialidade == quem.especialidade }, tecnicos, carga)
        }.paraLiveData(DadosDistribuicao(emptyList(), emptyList(), emptyMap()))
    }

    fun definirChefe(quem: Pessoa) {
        if (chefe.value != quem) chefe.value = quem
    }

    /** O responsável (ou null para “todos veem”) e os ajudantes; se uma regra falhar (ex.: um ajudante já está ocupado), avisa. */
    fun atribuir(avariaId: Long, responsavelId: String?, ajudantes: List<String>, quem: Pessoa) {
        executar { avisar(if (repositorio.atribuirEquipa(avariaId, responsavelId, ajudantes, quem)) R.string.distribuir_atribuida else R.string.erro_equipa) }
    }
}

/** “Distribuir” do chefe de oficina: cada avaria pendente tem “Atribuir” e escolhe-se o técnico (com a carga de cada um). */
class DistribuirFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DistribuirViewModel by viewModels()
    private val adapter = LinhaAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val chefe = pessoaOuVoltarAEntrada() ?: return
        viewModel.definirChefe(chefe)
        binding.textoIntro.setText(R.string.distribuir_intro)
        binding.textoResumo.isVisible = false
        binding.filtros.isVisible = false
        binding.textoVazio.setText(R.string.distribuir_vazio)
        binding.lista.adapter = adapter
        binding.lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.dados.observe(viewLifecycleOwner) { mostrar(it, chefe) }
        mostrarErrosDe(viewModel)
    }

    private fun mostrar(dados: DadosDistribuicao, chefe: Pessoa) {
        adapter.submitList(dados.pendentes.map { resumo ->
            val atribuida = dados.tecnicos.firstOrNull { it.id == resumo.avaria.atribuidaAId }
            val ajudantes = resumo.ajudantes
            Linha(
                id = resumo.avaria.id.toString(), titulo = resumo.avaria.titulo,
                sub = listOf(resumo.avaria.codigo, resumo.equipamentoNome, resumo.secaoNome).joinToString(EstiloAvaria.SEPARADOR),
                extra = atribuida?.let {
                    val equipa = if (ajudantes > 0) " " + resources.getQuantityString(R.plurals.ajudantes_mais, ajudantes, ajudantes) else ""
                    getString(R.string.distribuir_atribuida_a, it.nome) + equipa
                },
                acoes = listOf(Acao(getString(R.string.distribuir_atribuir)) { escolherTecnico(resumo, dados, chefe) })
            )
        })
        binding.textoVazio.isVisible = dados.pendentes.isEmpty()
    }

    /** “Carlos · manhã · 0”: o técnico, o turno e quantas intervenções já tem. */
    private fun opcaoDoTecnico(tecnico: Pessoa, dados: DadosDistribuicao): Opcao {
        val carga = dados.carga[tecnico.id] ?: 0
        return Opcao(tecnico.id, tecnico.nome, resources.getQuantityString(R.plurals.distribuir_carga, carga, getString(Datas.nomeTurno(tecnico.turno)), carga))
    }

    /** 1.º passo: o responsável (ou “Sem atribuição”). 2.º passo: os ajudantes, se quiser. */
    private fun escolherTecnico(resumo: AvariaResumo, dados: DadosDistribuicao, chefe: Pessoa) {
        val opcoes = dados.tecnicos.map { opcaoDoTecnico(it, dados) } + Opcao(PARA_TODOS, getString(R.string.distribuir_para_todos))
        escolherEConfirmar(getString(R.string.distribuir_escolher), opcoes, getString(R.string.distribuir_atribuir), etiqueta = resumo.avaria.codigo) { id ->
            if (id == PARA_TODOS) viewModel.atribuir(resumo.avaria.id, null, emptyList(), chefe)
            else escolherAjudantes(resumo, dados, chefe, id)
        }
    }

    /** Escolha múltipla (pode ficar vazia): os outros técnicos da oficina, que entram na equipa quando o responsável assumir. */
    private fun escolherAjudantes(resumo: AvariaResumo, dados: DadosDistribuicao, chefe: Pessoa, responsavelId: String) {
        val opcoes = dados.tecnicos.filter { it.id != responsavelId }.map { opcaoDoTecnico(it, dados) }
        escolher(
            EscolhaConfig(
                titulo = getString(R.string.distribuir_ajudantes),
                grupos = listOf(Grupo(null, opcoes)),
                etiqueta = resumo.avaria.codigo,
                multipla = true,
                textoConfirmar = getString(R.string.distribuir_atribuir),
                exigeAlteracao = false,
                aoConfirmar = { ids -> viewModel.atribuir(resumo.avaria.id, responsavelId, ids.toList(), chefe) }
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
