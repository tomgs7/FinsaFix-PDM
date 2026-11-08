package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PessoaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(pessoas: List<Pessoa>)

    /** Quem aparece no ecrã de entrada: operadores e técnicos do posto (a chefia entra por PIN). */
    @Query(
        "SELECT * FROM pessoas WHERE postoId = :postoId AND papel IN ('${Pessoa.PAPEL_OPERADOR}', '${Pessoa.PAPEL_TECNICO}') " +
            "ORDER BY CASE turno WHEN '${Turno.MANHA}' THEN 0 WHEN '${Turno.TARDE}' THEN 1 ELSE 2 END, nome"
    )
    fun obterDoPosto(postoId: String): Flow<List<Pessoa>>

    @Query("SELECT * FROM pessoas WHERE id = :id")
    suspend fun obter(id: String): Pessoa?

    /** Os chefes de um posto (secção ou oficina). */
    @Query("SELECT * FROM pessoas WHERE papel = '${Pessoa.PAPEL_CHEFE}' AND postoId = :postoId")
    suspend fun obterChefesDoPosto(postoId: String): List<Pessoa>

    @Query("SELECT * FROM pessoas WHERE papel = '${Pessoa.PAPEL_TECNICO}' ORDER BY nome")
    fun observarTecnicos(): Flow<List<Pessoa>>

    @Query("SELECT * FROM pessoas WHERE papel = '${Pessoa.PAPEL_ENGENHEIRO}'")
    suspend fun obterEngenheiros(): List<Pessoa>
}
