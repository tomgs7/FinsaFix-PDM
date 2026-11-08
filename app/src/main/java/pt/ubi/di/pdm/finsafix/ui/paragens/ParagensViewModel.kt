package pt.ubi.di.pdm.finsafix.ui.paragens

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.ParagemResumo
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.util.Sessao
import pt.ubi.di.pdm.finsafix.util.Posto

/**
 * Paragens ativas e dos últimos 7 dias. O operador vê a sua secção e pode registar paragens;
 * o técnico (tablet da oficina) vê todas as secções.
 */
class ParagensViewModel(application: Application) : ViewModelBase(application) {

    private val postoId = Posto.obterId(application)

    val equipamentos: LiveData<List<Equipamento>> = repositorio.equipamentosDaSecao(postoId).paraLiveData(emptyList())

    private val todasAsSecoes = MutableLiveData<Boolean>()
    val paragens: LiveData<List<ParagemResumo>> = todasAsSecoes.switchMap { todas ->
        repositorio.paragensRecentes(if (todas) null else postoId, System.currentTimeMillis() - SETE_DIAS_MS)
            .paraLiveData(emptyList())
    }

    private val _mensagem = MutableLiveData<Int?>()
    val mensagem: LiveData<Int?> = _mensagem

    /** O técnico vê todas as secções; o operador só a do tablet. */
    fun mostrarTodasAsSecoes(todas: Boolean) {
        if (todasAsSecoes.value != todas) todasAsSecoes.value = todas
    }

    fun registar(equipamentoId: String, causa: String) {
        val quem = Sessao.pessoa ?: return
        executar {
            _mensagem.value = if (repositorio.registarParagem(equipamentoId, causa, quem)) R.string.paragem_registada
            else R.string.erro_ja_parado
        }
    }

    fun retomar(paragemId: Long) {
        val quem = Sessao.pessoa ?: return
        executar {
            repositorio.retomarProducao(paragemId, quem)
            _mensagem.value = R.string.producao_retomada
        }
    }

    fun mensagemMostrada() {
        _mensagem.value = null
    }

    companion object {
        private const val SETE_DIAS_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
