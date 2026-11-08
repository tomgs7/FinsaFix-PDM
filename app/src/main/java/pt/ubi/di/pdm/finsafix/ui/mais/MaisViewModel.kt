package pt.ubi.di.pdm.finsafix.ui.mais

import android.app.Application
import android.view.MenuItem
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.map
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.ui.comum.VerificadorPin
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.menu.ContadoresViewModel
import pt.ubi.di.pdm.finsafix.util.Seguranca

/** Um item do menu que foi para "Mais" (não cabe na barra), com o seu número, se tiver. */
data class ItemMenu(val item: MenuItem, val contador: ContadoresViewModel.Contador?)

class MaisViewModel(application: Application) : ViewModelBase(application) {

    private val verificador = VerificadorPin(application)

    val postos: LiveData<List<Secao>> = repositorio.secoes().paraLiveData(emptyList())
    val equipamentos: LiveData<Map<String, Int>> =
        repositorio.equipamentosPorSecao().map { lista -> lista.associate { it.secaoId to it.total } }.asLiveData()

    /** Devolve null se o PIN de administrador está certo, ou a mensagem de erro (o bloqueio de 3 erros é o do tablet). */
    suspend fun verificarPinAdministrador(pin: String): String? =
        verificador.verificar(pin) { Seguranca.pinAdministradorCorreto(pin) }
}
