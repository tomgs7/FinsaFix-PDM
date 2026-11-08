package pt.ubi.di.pdm.finsafix.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Uma paragem de um equipamento. fim == null quer dizer que ainda está parado.
 * Se a causa for uma avaria, avariaId liga-a à avaria; se a avaria for eliminada, a ligação fica vazia (SET_NULL).
 */
@Entity(
    tableName = "paragens",
    foreignKeys = [
        ForeignKey(entity = Equipamento::class, parentColumns = ["id"], childColumns = ["equipamentoId"]),
        ForeignKey(entity = Avaria::class, parentColumns = ["id"], childColumns = ["avariaId"], onDelete = ForeignKey.SET_NULL)
    ],
    // "a paragem ativa deste equipamento" procura equipamentoId com fim nulo
    // "ativas" procuram fim nulo (todas as secções) e "recentes" procuram por início
    indices = [Index("equipamentoId", "fim"), Index("avariaId"), Index("fim"), Index("inicio")]
)
data class Paragem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val equipamentoId: String,
    val causa: String,
    val inicio: Long,
    val fim: Long? = null,
    val avariaId: Long? = null
)
