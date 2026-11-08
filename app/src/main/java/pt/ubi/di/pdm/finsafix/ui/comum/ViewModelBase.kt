package pt.ubi.di.pdm.finsafix.ui.comum

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Repositorio

/**
 * Base de todos os ViewModels: dá acesso ao Repositorio e trata os erros da base de dados.
 * Um erro nunca fecha a aplicação: fica em [erro] e o Fragment mostra-o numa Snackbar.
 */
open class ViewModelBase(application: Application) : AndroidViewModel(application) {

    protected val repositorio = Repositorio(application)

    private val _erro = MutableLiveData<Int?>()
    val erro: LiveData<Int?> = _erro

    fun erroMostrado() {
        _erro.value = null
    }

    /** Aviso de sucesso ou de regra (“Pedido enviado.”): mostra-se numa Snackbar e depois limpa-se. */
    private val _aviso = MutableLiveData<Int?>()
    val aviso: LiveData<Int?> = _aviso

    fun avisoMostrado() {
        _aviso.value = null
    }

    protected fun avisar(@StringRes mensagem: Int) {
        _aviso.value = mensagem
    }

    /** Converte uma consulta (Flow) em LiveData; se a leitura falhar, mostra o erro e usa [valorSeErro]. */
    protected fun <T> Flow<T>.paraLiveData(valorSeErro: T): LiveData<T> =
        catch { e ->
            Log.e(TAG, "Erro ao ler da base de dados", e)
            _erro.postValue(R.string.erro_abrir_dados)
            emit(valorSeErro)
        }.asLiveData()

    /** Mostra um erro de regra (não da base de dados), por exemplo uma autorização aberta. */
    protected fun mostrarErro(@StringRes mensagem: Int) {
        _erro.value = mensagem
    }

    /** Corre uma operação de escrita numa corrotina; se falhar, mostra [mensagemErro]. */
    protected fun executar(@StringRes mensagemErro: Int = R.string.erro_guardar, operacao: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operacao()
            } catch (e: CancellationException) {
                throw e // o ecrã fechou: não é um erro para mostrar
            } catch (e: Exception) {
                Log.e(TAG, "Erro na base de dados", e)
                _erro.value = mensagemErro
            }
        }
    }

    companion object {
        private const val TAG = "FinsaFix"
    }
}
