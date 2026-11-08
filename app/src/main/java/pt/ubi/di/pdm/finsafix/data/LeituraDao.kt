package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface LeituraDao {

    @Insert
    suspend fun inserir(leitura: Leitura)

    @Insert
    suspend fun inserirTodas(lista: List<Leitura>)
}
