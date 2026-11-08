package pt.ubi.di.pdm.finsafix.ui.inicio

import android.app.Application
import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.data.AvisoResumo
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.ParagemResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Registo
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Posto

/** Estado de um equipamento, calculado a partir das avarias abertas. */
enum class EstadoEquipamento { OPERACIONAL, AVARIA_MENOR, PARADA }

data class EquipamentoComEstado(val equipamento: Equipamento, val estado: EstadoEquipamento, val paradaDesde: Long? = null)

/** Filtros da lista "Avarias da secção": cada um decide se uma avaria aberta aparece. */
enum class FiltroAvarias(val aceita: (Avaria) -> Boolean) {
    TODAS({ true }),
    PENDENTES({ it.estado == Avaria.PENDENTE }),
    EM_CURSO({ it.estado == Avaria.EM_CURSO }),
    MECANICA({ it.especialidade == Avaria.MECANICA }),
    ELETRICA({ it.especialidade == Avaria.ELETRICA })
}

/** A lista filtrada e as contagens que aparecem nos filtros e no resumo. */
data class ListaAvarias(
    val avarias: List<AvariaResumo>,
    val contagens: Map<FiltroAvarias, Int>,
    val paragens: Int,
    val producao: Double,
    val unidade: String
)

private val ABERTAS = listOf(Avaria.PENDENTE, Avaria.EM_CURSO)

class InicioViewModel(application: Application) : ViewModelBase(application) {

    private val secaoId = Posto.obterId(application)

    private val paragensAtivas = repositorio.paragensAtivas(secaoId)

    /** Junta os equipamentos com as paragens ativas e as avarias abertas para saber o estado de cada um. */
    val equipamentos: LiveData<List<EquipamentoComEstado>> = combine(
        repositorio.equipamentosDaSecao(secaoId),
        repositorio.avariasDaSecao(secaoId, ABERTAS),
        paragensAtivas
    ) { equipamentos, abertas, paradas ->
        equipamentos.map { equipamento ->
            val parada = paradas.firstOrNull { it.paragem.equipamentoId == equipamento.id }
            val estado = when {
                parada != null -> EstadoEquipamento.PARADA
                abertas.any { it.avaria.equipamentoId == equipamento.id } -> EstadoEquipamento.AVARIA_MENOR
                else -> EstadoEquipamento.OPERACIONAL
            }
            EquipamentoComEstado(equipamento, estado, parada?.paragem?.inicio)
        }
    }.paraLiveData(emptyList())

    /** A paragem mostrada no banner do topo (a mais recente, se houver mais do que uma). */
    val paragemNoBanner: LiveData<ParagemResumo?> = paragensAtivas.map { it.firstOrNull() }.paraLiveData(null)

    /** O que o chefe tem por tratar: autorizações, pedidos de ajuda e avisos sem resposta. */
    data class PorTratar(val autorizacoes: Int, val pedidos: Int, val avisos: Int, val passagens: Int = 0, val checklists: Int = 0)

    val porTratar: LiveData<PorTratar> = combine(
        repositorio.contarAutorizacoesPorTratar(secaoId), repositorio.contarPedidosEmAberto(secaoId), repositorio.avisosPorResponder(secaoId),
        repositorio.rececoesDoPosto(secaoId), repositorio.checklistsDaSecao(secaoId)
    ) { autorizacoes, pedidos, avisos, rececoes, checklists ->
        PorTratar(
            autorizacoes, pedidos, avisos.size, rececoes.count { it.registo.estado == Registo.POR_CONFIRMAR },
            checklists.count { it.registo.estado == Registo.NAO_CONFORME }
        )
    }.paraLiveData(PorTratar(0, 0, 0))

    /** A checklist de arranque deste turno já foi feita? */
    val checklistFeita: LiveData<Boolean> = repositorio.contarChecklistsDoTurno(secaoId).map { it > 0 }.paraLiveData(true)

    val pessoas: LiveData<List<Pessoa>> = repositorio.pessoasDoPosto(secaoId).paraLiveData(emptyList())

    val avisos: LiveData<List<AvisoResumo>> = repositorio.avisosRecentes(secaoId).paraLiveData(emptyList())

    // O filtro e a pesquisa mudam-se no ecrã; a lista cruza-os com as avarias abertas (tudo em memória)
    private val filtro = MutableStateFlow(FiltroAvarias.TODAS)
    private val pesquisa = MutableStateFlow("")

    val lista: LiveData<ListaAvarias> = combine(
        repositorio.avariasDaSecao(secaoId, ABERTAS), filtro, pesquisa, paragensAtivas,
        combine(
            repositorio.producaoDoTurno(secaoId, Datas.turnoAtual(), Datas.inicioDoTurnoAtual()),
            repositorio.secao(secaoId)
        ) { producao, secao -> producao.sumOf { it.producao.quantidade } to (secao?.unidade ?: "") }
    ) { abertas, filtroAtual, texto, paradas, (producao, unidade) ->
        val termo = texto.trim().lowercase()
        val visiveis = abertas.filter { filtroAtual.aceita(it.avaria) }.filter {
            termo.isEmpty() || listOf(it.avaria.codigo, it.avaria.titulo, it.equipamentoNome).any { campo -> campo.lowercase().contains(termo) }
        }
        ListaAvarias(visiveis, FiltroAvarias.entries.associateWith { f -> abertas.count { f.aceita(it.avaria) } }, paradas.size, producao, unidade)
    }.paraLiveData(ListaAvarias(emptyList(), emptyMap(), 0, 0.0, ""))

    fun escolherFiltro(novoFiltro: FiltroAvarias) {
        filtro.value = novoFiltro
    }

    fun pesquisar(texto: String) {
        pesquisa.value = texto
    }
}
