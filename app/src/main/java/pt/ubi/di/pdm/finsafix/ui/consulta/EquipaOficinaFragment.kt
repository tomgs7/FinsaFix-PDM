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
import kotlinx.coroutines.flow.combine
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.data.EventoComNome
import pt.ubi.di.pdm.finsafix.data.MembroEquipa
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.databinding.FragmentEquipaOficinaBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemAtualizacaoBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemIntervencaoBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemTecnicoEquipaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.abrirDetalhe
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarMembros
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** Uma intervenção em curso com a sua equipa e as 3 últimas atualizações da cronologia. */
class CartaoIntervencao(val avaria: AvariaResumo, val equipa: List<MembroEquipa>, val atualizacoes: List<EventoComNome>)

/** Um técnico da oficina: se está em turno, em que avaria está (e se é o responsável) e o que lhe foi atribuído ou deixou em progresso. */
class LinhaTecnico(
    val tecnico: Pessoa,
    val emTurno: Boolean,
    val naAvaria: AvariaResumo?,
    val responsavel: Boolean,
    val atribuidas: Int,
    val emProgresso: List<AvariaResumo>
)

class EstadoEquipa(val emTurno: Int, val emIntervencao: Int, val disponiveis: Int, val intervencoes: List<CartaoIntervencao>, val tecnicos: List<LinhaTecnico>)

class EquipaOficinaViewModel(application: Application) : ViewModelBase(application) {

    private val chefe = MutableLiveData<Pessoa>()

    val estado: LiveData<EstadoEquipa> = chefe.switchMap { quem ->
        val especialidades = listOfNotNull(quem.especialidade, Avaria.NAO_SEI)
        combine(
            repositorio.avariasDeConsulta(quem, null, listOf(Avaria.PENDENTE, Avaria.EM_CURSO)),
            repositorio.equipasDasOficinas(especialidades),
            repositorio.eventosDasAvariasEmCurso(especialidades),
            repositorio.pessoasDoPosto(quem.postoId.orEmpty())
        ) { avarias, equipas, eventos, pessoas -> montar(avarias, equipas, eventos, pessoas) }
            .paraLiveData(EstadoEquipa(0, 0, 0, emptyList(), emptyList()))
    }

    fun definirChefe(quem: Pessoa) {
        if (chefe.value != quem) chefe.value = quem
    }

    private fun montar(avarias: List<AvariaResumo>, equipas: List<MembroEquipa>, eventos: List<EventoComNome>, pessoas: List<Pessoa>): EstadoEquipa {
        val emCurso = avarias.filter { it.avaria.estado == Avaria.EM_CURSO }
        val intervencoes = emCurso.map { resumo ->
            CartaoIntervencao(
                resumo,
                equipas.filter { it.participacao.avariaId == resumo.avaria.id },
                eventos.filter { it.evento.avariaId == resumo.avaria.id }.take(ULTIMAS_ATUALIZACOES)
            )
        }
        val turnoAtual = Datas.turnoAtual()
        val tecnicos = pessoas.filter { it.papel == Pessoa.PAPEL_TECNICO }.map { t ->
            val participacao = equipas.firstOrNull { it.participacao.tecnicoId == t.id }
            val avaria = participacao?.let { p -> emCurso.firstOrNull { it.avaria.id == p.participacao.avariaId } }
            LinhaTecnico(
                t, t.turno == turnoAtual, avaria, avaria?.avaria?.tecnicoId == t.id,
                avarias.count { it.avaria.estado == Avaria.PENDENTE && it.avaria.atribuidaAId == t.id },
                avarias.filter { it.avaria.estado == Avaria.PENDENTE && it.avaria.progressoNota != null && it.avaria.progressoPorId == t.id }
            )
        }.sortedByDescending { it.emTurno } // em turno agora primeiro; a ordem estável mantém os outros turnos por nome
        val emIntervencao = tecnicos.count { it.naAvaria != null }
        return EstadoEquipa(
            emTurno = tecnicos.count { it.emTurno },
            emIntervencao = emIntervencao,
            disponiveis = tecnicos.count { it.emTurno && it.naAvaria == null },
            intervencoes = intervencoes,
            tecnicos = tecnicos
        )
    }

    private companion object {
        const val ULTIMAS_ATUALIZACOES = 3
    }
}

/** “Equipa” do chefe de oficina: as intervenções em curso com a equipa de cada uma e os técnicos da oficina. Atualiza-se sozinho. */
class EquipaOficinaFragment : Fragment() {

