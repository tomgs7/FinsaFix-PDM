package pt.ubi.di.pdm.finsafix.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Autorização de trabalho (trabalho a quente, consignação…) para uma intervenção.
 * Estados, sem saltos: pedida → aprovada (ou recusada) → em curso → encerrada.
 * [secaoId] é a secção do equipamento: só o chefe dessa secção decide.
 * Em curso: [dataTrabalhoTerminado] diz que o técnico já acabou e que falta o chefe encerrar
 * (no trabalho a quente, só 60 minutos depois: vigilância de fogo).
 */
@Entity(
    tableName = "autorizacoes",
    foreignKeys = [
        ForeignKey(entity = Secao::class, parentColumns = ["id"], childColumns = ["secaoId"]),
        // sem avaria a autorização não faz sentido: apaga-se com ela
        ForeignKey(entity = Avaria::class, parentColumns = ["id"], childColumns = ["avariaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["pedidaPorId"]),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["decididaPorId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["encerradaPorId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [
        // "autorizações abertas da secção" e "autorizações abertas da avaria"
        Index("secaoId", "estado"), Index("avariaId", "estado"), Index("pedidaPorId"), Index("decididaPorId"), Index("encerradaPorId")
    ]
)
data class Autorizacao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val secaoId: String,
    val avariaId: Long,
    val tipo: String,
    val descricao: String,
    val estado: String,
    val pedidaPorId: String,
    val dataPedido: Long,
    val decididaPorId: String? = null,
    val dataDecisao: Long? = null,
    val motivoRecusa: String? = null,
    val cadeado: String? = null,
    val dataInicio: Long? = null,
    val dataTrabalhoTerminado: Long? = null,
    val encerradaPorId: String? = null,
    val dataFim: Long? = null,
    val duracaoMin: Int = 60,
    val vigilante: String? = null,
    val validaAte: Long? = null
) {
    /** Aprovada ou em curso, mas já passou o fim do turno em que foi aprovada: o chefe seguinte tem de a renovar. */
    fun expirada(agora: Long): Boolean = validaAte != null && agora > validaAte && (estado == APROVADA || estado == EM_CURSO)

    /** Os tipos pedidos (o campo [tipo] guarda-os separados por vírgula, ex.: "quente,altura"). */
    fun tipos(): List<String> = tipo.split(",")

    fun temQuente(): Boolean = QUENTE in tipos()

    /** A consignação e o bloqueio mecânico pedem o número do cadeado ao começar. */
    fun exigeCadeado(): Boolean = tipos().any { it == CONSIGNACAO || it == BLOQUEIO }

    /** Milissegundos que faltam da vigilância de fogo (só no trabalho a quente, depois de o trabalho terminar). */
    fun vigilanciaRestanteMs(agora: Long): Long {
        val terminado = dataTrabalhoTerminado ?: return 0
        if (!temQuente()) return 0
        return (terminado + VIGILANCIA_MS - agora).coerceAtLeast(0)
    }

    /** Uma autorização aberta impede concluir a avaria, exceto a vigilância de fogo (quente com o trabalho já terminado). */
    fun bloqueiaConclusao(): Boolean {
        val aberta = estado == PEDIDA || estado == APROVADA || estado == EM_CURSO
        val emVigilancia = estado == EM_CURSO && tipo == QUENTE && dataTrabalhoTerminado != null
        return aberta && !emVigilancia
    }

    companion object {
        const val PEDIDA = "pedida"
        const val APROVADA = "aprovada"
        const val RECUSADA = "recusada"
        const val EM_CURSO = "em_curso"
        const val ENCERRADA = "encerrada"

        const val QUENTE = "quente"
        const val CONSIGNACAO = "loto_el"
        const val BLOQUEIO = "loto_mec"
        const val ALTURA = "altura"
        const val CONFINADO = "confinado"

        val TIPOS = listOf(QUENTE, CONSIGNACAO, BLOQUEIO, ALTURA, CONFINADO)

        /** Vigilância de fogo depois do trabalho a quente: 60 minutos. */
        const val VIGILANCIA_MS = 60 * 60 * 1000L
    }
}
