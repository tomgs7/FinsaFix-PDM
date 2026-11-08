package pt.ubi.di.pdm.finsafix.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Uma quantidade produzida num equipamento, num turno ("M", "T" ou "N"), na unidade da secção. */
@Entity(
    tableName = "producoes",
    foreignKeys = [
        ForeignKey(entity = Equipamento::class, parentColumns = ["id"], childColumns = ["equipamentoId"]),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["pessoaId"])
    ],
    // as listas do turno e os relatórios filtram por data
    indices = [Index("equipamentoId"), Index("pessoaId"), Index("data")]
)
data class Producao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val equipamentoId: String,
    val turno: String,
    val quantidade: Double,
    val pessoaId: String,
    val data: Long,
    val cortes: Int? = null
)