    private var _binding: FragmentEquipaOficinaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EquipaOficinaViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEquipaOficinaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.definirChefe(pessoaOuVoltarAEntrada() ?: return)
        viewModel.estado.observe(viewLifecycleOwner) { mostrar(it) }
        mostrarErrosDe(viewModel)
    }

    private fun mostrar(estado: EstadoEquipa) {
        binding.textoResumo.text = listOf(
            resources.getQuantityString(R.plurals.equipa_n_em_turno, estado.emTurno, estado.emTurno),
            resources.getQuantityString(R.plurals.equipa_n_em_intervencao, estado.emIntervencao, estado.emIntervencao),
            resources.getQuantityString(R.plurals.equipa_n_disponiveis, estado.disponiveis, estado.disponiveis)
        ).joinToString(EstiloAvaria.SEPARADOR)

        val lista = binding.intervencoes.listaIntervencoes
        lista.removeAllViews()
        estado.intervencoes.forEach { lista.addView(cartao(it)) }
        binding.intervencoes.textoSemIntervencoes.isVisible = estado.intervencoes.isEmpty()

        binding.tecnicos.listaTecnicos.removeAllViews()
        estado.tecnicos.forEach { binding.tecnicos.listaTecnicos.addView(linhaDoTecnico(it)) }
    }

    private fun cartao(dados: CartaoIntervencao): View {
        val item = ItemIntervencaoBinding.inflate(layoutInflater, binding.intervencoes.listaIntervencoes, false)
        val avaria = dados.avaria.avaria
        item.textoCodigo.text = listOf(avaria.codigo, getString(EstiloAvaria.prioridade(avaria.prioridade))).joinToString(EstiloAvaria.SEPARADOR)
        item.textoTitulo.text = avaria.titulo
        item.textoLocal.text = listOf(dados.avaria.equipamentoNome, dados.avaria.secaoNome).joinToString(EstiloAvaria.SEPARADOR)
        item.textoDuracao.text = getString(
            R.string.equipa_em_curso_ha, Datas.duracao(requireContext(), System.currentTimeMillis() - (avaria.dataInicio ?: avaria.dataCriacao))
        )
        mostrarMembros(layoutInflater, item.listaEquipa, dados.equipa, avaria.tecnicoId)
        for (evento in dados.atualizacoes) {
            val linha = ItemAtualizacaoBinding.inflate(layoutInflater, item.listaAtualizacoes, false)
            linha.root.text = getString(R.string.equipa_atualizacao, evento.evento.texto, evento.pessoaNome.orEmpty(), Datas.hora(evento.evento.data))
            item.listaAtualizacoes.addView(linha.root)
        }
        item.root.setOnClickListener { abrirDetalhe(dados.avaria) }
        return item.root
    }

    private fun linhaDoTecnico(dados: LinhaTecnico): View {
        val item = ItemTecnicoEquipaBinding.inflate(layoutInflater, binding.tecnicos.listaTecnicos, false)
        item.textoNome.text = dados.tecnico.nome
        item.textoTurno.text = getString(R.string.equipa_tecnico_turno, dados.tecnico.funcao, getString(Datas.nomeTurno(dados.tecnico.turno)).lowercase())
        // Estado com ícone + texto + cor: em intervenção (aviso), disponível (ok) ou fora de turno (cinzento)
        EstiloAvaria.aplicar(
            item.textoEstado, when {
                dados.naAvaria != null -> EstiloAvaria.Estilo(R.string.equipa_estado_intervencao, R.drawable.ic_wrench, R.color.if_warn_text)
                dados.emTurno -> EstiloAvaria.Estilo(R.string.equipa_estado_disponivel, R.drawable.ic_check_circle, R.color.if_ok_text)
                else -> EstiloAvaria.Estilo(R.string.equipa_estado_fora_de_turno, R.drawable.ic_clock, R.color.if_on_surface_variant)
            }
        )
        item.textoOnde.isVisible = dados.naAvaria != null
        dados.naAvaria?.let {
            item.textoOnde.text = getString(if (dados.responsavel) R.string.equipa_na_avaria_responsavel else R.string.equipa_na_avaria_ajudante, it.avaria.codigo)
        }
        item.textoAtribuidas.isVisible = dados.atribuidas > 0
        item.textoAtribuidas.text = resources.getQuantityString(R.plurals.equipa_atribuidas, dados.atribuidas, dados.atribuidas)
        for (avaria in dados.emProgresso) {
            val linha = ItemAtualizacaoBinding.inflate(layoutInflater, item.listaEmProgresso, false)
            linha.root.text = getString(R.string.equipa_deixou_em_progresso, avaria.avaria.codigo, avaria.avaria.progressoNota.orEmpty())
            item.listaEmProgresso.addView(linha.root)
        }
        return item.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
