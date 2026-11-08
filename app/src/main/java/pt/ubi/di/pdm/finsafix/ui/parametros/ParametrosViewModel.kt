package pt.ubi.di.pdm.finsafix.ui.parametros

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.LeituraResumo
import pt.ubi.di.pdm.finsafix.data.ParametroComLeitura
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.util.Posto

/** Controlo do turno: os parâmetros da secção do tablet e a última leitura de cada um. */
class ParametrosViewModel(application: Application) : ViewModelBase(application) {

    // A secção cujas leituras se veem: a do posto, ou outra (“Outra secção”)
    private val secaoId = MutableLiveData(Posto.obterId(application))
    val secaoAtual: LiveData<String> = secaoId

    val parametros: LiveData<List<ParametroComLeitura>> =
        secaoId.switchMap { repositorio.parametrosDaSecao(it).paraLiveData(emptyList()) }

    val ultimas: LiveData<List<LeituraResumo>> =
        secaoId.switchMap { repositorio.ultimasLeituras(it).paraLiveData(emptyList()) }

    val secoes: LiveData<List<Secao>> = repositorio.secoes().paraLiveData(emptyList())

    fun escolherSecao(id: String) {
        secaoId.value = id
    }

    private val _mensagem = MutableLiveData<Int?>()
    val mensagem: LiveData<Int?> = _mensagem

    fun registar(parametroId: String, valor: Double, quem: Pessoa) {
        executar {
            repositorio.registarLeitura(parametroId, valor, quem)
            _mensagem.value = R.string.leitura_registada
        }
    }

    fun avisarChefe(texto: String, quem: Pessoa) {
        executar {
            repositorio.avisarChefe(secaoId.value ?: Posto.obterId(getApplication()), texto, quem)
            _mensagem.value = R.string.chefe_avisado
        }
    }

    fun mensagemMostrada() {
        _mensagem.value = null
    }
}
