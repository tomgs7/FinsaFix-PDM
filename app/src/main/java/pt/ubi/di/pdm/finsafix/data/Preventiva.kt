package pt.ubi.di.pdm.finsafix.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Um plano de manutenção preventiva de um equipamento ("Troca das cintas de lixa, cada 80 h").
 * Guarda-se só quando foi feita pela última vez; as horas decorridas calculam-se com a hora atual.
 */
@Entity(
    tableName = "preventivas",
    foreignKeys = [
        ForeignKey(entity = Equipamento::class, parentColumns = ["id"], childColumns = ["equipamentoId"]),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["ultimaPorId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("equipamentoId"), Index("ultimaPorId")]
)
data class Preventiva(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val equipamentoId: String,
    val titulo: String,
    val especialidade: String,
    val intervaloH: Int,
    val ultimaData: Long,
    val ultimaPorId: String? = null
) {
    enum class Estado { VENCIDA, A_VENCER, EM_DIA }

    fun horasDesdeUltima(agora: Long): Long = (agora - ultimaData).coerceAtLeast(0) / HORA_MS

    /** Vencida a partir do intervalo; "a vencer" quando já passou 85 % dele. */
    fun estado(agora: Long): Estado {
        val horas = horasDesdeUltima(agora)
        return when {
            horas >= intervaloH -> Estado.VENCIDA
            horas * 100 >= intervaloH * LIMITE_A_VENCER -> Estado.A_VENCER
            else -> Estado.EM_DIA
        }
    }

    companion object {
        const val HORA_MS = 3_600_000L
        const val LIMITE_A_VENCER = 85
    }
}

data class PreventivaResumo(
    @Embedded val preventiva: Preventiva,
    val equipamentoNome: String,
    val secaoNome: String,
    val ultimaPorNome: String?
)
