package pt.ubi.di.pdm.finsafix.ui.avisos

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import pt.ubi.di.pdm.finsafix.data.AvisoResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase

/**
 * O que a secção tem à espera: o aviso urgente (banner no topo de todos os ecrãs), os avisos por responder
 * e as autorizações por tratar (contadores do menu do chefe).
 * Fica na Activity, por isso vale para todos os ecrãs.
 */
class AvisoViewModel(application: Application) : ViewModelBase(application) {

    private val postoId = MutableLiveData<String>()
    val urgente: LiveData<AvisoResumo?> = postoId.switchMap { repositorio.avisoUrgente(it).paraLiveData(null) }
    val porResponder: LiveData<List<AvisoResumo>> = postoId.switchMap { repositorio.avisosPorResponder(it).paraLiveData(emptyList()) }

    /** O posto pode mudar (Configurar posto) com a Activity viva: chama-se depois de cada entrada. */
    fun definirPosto(id: String) {
        if (postoId.value != id) postoId.value = id
    }

    fun responder(aviso: AvisoResumo, quem: Pessoa) {
        executar { repositorio.responderAviso(aviso.aviso.id, quem) }
    }
}
