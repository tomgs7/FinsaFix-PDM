package pt.ubi.di.pdm.finsafix

import android.app.Application
import android.content.Context

/**
 * A primeira leitura de cada ficheiro de SharedPreferences vai ao disco. Se acontecesse no onCreate do ecrã travava o arranque
 * (o StrictMode apanhava-o em Tema, Posto, Bloqueio de PIN e Ajudas). Aqui lêem-se noutra thread logo que o processo começa,
 * e quando o ecrã pede as preferências já estão em memória.
 */
class FinsaFixApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Thread {
            for (ficheiro in listOf("tema", "configuracao", "bloqueio_pin", "ajuda")) {
                getSharedPreferences(ficheiro, Context.MODE_PRIVATE).all
            }
        }.start()
    }
}
