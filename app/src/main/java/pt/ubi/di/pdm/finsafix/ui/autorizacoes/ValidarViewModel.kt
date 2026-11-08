package pt.ubi.di.pdm.finsafix.ui.autorizacoes

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Registo
import pt.ubi.di.pdm.finsafix.data.RegistoResumo
import pt.ubi.di.pdm.finsafix.data.Autorizacao
import pt.ubi.di.pdm.finsafix.data.AutorizacaoResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoAutorizacao
import pt.ubi.di.pdm.finsafix.ui.comum.VerificadorPin
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase

/** Ecrã Validar do chefe: as autorizações abertas da sua secção e as decisões (aprovar, recusar, encerrar). */
class ValidarViewModel(application: Application) : ViewModelBase(application) {

    private val secaoId = MutableLiveData<String>()
    val autorizacoes: LiveData<List<AutorizacaoResumo>> =
        secaoId.switchMap { repositorio.autorizacoesAbertasDaSecao(it).paraLiveData(emptyList()) }

    // Checklists com Não OK, rondas não conformes (oficina) e passagens de turno por confirmar
    val checklists: LiveData<List<RegistoResumo>> =
        secaoId.switchMap { repositorio.checklistsDaSecao(it).map { l -> l.filter { r -> r.registo.estado == Registo.NAO_CONFORME } }.paraLiveData(emptyList()) }
    val passagens: LiveData<List<RegistoResumo>> =
        secaoId.switchMap { repositorio.rececoesDoPosto(it).map { l -> l.filter { r -> r.registo.estado == Registo.POR_CONFIRMAR } }.paraLiveData(emptyList()) }
    private val especialidade = MutableLiveData<String>()
    val rondas: LiveData<List<RegistoResumo>> =
        especialidade.switchMap { repositorio.rondasDa(it).map { l -> l.filter { r -> r.registo.estado == Registo.NAO_CONFORME } }.paraLiveData(emptyList()) }

    fun renovar(id: Long, chefe: Pessoa) = executar { avisarResultado(repositorio.renovarAutorizacao(id, chefe), R.string.aut_renovada) }

    fun definirEspecialidade(valor: String) {
        if (especialidade.value != valor) especialidade.value = valor
    }

    fun validarChecklist(id: Long, chefe: Pessoa) = executar { avisarResultado(repositorio.validarChecklist(id, chefe), R.string.checklist_validada) }
    fun confirmarPassagem(id: Long, chefe: Pessoa) = executar { avisarResultado(repositorio.confirmarPassagem(id, chefe), R.string.passagem_confirmada) }
    fun validarRonda(id: Long, chefe: Pessoa) = executar { avisarResultado(repositorio.validarRonda(id, chefe), R.string.ronda_validada) }

    private fun avisarResultado(correu: Boolean, mensagem: Int) {
        _mensagem.value = if (correu) mensagem else R.string.erro_validar
    }

    // O PIN errado conta para o mesmo bloqueio de 3 tentativas, seja ao aprovar ou ao encerrar.
    private val verificador = VerificadorPin(application)

    private val _mensagem = MutableLiveData<Int?>()
    val mensagem: LiveData<Int?> = _mensagem

    /** A secção é a do chefe autenticado (e o Repositório volta a confirmá-lo em cada decisão). */
    fun definirSecao(id: String) {
        if (secaoId.value != id) secaoId.value = id
    }

    fun mensagemMostrada() {
        _mensagem.value = null
    }

    /** Devolve null se aprovou, ou a mensagem para o campo do PIN. */
    suspend fun aprovar(aut: Autorizacao, chefe: Pessoa, pin: String, checklistCompleta: Boolean, vigilante: String): String? =
        comPin(pin, R.string.aut_aprovada) { repositorio.aprovarAutorizacao(aut.id, chefe, pin, checklistCompleta, vigilante) }

    /** Há uma paragem ativa da avaria? (se não, o chefe vê o aviso “Sem confirmação de máquina parada”). */
    suspend fun maquinaParada(avariaId: Long): Boolean = repositorio.paragemAtivaDaAvaria(avariaId).first() != null

    suspend fun encerrar(aut: Autorizacao, chefe: Pessoa, pin: String, checklistCompleta: Boolean): String? =
        comPin(pin, R.string.aut_encerrada) { repositorio.encerrarAutorizacao(aut.id, chefe, pin, checklistCompleta) }

    fun recusar(aut: Autorizacao, chefe: Pessoa, motivo: String) {
        executar {
            val resultado = repositorio.recusarAutorizacao(aut.id, chefe, motivo)
            _mensagem.value = if (resultado == ResultadoAutorizacao.OK) R.string.aut_recusada else EstiloAutorizacao.mensagem(resultado)
        }
    }

    private suspend fun comPin(pin: String, seCorreuBem: Int, passo: suspend () -> ResultadoAutorizacao): String? {
        verificador.antes(pin)?.let { return it }
        val resultado = try {
            passo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Erro na base de dados", e)
            return getApplication<Application>().getString(R.string.erro_guardar)
        }
        return when (resultado) {
            ResultadoAutorizacao.OK -> {
                verificador.certo()
                _mensagem.value = seCorreuBem
                null
            }
            ResultadoAutorizacao.PIN_INCORRETO -> verificador.errado()
            else -> getApplication<Application>().getString(EstiloAutorizacao.mensagem(resultado))
        }
    }

    private companion object {
        const val TAG = "FinsaFix"
    }
}
