package pt.ubi.di.pdm.finsafix.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Uma linha da cronologia ("quem fez o quê"). Ao eliminar a avaria, os eventos são eliminados (CASCADE). */
@Entity(
    tableName = "eventos_avaria",
    foreignKeys = [
        ForeignKey(entity = Avaria::class, parentColumns = ["id"], childColumns = ["avariaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["pessoaId"], onDelete = ForeignKey.SET_NULL)
    ],
    // a cronologia lê os eventos de uma avaria por ordem de data
    indices = [Index("avariaId", "data"), Index("pessoaId")]
)
data class EventoAvaria(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val avariaId: Long,
    val texto: String,
    val pessoaId: String?,
    val data: Long
)
