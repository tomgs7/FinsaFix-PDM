package pt.ubi.di.pdm.finsafix.ui.consulta

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.AutorizacaoResumo
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.data.Indicadores
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase

/** Filtros da consulta: secção (só a engenharia a escolhe) e estados. */
data class FiltroConsulta(
    val secao: Secao? = null,
    val estados: List<String> = Avaria.ESTADOS,
    val especialidade: String? = null,
    val soPorAprovar: Boolean = false
)

/**
 * Consulta de avarias da engenharia (toda a fábrica) e do chefe de oficina (a sua especialidade).
 * Quem decide o que cada perfil vê é o Repositório, não este ViewModel.
 */
class ConsultaViewModel(application: Application) : ViewModelBase(application) {

    private val pessoa = MutableLiveData<Pessoa>()
    private val _filtro = MutableLiveData(FiltroConsulta())
    val filtro: LiveData<FiltroConsulta> = _filtro

    val secoes: LiveData<List<Secao>> = repositorio.secoes().paraLiveData(emptyList())

    val avarias: LiveData<List<AvariaResumo>> = _filtro.switchMap { filtro ->
        pessoa.switchMap {
            repositorio.avariasDeConsulta(it, filtro.secao?.id, filtro.estados)
                .map { lista ->
                    lista.filter { r -> filtro.especialidade == null || r.avaria.especialidade == filtro.especialidade }
                        .filter { r -> !filtro.soPorAprovar || r.avaria.porAprovar() }
                }
                .paraLiveData(emptyList())
        }
    }

    /** As autorizações da fábrica (só a engenharia vê): ativas = pedidas, aprovadas e em curso; histórico = recusadas e encerradas. */
    val autorizacoes: LiveData<List<AutorizacaoResumo>> = repositorio.todasAsAutorizacoes().paraLiveData(emptyList())

    /** “Por classificar 2 · Por aprovar 2”: números de toda a fábrica, sem filtros. */
    val decisoes: LiveData<Pair<Int, Int>> = pessoa.switchMap {
        repositorio.avariasDeConsulta(it, null, Avaria.ESTADOS)
            .map { lista -> lista.count { r -> r.avaria.especialidade == Avaria.NAO_SEI && r.avaria.estado == Avaria.PENDENTE } to lista.count { r -> r.avaria.porAprovar() } }
            .paraLiveData(0 to 0)
    }

    /** O resumo do ecrã Início / Resumo: as mesmas avarias, sem filtros. */
    val resumo: LiveData<Indicadores.Resumo> = pessoa.switchMap {
        repositorio.avariasDeConsulta(it, null, Avaria.ESTADOS)
            .map { lista -> Indicadores.resumo(lista.map { resumo -> resumo.avaria }) }
            .paraLiveData(Indicadores.Resumo(0, 0, 0, 0, null))
    }

    /** Período dos Indicadores: 7 ou 30 dias. */
    private val periodo = MutableLiveData(7)

    /** Os números do período, o período anterior (para comparar), o gráfico e as secções. */
    data class Analise(
        val avarias: Int, val anterior: Int, val mtbfMs: Long?, val mttrMs: Long?, val paradoPorAvariaMs: Long, val porDia: IntArray,
        val porEquipamento: List<Indicadores.PorEquipamento>, val porSecao: List<Pair<String, Int>>
    )

    val analise: LiveData<Analise> = periodo.switchMap { dias ->
        _filtro.switchMap { filtro -> pessoa.switchMap { quem ->
            // O filtro de secção vale para as avarias e para as paragens
            combine(
                repositorio.avariasDeConsulta(quem, filtro.secao?.id, Avaria.ESTADOS),
                repositorio.paragensRecentes(filtro.secao?.id, 0L)
            ) { todas, paragens ->
                val agora = System.currentTimeMillis()
                val duracao = dias * 24L * 60 * 60 * 1000
                val atuais = Indicadores.doPeriodo(todas, agora - duracao, agora + 1)
                val porEquipamento = Indicadores.porEquipamento(atuais)
                Analise(
                    avarias = atuais.size,
                    anterior = Indicadores.doPeriodo(todas, agora - 2 * duracao, agora - duracao).size,
                    mtbfMs = Indicadores.mtbfMedio(porEquipamento),
                    mttrMs = Indicadores.mttr(atuais.map { it.avaria }),
                    paradoPorAvariaMs = Indicadores.paradoPorAvaria(paragens.map { it.paragem }, agora - duracao, agora),
                    porDia = Indicadores.avariasPorDia(atuais.map { it.avaria }, dias, agora),
                    porEquipamento = porEquipamento,
                    porSecao = atuais.groupingBy { it.secaoNome }.eachCount().toList().sortedByDescending { it.second }
                )
            }.paraLiveData(Analise(0, 0, null, null, 0L, IntArray(dias), emptyList(), emptyList()))
        } }
    }

    fun mudarPeriodo(dias: Int) {
        if (periodo.value != dias) periodo.value = dias
    }

    val indicadores: LiveData<List<Indicadores.PorEquipamento>> = pessoa.switchMap {
        repositorio.indicadores(it).paraLiveData(emptyList())
    }

    fun definirPessoa(quem: Pessoa) {
        if (pessoa.value != quem) pessoa.value = quem
    }

    fun mudarSecao(secao: Secao?) {
        _filtro.value = _filtro.value?.copy(secao = secao)
    }

    fun mudarEstados(estados: List<String>, soPorAprovar: Boolean = false) {
        _filtro.value = _filtro.value?.copy(estados = estados, soPorAprovar = soPorAprovar)
    }

    fun mudarEspecialidade(especialidade: String?) {
        _filtro.value = _filtro.value?.copy(especialidade = especialidade)
    }
}
