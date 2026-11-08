package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * O que está planeado produzir num equipamento, num turno (a ordem repete-se todos os dias).
 * O chefe da secção ajusta [planeado] (−5 −1 +1 +5) e fica registado quem e quando.
 */
@Entity(
    tableName = "ordens_producao",
    foreignKeys = [
        ForeignKey(entity = Secao::class, parentColumns = ["id"], childColumns = ["secaoId"]),
        ForeignKey(entity = Equipamento::class, parentColumns = ["id"], childColumns = ["equipamentoId"]),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["ajustadaPorId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("secaoId"), Index("equipamentoId"), Index("ajustadaPorId")]
)
data class OrdemProducao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val secaoId: String,
    val equipamentoId: String,
    val turno: String,
    val planeado: Double,
    val ajustadaPorId: String? = null,
    val dataAjuste: Long? = null
)

/** Uma ordem com o nome do equipamento e de quem a ajustou, para as listas. */
data class OrdemResumo(
    @Embedded val ordem: OrdemProducao,
    val equipamentoNome: String,
    val ajustadaPorNome: String?
)

@Dao
interface OrdemProducaoDao {

    @Insert
    suspend fun inserirTodas(lista: List<OrdemProducao>)

    @Update
    suspend fun atualizar(ordem: OrdemProducao)

    @Query("SELECT * FROM ordens_producao WHERE id = :id")
    suspend fun obter(id: Long): OrdemProducao?

    @Query(
        "SELECT o.*, e.nome AS equipamentoNome, p.nome AS ajustadaPorNome FROM ordens_producao o " +
            "JOIN equipamentos e ON e.id = o.equipamentoId LEFT JOIN pessoas p ON p.id = o.ajustadaPorId " +
            "WHERE o.secaoId = :secaoId AND o.turno = :turno ORDER BY e.id"
    )
    fun obterDoTurno(secaoId: String, turno: String): Flow<List<OrdemResumo>>
}
