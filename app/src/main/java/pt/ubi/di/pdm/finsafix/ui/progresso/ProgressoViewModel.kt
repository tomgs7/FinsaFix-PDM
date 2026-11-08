package pt.ubi.di.pdm.finsafix.ui.progresso

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.util.Validacao

/**
 * "Deixar em progresso": quem deu a ordem, quando se retoma, o motivo e uma nota obrigatória.
 * As respostas ficam aqui para não se perderem ao rodar o ecrã.
 */
class ProgressoViewModel(application: Application) : ViewModelBase(application) {

    var ordem: String? = null
    var retoma: String? = null
    var motivo: String? = null
    var nota: String = ""

    private val _guardado = MutableLiveData(false)
    val guardado: LiveData<Boolean> = _guardado

    fun escolhasFeitas(): Boolean = ordem != null && retoma != null && motivo != null

    fun notaValida(): Boolean = Validacao.notaValida(nota)

    fun guardar(avariaId: Long, tecnico: Pessoa) {
        if (!escolhasFeitas() || !notaValida()) return
        executar {
            if (repositorio.deixarEmProgresso(avariaId, tecnico, ordem!!, retoma!!, motivo!!, nota.trim())) {
                _guardado.value = true
            } else {
                mostrarErro(R.string.erro_autorizacao_ativa)
            }
        }
    }
}
