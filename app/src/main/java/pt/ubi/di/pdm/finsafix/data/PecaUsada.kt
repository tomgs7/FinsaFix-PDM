package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Uma peça substituída numa intervenção: o técnico regista-a e o stock desce. */
@Entity(
    tableName = "pecas_usadas",
    foreignKeys = [
        ForeignKey(entity = Avaria::class, parentColumns = ["id"], childColumns = ["avariaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Peca::class, parentColumns = ["id"], childColumns = ["pecaId"]),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["tecnicoId"])
    ],
    indices = [Index("avariaId"), Index("pecaId"), Index("tecnicoId")]
)
data class PecaUsada(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val avariaId: Long,
    val pecaId: String,
    val quantidade: Int,
    val tecnicoId: String,
    val data: Long
)

data class PecaUsadaResumo(
    @Embedded val usada: PecaUsada,
    val pecaNome: String
)

@Dao
interface PecaUsadaDao {

    @Insert
    suspend fun inserir(usada: PecaUsada)

    @Query(
        "SELECT u.*, p.nome AS pecaNome FROM pecas_usadas u JOIN pecas p ON p.id = u.pecaId " +
            "WHERE u.avariaId = :avariaId ORDER BY u.data"
    )
    fun obterDaAvaria(avariaId: Long): Flow<List<PecaUsadaResumo>>
}
