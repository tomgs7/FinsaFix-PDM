package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Um aviso com os nomes do equipamento (se houver), de quem o enviou e de quem respondeu. */
data class AvisoResumo(
    @Embedded val aviso: Aviso,
    val equipamentoNome: String?,
    val criadoPorNome: String,
    val respondidoPorNome: String?
)

@Dao
interface AvisoDao {

    companion object {
        private const val SELECT_RESUMO =
            "SELECT a.*, e.nome AS equipamentoNome, c.nome AS criadoPorNome, r.nome AS respondidoPorNome " +
                "FROM avisos a " +
                "LEFT JOIN equipamentos e ON e.id = a.equipamentoId " +
                "JOIN pessoas c ON c.id = a.criadoPorId " +
                "LEFT JOIN pessoas r ON r.id = a.respondidoPorId "
    }

    @Insert
    suspend fun inserir(aviso: Aviso): Long

    @Update
    suspend fun atualizar(aviso: Aviso)

    @Query("SELECT * FROM avisos WHERE id = :id")
    suspend fun obter(id: Long): Aviso?

    @Query("SELECT COUNT(*) FROM avisos WHERE avariaId = :avariaId AND tipo = :tipo AND dataResposta IS NULL")
    suspend fun contarPorResponder(avariaId: Long, tipo: String): Int

    /** O aviso urgente (banner) mais antigo ainda sem resposta; os seguintes aparecem à medida que este é respondido. */
    @Query(
        "$SELECT_RESUMO WHERE a.secaoId = :secaoId AND a.tipo IN ('${Aviso.PARAR}', '${Aviso.ARRANCAR}') AND a.dataResposta IS NULL " +
            "ORDER BY a.dataCriacao LIMIT 1"
    )
    fun obterUrgente(secaoId: String): Flow<AvisoResumo?>

    /** Os avisos da secção que ainda esperam resposta, os mais antigos primeiro (o ecrã Pedidos do chefe). */
    @Query("$SELECT_RESUMO WHERE a.secaoId = :secaoId AND a.dataResposta IS NULL ORDER BY a.dataCriacao")
    fun obterPorResponder(secaoId: String): Flow<List<AvisoResumo>>

    /** "Avisos recentes": os 10 mais recentes da secção, respondidos ou não. */
    @Query("$SELECT_RESUMO WHERE a.secaoId = :secaoId ORDER BY a.dataCriacao DESC LIMIT 10")
    fun obterRecentes(secaoId: String): Flow<List<AvisoResumo>>
}
