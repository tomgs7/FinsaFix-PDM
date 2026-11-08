package pt.ubi.di.pdm.finsafix.util

import androidx.core.content.edit
import pt.ubi.di.pdm.finsafix.data.Turno
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Modo escuro. Se ninguém escolheu, liga sozinho no turno da noite; o interruptor de "Mais" fixa a escolha.
 * A escolha fica em SharedPreferences (é uma configuração, não um registo).
 */
object Tema {

    private const val FICHEIRO = "tema"
    private const val CHAVE = "modo_escuro"
    private const val SEM_ESCOLHA = -1

    fun estaEscuro(context: Context): Boolean = when (escolha(context)) {
        SEM_ESCOLHA -> Datas.turnoAtual() == Turno.NOITE
        else -> escolha(context) == 1
    }

    fun aplicar(context: Context) {
        AppCompatDelegate.setDefaultNightMode(if (estaEscuro(context)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
    }

    /** Guarda a escolha e aplica-a (a Activity recria-se sozinha). */
    fun definir(context: Context, escuro: Boolean) {
        context.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE).edit { putInt(CHAVE, if (escuro) 1 else 0) }
        aplicar(context)
    }

    private fun escolha(context: Context): Int =
        context.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE).getInt(CHAVE, SEM_ESCOLHA)
}
