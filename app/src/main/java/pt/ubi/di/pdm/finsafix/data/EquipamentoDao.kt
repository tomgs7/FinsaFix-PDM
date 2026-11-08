package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Quantos equipamentos tem cada secção (lista de “Configurar posto”). */
data class ContagemEquipamentos(val secaoId: String, val total: Int)

@Dao
interface EquipamentoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(equipamentos: List<Equipamento>)

    @Query("SELECT * FROM equipamentos WHERE secaoId = :secaoId ORDER BY id")
    fun obterPorSecao(secaoId: String): Flow<List<Equipamento>>

    @Query("SELECT * FROM equipamentos ORDER BY id")
    suspend fun obterTodos(): List<Equipamento>

    @Query("SELECT * FROM equipamentos ORDER BY nome")
    fun observarTodos(): Flow<List<Equipamento>>

    @Query("SELECT secaoId, COUNT(*) AS total FROM equipamentos GROUP BY secaoId")
    fun contarPorSecao(): Flow<List<ContagemEquipamentos>>

    @Query("SELECT secaoId FROM equipamentos WHERE id = :id")
    suspend fun obterSecaoId(id: String): String?
}
