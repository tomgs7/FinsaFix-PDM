package pt.ubi.di.pdm.finsafix

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.ubi.di.pdm.finsafix.data.AppDatabase
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.CarregadorDadosDemo

/** Os dados de demonstração carregam-se só quando a base de dados está vazia (a abertura seguinte não os repete). */
@RunWith(AndroidJUnit4::class)
class DadosDemoTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun criarBaseDeDados() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun fecharBaseDeDados() = db.close()

    @Test
    fun carrega_numa_base_vazia_e_nao_repete_na_abertura_seguinte() = runBlocking {
        val carregador = CarregadorDadosDemo(context)
        carregador.carregarSeVazia(db) // primeira abertura: base vazia
        val secoes = db.secaoDao().contar()
        val avarias = db.avariaDao().obterDaFabrica(null, Avaria.ESTADOS).first().size
        assertTrue(secoes > 0 && avarias > 0)

        carregador.carregarSeVazia(db) // aberturas seguintes: já há secções, não duplica nem apaga nada
        assertEquals(secoes, db.secaoDao().contar())
        assertEquals(avarias, db.avariaDao().obterDaFabrica(null, Avaria.ESTADOS).first().size)
    }
}
