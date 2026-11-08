package pt.ubi.di.pdm.finsafix.ui.tecnico

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combine
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.data.Parametro
import pt.ubi.di.pdm.finsafix.data.Peca
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Preventiva
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase

/** Filtros da lista do técnico: oficina e estado. */
data class FiltroTecnico(
    val todasAsOficinas: Boolean = false,
    val estados: List<String> = listOf(Avaria.PENDENTE, Avaria.EM_CURSO)
)

/**
 * Lista do técnico. Regra: vê as avarias da sua especialidade e as "Não sei" (ainda por classificar).
 * Com "Minhas", vê só as intervenções que tem em curso.
 */
class TecnicoViewModel(application: Application) : ViewModelBase(application) {

    private val tecnico = MutableLiveData<Pessoa>()
    private val filtro = MutableLiveData(FiltroTecnico())

    val emProgresso: LiveData<List<AvariaResumo>> = tecnico.switchMap {
        repositorio.avariasEmProgresso(especialidadesDa(it, todas = false)).paraLiveData(emptyList())
    }

    val avarias: LiveData<List<AvariaResumo>> = filtro.switchMap { filtro ->
        tecnico.switchMap {
            repositorio.avariasDaOficina(especialidadesDa(it, filtro.todasAsOficinas), filtro.estados, it.id)
                .paraLiveData(emptyList())
        }
    }

    val minhas: LiveData<List<AvariaResumo>> = tecnico.switchMap {
        repositorio.avariasDoTecnico(it.id).paraLiveData(emptyList())
    }

    /** As avarias em curso em que sou ajudante: levam a etiqueta “Na equipa”. */
    val naEquipa: LiveData<Set<Long>> = tecnico.switchMap {
        repositorio.avariasComoAjudante(it.id).map { ids -> ids.toSet() }.paraLiveData(emptySet())
    }

    val concluidas: LiveData<List<AvariaResumo>> = tecnico.switchMap {
        repositorio.avariasConcluidasPor(it.id).paraLiveData(emptyList())
    }

    /** Quantas avarias abertas há na minha oficina e em todas (nos chips “Minha oficina 4”, “Todas as oficinas 8”). */
    val contagemOficinas: LiveData<Pair<Int, Int>> = tecnico.switchMap {
        val abertas = listOf(Avaria.PENDENTE, Avaria.EM_CURSO)
        combine(
            repositorio.avariasDaOficina(especialidadesDa(it, todas = false), abertas, it.id),
            repositorio.avariasDaOficina(especialidadesDa(it, todas = true), abertas, it.id)
        ) { minha, todas -> minha.size to todas.size }.paraLiveData(0 to 0)
    }

    /** Os “Por fazer” do técnico: preventivas vencidas e peças abaixo do mínimo da sua especialidade e parâmetros fora do limite. */
    data class PorFazer(val preventivas: Int, val pecas: Int, val parametros: List<String>)

    val porFazer: LiveData<PorFazer> = tecnico.switchMap { quem ->
        combine(repositorio.preventivas(), repositorio.pecas(), repositorio.todosOsParametros()) { planos, pecas, parametros ->
            val agora = System.currentTimeMillis()
            PorFazer(
                preventivas = planos.count { it.preventiva.especialidade == quem.especialidade && it.preventiva.estado(agora) == Preventiva.Estado.VENCIDA },
                pecas = pecas.count { it.peca.especialidade == quem.especialidade && it.peca.estado() <= Peca.Estado.ABAIXO },
                parametros = parametros.filter { p -> p.valor != null && p.parametro.estado(p.valor) == Parametro.Estado.FORA }.map { it.parametro.nome }.distinct()
            )
        }.paraLiveData(PorFazer(0, 0, emptyList()))
    }

    fun definirTecnico(pessoa: Pessoa) {
        if (tecnico.value != pessoa) tecnico.value = pessoa
    }

    fun mudarOficina(todas: Boolean) {
        filtro.value = filtro.value?.copy(todasAsOficinas = todas)
    }

    fun mudarEstados(estados: List<String>) {
        filtro.value = filtro.value?.copy(estados = estados)
    }

    private fun especialidadesDa(pessoa: Pessoa, todas: Boolean): List<String> =
        if (todas) listOf(Avaria.MECANICA, Avaria.ELETRICA, Avaria.NAO_SEI)
        else listOfNotNull(pessoa.especialidade, Avaria.NAO_SEI)
}
