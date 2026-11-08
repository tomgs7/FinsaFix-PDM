package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SecaoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(secoes: List<Secao>)

    @Query("SELECT COUNT(*) FROM secoes")
    suspend fun contar(): Int

    @Query("SELECT * FROM secoes ORDER BY tipo DESC, nome")
    fun obterTodas(): Flow<List<Secao>>

    @Query("SELECT * FROM secoes WHERE id = :id")
    fun obter(id: String): Flow<Secao?>

    @Query("SELECT * FROM secoes WHERE id = :id")
    suspend fun obterUma(id: String): Secao?
}
