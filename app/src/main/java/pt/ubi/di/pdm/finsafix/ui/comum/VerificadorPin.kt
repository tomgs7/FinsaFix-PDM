package pt.ubi.di.pdm.finsafix.ui.comum

import android.app.Application
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.util.BloqueioPin
import pt.ubi.di.pdm.finsafix.util.EstadoGuardado
import pt.ubi.di.pdm.finsafix.util.Validacao

/**
 * Tudo o que um ecrã com PIN precisa: formato, bloqueio de 30 s e mensagens de erro.
 * O bloqueio é um só para o tablet (fica guardado).
 */
class VerificadorPin(private val app: Application) {

    private val bloqueio = BloqueioPin(EstadoGuardado(app))

    /** Antes de verificar: devolve a mensagem de erro se o PIN não pode ser tentado (bloqueado ou mal escrito). */
    fun antes(pin: String): String? = when {
        bloqueio.segundosRestantes() > 0 -> bloqueio.segundosRestantes().toInt().let { s -> app.resources.getQuantityString(R.plurals.erro_pin_bloqueado, s, s) }
        !Validacao.pinTemFormatoValido(pin) -> app.getString(R.string.erro_pin_formato)
        else -> null
    }

    fun certo() = bloqueio.acerto()

    /** O PIN estava errado: regista o erro e devolve a mensagem ("Restam 2 tentativas" ou bloqueio). */
    fun errado(): String {
        val restam = bloqueio.erro()
        return if (restam == 0) (Validacao.BLOQUEIO_PIN_MS / 1000).toInt().let { s -> app.resources.getQuantityString(R.plurals.erro_pin_bloqueado, s, s) }
        else app.resources.getQuantityString(R.plurals.erro_pin_incorreto, restam, restam)
    }

    /** Atalho para os casos simples: devolve null se o PIN está certo, ou a mensagem de erro. */
    suspend fun verificar(pin: String, correto: suspend () -> Boolean): String? {
        antes(pin)?.let { return it }
        if (correto()) {
            certo()
            return null
        }
        return errado()
    }
}
