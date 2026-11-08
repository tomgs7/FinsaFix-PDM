package pt.ubi.di.pdm.finsafix.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "equipamentos",
    foreignKeys = [ForeignKey(entity = Secao::class, parentColumns = ["id"], childColumns = ["secaoId"])],
    indices = [Index("secaoId")]
)
data class Equipamento(
    @PrimaryKey val id: String,
    val nome: String,
    val secaoId: String
)
