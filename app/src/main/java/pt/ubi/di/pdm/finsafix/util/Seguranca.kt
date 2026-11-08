package pt.ubi.di.pdm.finsafix.util

import java.security.MessageDigest

/** Os PINs nunca se guardam em texto: guarda-se o SHA-256 de "finsafix:<sal>:<pin>". */
object Seguranca {

    private const val PREFIXO = "finsafix"

    // Hash do PIN de administrador (sal "admin"). O PIN de demonstração está no README.
    private const val HASH_ADMINISTRADOR = "84c3852e3a9cc681b463b656d5d5f2c4fb9abafb93e92cfbb3363ba2fa1097d4"

    fun hashPin(sal: String, pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$PREFIXO:$sal:$pin".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun pinAdministradorCorreto(pin: String): Boolean = hashPin("admin", pin) == HASH_ADMINISTRADOR
}
