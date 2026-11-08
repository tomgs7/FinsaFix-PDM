package pt.ubi.di.pdm.finsafix.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Uma instrução de trabalho de uma secção. Só se lê (a engenharia é que as valida).
 * Os [passos] ficam num só texto, um passo por linha ("Título — detalhe").
 */
@Entity(
    tableName = "instrucoes",
    foreignKeys = [
        ForeignKey(entity = Secao::class, parentColumns = ["id"], childColumns = ["secaoId"]),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["validadaPorId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("secaoId"), Index("validadaPorId")]
)
data class Instrucao(
    @PrimaryKey val id: String,
    val secaoId: String,
    val titulo: String,
    val duracaoMin: Int,
    val passos: String,
    val estado: String,
    val validadaPorId: String? = null,
    val dataValidacao: Long? = null
) {
    fun listaDePassos(): List<String> = passos.split("\n").filter { it.isNotBlank() }

    companion object {
        const val VALIDADA = "validada"
        const val POR_VALIDAR = "por_validar"
        const val CORRECAO = "correcao"
    }
}

data class InstrucaoResumo(
    @Embedded val instrucao: Instrucao,
    val secaoNome: String,
    val validadaPorNome: String?
)
