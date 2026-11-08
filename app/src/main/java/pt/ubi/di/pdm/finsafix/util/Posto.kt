package pt.ubi.di.pdm.finsafix.util

import androidx.core.content.edit
import android.content.Context

/**
 * A secção ou oficina onde o tablet está instalado. É uma configuração do dispositivo
 * (não um registo), por isso fica em SharedPreferences.
 */
object Posto {

    private const val FICHEIRO = "configuracao"
    private const val CHAVE_POSTO = "posto_id"
    private const val POSTO_POR_OMISSAO = "SCHEL"
    private const val CHAVE_DE = "alteracao_de"
    private const val CHAVE_PARA = "alteracao_para"
    private const val CHAVE_QUANDO = "alteracao_quando"

    fun obterId(context: Context): String =
        context.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE)
            .getString(CHAVE_POSTO, POSTO_POR_OMISSAO) ?: POSTO_POR_OMISSAO

    /** A última mudança de posto: nomes (de → para) e quando aconteceu. */
    class Alteracao(val de: String, val para: String, val quando: Long)

    fun ultimaAlteracao(context: Context): Alteracao? {
        val preferencias = context.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE)
        val de = preferencias.getString(CHAVE_DE, null) ?: return null
        val para = preferencias.getString(CHAVE_PARA, null) ?: return null
        return Alteracao(de, para, preferencias.getLong(CHAVE_QUANDO, 0))
    }

    fun registarAlteracao(context: Context, de: String, para: String) {
        context.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE).edit {
            putString(CHAVE_DE, de)
            putString(CHAVE_PARA, para)
            putLong(CHAVE_QUANDO, System.currentTimeMillis())
        }
    }

    fun guardar(context: Context, postoId: String) {
        context.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE).edit { putString(CHAVE_POSTO, postoId) }
    }
}
