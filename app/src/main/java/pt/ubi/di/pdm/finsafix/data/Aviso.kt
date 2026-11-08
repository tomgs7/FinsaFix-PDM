package pt.ubi.di.pdm.finsafix.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Um aviso a uma secção. [secaoId] é quem o vê.
 * - PARAR / ARRANCAR: o técnico avisa a secção; aparece como banner até alguém responder.
 * - A_CAMINHO / ESPERA_PECA / DEMORA: avisos informativos do técnico (aparecem em “Avisos recentes”).
 * - FORA_LIMITE: o operador avisa o chefe de um parâmetro fora do limite ([texto] diz qual); não tem banner.
 * Se a avaria for eliminada, o aviso fica sem avaria (SET_NULL) mas não se perde.
 */
@Entity(
    tableName = "avisos",
    foreignKeys = [
        ForeignKey(entity = Secao::class, parentColumns = ["id"], childColumns = ["secaoId"]),
        ForeignKey(entity = Equipamento::class, parentColumns = ["id"], childColumns = ["equipamentoId"]),
        ForeignKey(entity = Avaria::class, parentColumns = ["id"], childColumns = ["avariaId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["criadoPorId"]),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["respondidoPorId"], onDelete = ForeignKey.SET_NULL)
    ],
    // "avisos desta secção ainda sem resposta": secaoId e dataResposta nula
    indices = [Index("secaoId", "dataResposta"), Index("equipamentoId"), Index("avariaId"), Index("criadoPorId"), Index("respondidoPorId")]
)
data class Aviso(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val secaoId: String,
    val tipo: String,
    val equipamentoId: String? = null,
    val avariaId: Long? = null,
    val texto: String? = null,
    val criadoPorId: String,
    val dataCriacao: Long,
    val respondidoPorId: String? = null,
    val dataResposta: Long? = null
) {
    companion object {
        /** Um aviso sem resposta fica em destaque para o chefe ao fim de 3 minutos. */
        const val DESTAQUE_MS = 3 * 60_000L

        const val PARAR = "parar"
        const val ARRANCAR = "arrancar"
        const val FORA_LIMITE = "fora_limite"

        // Avisos só informativos do técnico: não têm banner e o chefe marca-os como vistos
        const val A_CAMINHO = "a_caminho"
        const val ESPERA_PECA = "espera_peca"
        const val DEMORA = "demora"
    }
}
