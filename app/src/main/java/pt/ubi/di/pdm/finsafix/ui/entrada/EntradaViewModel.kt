package pt.ubi.di.pdm.finsafix.ui.entrada

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.CarregadorDadosDemo
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.ui.comum.VerificadorPin
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.util.Posto

class EntradaViewModel(application: Application) : ViewModelBase(application) {

    // Quando o posto muda, o switchMap troca automaticamente as consultas que dependem dele.
    private val postoId = MutableLiveData(Posto.obterId(application))
    val posto: LiveData<Secao?> = postoId.switchMap { repositorio.secao(it).paraLiveData(null) }
    val equipamentos: LiveData<List<Equipamento>> = postoId.switchMap { repositorio.equipamentosDaSecao(it).paraLiveData(emptyList()) }
    val pessoas: LiveData<List<Pessoa>> = postoId.switchMap { repositorio.pessoasDoPosto(it).paraLiveData(emptyList()) }

    // O bloqueio de 3 erros é um só para o tablet (VerificadorPin guarda-o em SharedPreferences).
    private val verificador = VerificadorPin(application)

    fun dadosDemoFalharam(): Boolean = CarregadorDadosDemo.falhou

    /**
     * Entrada pelo cadeado: o PIN de um chefe deste posto ou da engenharia (qualquer tablet).
     * Devolve a pessoa ou, se não, a mensagem de erro.
     */
    suspend fun entrarComoChefia(pin: String): Resultado<Pessoa> {
        verificador.antes(pin)?.let { return Resultado.Erro(it) }
        val chefe = try {
            repositorio.autenticarChefia(Posto.obterId(getApplication()), pin)
        } catch (e: Exception) {
            return Resultado.Erro(getApplication<Application>().getString(R.string.erro_abrir_dados))
        }
        if (chefe == null) return Resultado.Erro(verificador.errado())
        verificador.certo()
        return Resultado.Certo(chefe)
    }

    sealed interface Resultado<out T> {
        data class Certo<T>(val valor: T) : Resultado<T>
        data class Erro(val mensagem: String) : Resultado<Nothing>
    }
}
