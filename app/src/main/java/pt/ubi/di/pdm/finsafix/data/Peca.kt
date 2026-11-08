package pt.ubi.di.pdm.finsafix.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Uma peça de stock da oficina. [pedidaPorId] e [dataPedido] só têm valor depois de "Pedir reposição". */
@Entity(
    tableName = "pecas",
    foreignKeys = [
        ForeignKey(entity = Equipamento::class, parentColumns = ["id"], childColumns = ["equipamentoId"]),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["pedidaPorId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("equipamentoId"), Index("pedidaPorId")]
)
data class Peca(
    @PrimaryKey val id: String,
    val nome: String,
    val equipamentoId: String,
    val especialidade: String,
    val minimo: Int,
    val stock: Int,
    val pedidaPorId: String? = null,
    val dataPedido: Long? = null
) {
    enum class Estado { RUTURA, ABAIXO, NO_LIMITE, OK }

    fun estado(): Estado = when {
        stock <= 0 -> Estado.RUTURA
        stock < minimo -> Estado.ABAIXO
        stock == minimo -> Estado.NO_LIMITE
        else -> Estado.OK
    }

    val pedida: Boolean get() = pedidaPorId != null
}

data class PecaResumo(
    @Embedded val peca: Peca,
    val secaoNome: String,
    val pedidaPorNome: String?
)
