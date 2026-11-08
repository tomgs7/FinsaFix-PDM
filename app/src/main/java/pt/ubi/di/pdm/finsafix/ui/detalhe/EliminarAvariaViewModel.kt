package pt.ubi.di.pdm.finsafix.ui.detalhe

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Repositorio
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.util.Sessao

/**
 * Eliminar uma avaria e o "Desfazer" da Snackbar.
 * Fica associado à Activity (activityViewModels), porque o "Desfazer" tem de funcionar
 * depois de o ecrã de detalhe fechar.
 */
class EliminarAvariaViewModel(application: Application) : ViewModelBase(application) {

    private var ultimaEliminada: Repositorio.AvariaEliminada? = null

    /** O motivo escolhido, depois de eliminar com sucesso. O Fragment mostra a Snackbar e fecha. */
    private val _eliminada = MutableLiveData<String?>()
    val eliminada: LiveData<String?> = _eliminada

    fun eliminar(avaria: Avaria, motivo: String) {
        val quem = Sessao.pessoa ?: return
        executar(R.string.erro_eliminar) {
            ultimaEliminada = repositorio.eliminarAvaria(avaria, quem)
            _eliminada.value = motivo
        }
    }

    fun eliminacaoMostrada() {
        _eliminada.value = null
    }

    fun desfazer() {
        val eliminada = ultimaEliminada ?: return
        ultimaEliminada = null
        executar { repositorio.restaurarAvaria(eliminada) }
    }
}
