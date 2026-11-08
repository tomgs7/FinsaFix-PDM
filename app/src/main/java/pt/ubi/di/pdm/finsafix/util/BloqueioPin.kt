package pt.ubi.di.pdm.finsafix.util

import androidx.core.content.edit
import android.content.Context

/**
 * Bloqueio de tentativas de PIN: 3 erros seguidos bloqueiam durante 30 segundos.
 * O [estado] diz onde ficam os números (em memória nos testes, em SharedPreferences na aplicação) e
 * [relogio] devolve a hora atual em milissegundos (nos testes é um relógio falso).
 *
 * shortcut: usa a hora do tablet; mudar a hora do tablet encurta o bloqueio. Guardar o tempo desde o arranque se for preciso.
 */
class BloqueioPin(
    private val estado: Estado = EmMemoria(),
    private val relogio: () -> Long = System::currentTimeMillis
) {

    interface Estado {
        var erradas: Int
        var bloqueadoAte: Long

        /** Guarda a hora de um PIN errado (para o registo da engenharia); quem não guarda não faz nada. */
        fun registarErro(hora: Long) {}

        /** As horas dos PINs errados desde [desde], por ordem. */
        fun errosDesde(desde: Long): List<Long> = emptyList()
    }

    class EmMemoria : Estado {
        override var erradas = 0
        override var bloqueadoAte = 0L
        private val horas = mutableListOf<Long>()

        override fun registarErro(hora: Long) {
            horas += hora
        }

        override fun errosDesde(desde: Long) = horas.filter { it >= desde }
    }

    /** Segundos que faltam para acabar o bloqueio (0 se não está bloqueado). */
    fun segundosRestantes(): Long = ((estado.bloqueadoAte - relogio() + 999) / 1000).coerceAtLeast(0)

    /** Regista um PIN errado e devolve as tentativas que restam (0 = ficou bloqueado). */
    fun erro(): Int {
        estado.registarErro(relogio())
        estado.erradas++
        val restam = Validacao.MAX_TENTATIVAS_PIN - estado.erradas
        if (restam <= 0) {
            estado.erradas = 0
            estado.bloqueadoAte = relogio() + Validacao.BLOQUEIO_PIN_MS
            return 0
        }
        return restam
    }

    fun acerto() {
        estado.erradas = 0
    }
}

/**
 * O estado do bloqueio em SharedPreferences (é uma configuração de segurança, não um registo): reiniciar a app
 * não apaga o bloqueio. Há um só para o tablet: o PIN de administrador, o da chefia e o das aprovações partilham-no.
 */
class EstadoGuardado(context: Context) : BloqueioPin.Estado {
    private val prefs = context.getSharedPreferences("bloqueio_pin", Context.MODE_PRIVATE)

    override var erradas: Int
        get() = prefs.getInt("erradas", 0)
        set(valor) = prefs.edit { putInt("erradas", valor) }

    override var bloqueadoAte: Long
        get() = prefs.getLong("bloqueadoAte", 0)
        set(valor) = prefs.edit { putLong("bloqueadoAte", valor) }

    // As horas dos PINs errados ficam numa só linha de texto; guardam-se só as das últimas 24 h
    override fun registarErro(hora: Long) {
        val recentes = errosDesde(hora - UM_DIA_MS) + hora
        prefs.edit { putString("horasErradas", recentes.joinToString(",")) }
    }

    override fun errosDesde(desde: Long): List<Long> =
        prefs.getString("horasErradas", "").orEmpty().split(",").mapNotNull { it.toLongOrNull() }.filter { it >= desde }

    private companion object {
        const val UM_DIA_MS = 24 * 60 * 60 * 1000L
    }
}
