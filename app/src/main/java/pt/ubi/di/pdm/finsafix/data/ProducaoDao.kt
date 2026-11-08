package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Um registo de produção com o nome do equipamento e de quem registou. */
data class ProducaoResumo(
    @Embedded val producao: Producao,
    val equipamentoNome: String,
    val pessoaNome: String
)

@Dao
interface ProducaoDao {

    @Insert
    suspend fun inserir(producao: Producao)

    @Query("SELECT * FROM producoes WHERE id = :id")
    suspend fun obter(id: Long): Producao?

    @Query("DELETE FROM producoes WHERE id = :id")
    suspend fun eliminar(id: Long)

    /** Registos da secção para o [turno], feitos desde [desde] (o início desse turno), mais recentes primeiro. */
    @Query(
        "SELECT pr.*, e.nome AS equipamentoNome, pe.nome AS pessoaNome FROM producoes pr " +
            "JOIN equipamentos e ON e.id = pr.equipamentoId " +
            "JOIN pessoas pe ON pe.id = pr.pessoaId " +
            "WHERE e.secaoId = :secaoId AND pr.turno = :turno AND pr.data >= :desde ORDER BY pr.data DESC"
    )
    fun obterDoTurno(secaoId: String, turno: String, desde: Long): Flow<List<ProducaoResumo>>

    /** Produção desde uma data, de uma secção ou de toda a fábrica (secaoId nulo): os relatórios. */
    @Query(
        "SELECT pr.*, e.nome AS equipamentoNome, pe.nome AS pessoaNome FROM producoes pr " +
            "JOIN equipamentos e ON e.id = pr.equipamentoId JOIN pessoas pe ON pe.id = pr.pessoaId " +
            "WHERE (:secaoId IS NULL OR e.secaoId = :secaoId) AND pr.data >= :desde ORDER BY pr.data DESC"
    )
    fun obterDesde(secaoId: String?, desde: Long): Flow<List<ProducaoResumo>>
}
