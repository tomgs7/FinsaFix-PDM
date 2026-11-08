package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Uma paragem com o nome do equipamento, a secção e o código e estado da avaria (se houver), para as listas. */
data class ParagemResumo(
    @Embedded val paragem: Paragem,
    val equipamentoNome: String,
    val secaoNome: String,
    val secaoId: String,
    val unidade: String,
    val avariaCodigo: String?,
    val avariaEstado: String?
) {
    /** Uma paragem causada por avaria só retoma depois de a avaria estar resolvida. */
    fun podeRetomar(): Boolean = paragem.fim == null && (paragem.avariaId == null || avariaEstado == Avaria.RESOLVIDA)
}

@Dao
interface ParagemDao {

    companion object {
        private const val SELECT_RESUMO =
            "SELECT p.*, e.nome AS equipamentoNome, s.nome AS secaoNome, s.id AS secaoId, s.unidade AS unidade, a.codigo AS avariaCodigo, a.estado AS avariaEstado " +
                "FROM paragens p " +
                "JOIN equipamentos e ON e.id = p.equipamentoId " +
                "JOIN secoes s ON s.id = e.secaoId " +
                "LEFT JOIN avarias a ON a.id = p.avariaId "
    }

    @Insert
    suspend fun inserir(paragem: Paragem): Long

    @Insert
    suspend fun inserirTodas(lista: List<Paragem>)

    @Query("SELECT * FROM paragens WHERE id = :id")
    suspend fun obter(id: Long): Paragem?

    @Query("SELECT * FROM paragens WHERE equipamentoId = :equipamentoId AND fim IS NULL LIMIT 1")
    suspend fun obterAtivaDoEquipamento(equipamentoId: String): Paragem?

    @Query("SELECT * FROM paragens WHERE avariaId = :avariaId")
    suspend fun obterDaAvaria(avariaId: Long): List<Paragem>

    @Query("SELECT * FROM paragens WHERE avariaId = :avariaId AND fim IS NULL LIMIT 1")
    fun observarAtivaDaAvaria(avariaId: Long): Flow<Paragem?>

    @Query("UPDATE paragens SET fim = :fim WHERE id = :id AND fim IS NULL")
    suspend fun terminar(id: Long, fim: Long)

    @Query("DELETE FROM paragens WHERE avariaId = :avariaId")
    suspend fun eliminarDaAvaria(avariaId: Long)

    /**
     * Paragens ativas e as que começaram desde [desde] (ex.: há 7 dias), mais recentes primeiro.
     * [secaoId] nulo = todas as secções (técnico).
     */
    @Query(
        "$SELECT_RESUMO WHERE (:secaoId IS NULL OR s.id = :secaoId) AND (p.fim IS NULL OR p.inicio >= :desde) " +
            "ORDER BY p.fim IS NOT NULL, p.inicio DESC"
    )
    fun obterRecentes(secaoId: String?, desde: Long): Flow<List<ParagemResumo>>

    // As que ainda não terminaram (fim nulo); secaoId nulo = toda a fábrica
    @Query("$SELECT_RESUMO WHERE (:secaoId IS NULL OR s.id = :secaoId) AND p.fim IS NULL ORDER BY p.inicio DESC")
    fun obterAtivas(secaoId: String?): Flow<List<ParagemResumo>>
}
