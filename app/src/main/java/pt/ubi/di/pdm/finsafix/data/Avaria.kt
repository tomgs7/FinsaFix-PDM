package pt.ubi.di.pdm.finsafix.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * O registo principal da aplicação. As datas são milissegundos desde 1970 (Long).
 * Os campos "progresso*" só têm valor quando a intervenção foi deixada em progresso.
 * As chaves estrangeiras impedem gravar uma avaria de um equipamento ou pessoa que não existe.
 */
@Entity(
    tableName = "avarias",
    // Quem reporta e o equipamento são obrigatórios (sem onDelete: a base de dados recusa apagá-los, o histórico não
    // perde o autor). As pessoas que podem ser nulas passam a nulo (SET_NULL) se forem apagadas.
    foreignKeys = [
        ForeignKey(entity = Equipamento::class, parentColumns = ["id"], childColumns = ["equipamentoId"]),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["reportadaPorId"]),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["tecnicoId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["progressoPorId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["atribuidaAId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["aprovadaPorId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [
        Index("codigo", unique = true),
        Index("equipamentoId"),
        Index("reportadaPorId"),
        // as intervenções do técnico (em curso e concluídas) filtram por técnico e estado; o prefixo tecnicoId serve a chave estrangeira
        Index("tecnicoId", "estado"),
        Index("progressoPorId"),
        Index("atribuidaAId"),
        Index("aprovadaPorId"),
        // quase todas as listas filtram por estado e especialidade
        Index("estado", "especialidade")
    ]
)
data class Avaria(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codigo: String,
    val equipamentoId: String,
    val titulo: String,
    val descricao: String,
    val prioridade: String,
    val especialidade: String,
    val estado: String,
    val reportadaPorId: String,
    val dataCriacao: Long,
    val tecnicoId: String? = null,
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val tempoParadoMs: Long = 0,
    val progressoOrdem: String? = null,
    val progressoRetoma: String? = null,
    val progressoMotivo: String? = null,
    val progressoNota: String? = null,
    val progressoPorId: String? = null,
    val progressoData: Long? = null,
    val aprovadaPorId: String? = null,
    val dataAprovacao: Long? = null,
    val atribuidaAId: String? = null
) {
    /** Resolvida mas ainda sem a aprovação do fecho pela engenharia. */
    fun porAprovar(): Boolean = estado == RESOLVIDA && aprovadaPorId == null

    /** Editar e eliminar: só quem reportou, e só enquanto ninguém começou a tratar da avaria. */
    fun podeSerAlteradaPor(pessoaId: String): Boolean =
        estado == PENDENTE && dataInicio == null && reportadaPorId == pessoaId

    /**
     * Tempo de intervenção, sem contar o tempo em que esteve deixada em progresso.
     * Devolve null se a intervenção ainda não começou.
     */
    fun duracaoMs(agora: Long): Long? {
        val inicio = dataInicio ?: return null
        val fim = dataFim ?: progressoData ?: agora
        return (fim - inicio - tempoParadoMs).coerceAtLeast(0)
    }

    companion object {
        const val PENDENTE = "pendente"
        const val EM_CURSO = "em_curso"
        const val RESOLVIDA = "resolvida"
        val ESTADOS = listOf(PENDENTE, EM_CURSO, RESOLVIDA)

        const val CRITICA = "critica"
        const val MEDIA = "media"
        const val BAIXA = "baixa"

        const val MECANICA = "mecanica"
        const val ELETRICA = "eletrica"
        const val NAO_SEI = "nao_sei"

        const val RETOMA_TURNO = "turno"
        const val RETOMA_DIA = "dia"
    }
}
