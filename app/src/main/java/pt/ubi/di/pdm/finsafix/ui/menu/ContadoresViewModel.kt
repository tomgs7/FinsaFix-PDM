package pt.ubi.di.pdm.finsafix.ui.menu

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Aviso
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Peca
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Preventiva
import pt.ubi.di.pdm.finsafix.data.Registo
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase

/** Os números que aparecem nos ícones do menu, por destino (id do ecrã). Cada perfil tem os seus. */
class ContadoresViewModel(application: Application) : ViewModelBase(application) {

    /** [urgente] pinta o número de vermelho; sem urgência fica cinzento. */
    data class Contador(val numero: Int, val urgente: Boolean = false)

    private val pessoa = MutableLiveData<Pessoa>()

    val contadores: LiveData<Map<Int, Contador>> = pessoa.switchMap { quem ->
        fluxoDe(quem).paraLiveData(emptyMap())
    }

    fun definirPessoa(quem: Pessoa) {
        if (pessoa.value != quem) pessoa.value = quem
    }

    private fun fluxoDe(quem: Pessoa): Flow<Map<Int, Contador>> {
        val posto = quem.postoId
        return when {
            quem.papel == Pessoa.PAPEL_OPERADOR && posto != null -> operador(quem, posto)
            quem.papel == Pessoa.PAPEL_TECNICO -> tecnico(quem)
            quem.papel == Pessoa.PAPEL_CHEFE && !quem.eChefeDeOficina() && posto != null -> chefe(posto)
            quem.eChefeDeOficina() && posto != null -> oficina(quem, posto)
            else -> flowOf(emptyMap())
        }
    }

    private fun operador(quem: Pessoa, posto: String): Flow<Map<Int, Contador>> = combine(
        repositorio.avariasDaSecao(posto, listOf(Avaria.PENDENTE, Avaria.EM_CURSO)),
        repositorio.contarPedidosPorFechar(posto),
        repositorio.contarNotas(posto, quem.id)
    ) { avarias, pedidos, notas ->
        mapOf(
            R.id.inicioFragment to Contador(avarias.size, avarias.any { it.avaria.prioridade == Avaria.CRITICA }),
            R.id.pedidosOperadorFragment to Contador(pedidos),
            R.id.turnoFragment to Contador(notas)
        )
    }

    private fun tecnico(quem: Pessoa): Flow<Map<Int, Contador>> {
        val especialidades = listOfNotNull(quem.especialidade, Avaria.NAO_SEI)
        val avarias = combine(
            repositorio.avariasDaOficina(especialidades, listOf(Avaria.PENDENTE), quem.id),
            repositorio.avariasDoTecnico(quem.id),
            repositorio.paragensAtivas(null)
        ) { pendentes, minhas, paragens ->
            mapOf(
                R.id.avariasTecnicoFragment to Contador(pendentes.size, pendentes.any { it.avaria.prioridade == Avaria.CRITICA }),
                R.id.minhasFragment to Contador(minhas.size),
                R.id.paragensFragment to Contador(paragens.size, paragens.isNotEmpty())
            )
        }
        val manutencao = combine(
            repositorio.preventivas(),
            repositorio.pecas(),
            repositorio.contarNotas(quem.postoId ?: "", quem.id)
        ) { planos, pecas, notas ->
            val agora = System.currentTimeMillis()
            val vencidas = planos.count { it.preventiva.especialidade == quem.especialidade && it.preventiva.estado(agora) == Preventiva.Estado.VENCIDA }
            val abaixo = pecas.count { it.peca.especialidade == quem.especialidade && it.peca.estado() <= Peca.Estado.ABAIXO }
            mapOf(
                R.id.preventivaFragment to Contador(vencidas, vencidas > 0),
                R.id.stockFragment to Contador(abaixo),
                R.id.turnoFragment to Contador(notas)
            )
        }
        return combine(avarias, manutencao) { a, m -> a + m }
    }

    /** Chefe de oficina: avarias por atribuir (Distribuir) e rondas e passagens por validar (Validar). */
    private fun oficina(quem: Pessoa, posto: String): Flow<Map<Int, Contador>> = combine(
        repositorio.avariasDeConsulta(quem, null, listOf(Avaria.PENDENTE)),
        repositorio.rondasDa(quem.especialidade ?: ""),
        repositorio.contarPorValidar(posto),
        repositorio.notasParaChefe(posto, quem.id)
    ) { pendentes, rondas, passagens, notas ->
        mapOf(
            // vermelho se alguma das por atribuir é crítica
            R.id.distribuirFragment to pendentes.filter { it.avaria.especialidade == quem.especialidade && it.avaria.atribuidaAId == null }
                .let { Contador(it.size, urgente = it.any { r -> r.avaria.prioridade == Avaria.CRITICA }) },
            R.id.validarFragment to Contador(rondas.count { it.registo.estado == Registo.NAO_CONFORME } + passagens),
            R.id.turnoFragment to Contador(notas.size)
        )
    }

    private fun chefe(posto: String): Flow<Map<Int, Contador>> = combine(
        repositorio.avisosPorResponder(posto),
        repositorio.contarAutorizacoesPorTratar(posto),
        repositorio.contarPedidosEmAberto(posto),
        repositorio.contarPorValidar(posto)
    ) { avisos, autorizacoes, pedidos, porValidar ->
        mapOf(
            R.id.pedidosFragment to Contador(avisos.size + pedidos, pedidos > 0 || avisos.any { it.aviso.tipo == Aviso.PARAR || it.aviso.tipo == Aviso.ARRANCAR }),
            R.id.validarFragment to Contador(autorizacoes + porValidar)
        )
    }
}
