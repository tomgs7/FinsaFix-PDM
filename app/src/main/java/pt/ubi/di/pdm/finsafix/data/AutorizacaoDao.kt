package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Uma autorização com o código da avaria, o equipamento e quem a pediu, para as listas. */
data class AutorizacaoResumo(
    @Embedded val autorizacao: Autorizacao,
    val avariaCodigo: String,
    val equipamentoNome: String,
    val pedidaPorNome: String
)

@Dao
interface AutorizacaoDao {

    companion object {
        private const val SELECT_RESUMO =
            "SELECT a.*, v.codigo AS avariaCodigo, e.nome AS equipamentoNome, p.nome AS pedidaPorNome " +
                "FROM autorizacoes a " +
                "JOIN avarias v ON v.id = a.avariaId " +
                "JOIN equipamentos e ON e.id = v.equipamentoId " +
                "JOIN pessoas p ON p.id = a.pedidaPorId "
    }

    @Insert
    suspend fun inserir(autorizacao: Autorizacao): Long

    @Insert
    suspend fun inserirTodas(lista: List<Autorizacao>)

    @Update
    suspend fun atualizar(autorizacao: Autorizacao)

    @Query("SELECT * FROM autorizacoes WHERE id = :id")
    suspend fun obter(id: Long): Autorizacao?

    /** A autorização mais recente da avaria (a que o técnico vê no detalhe). */
    @Query("$SELECT_RESUMO WHERE a.avariaId = :avariaId ORDER BY a.id DESC LIMIT 1")
    fun obterAtualDaAvaria(avariaId: Long): Flow<AutorizacaoResumo?>

    /** Autorizações abertas da secção (as pedidas primeiro): a lista do ecrã Validar do chefe. */
    @Query(
        "$SELECT_RESUMO WHERE a.secaoId = :secaoId AND a.estado IN ('${Autorizacao.PEDIDA}', '${Autorizacao.APROVADA}', '${Autorizacao.EM_CURSO}') " +
            "ORDER BY CASE a.estado WHEN '${Autorizacao.PEDIDA}' THEN 0 WHEN '${Autorizacao.EM_CURSO}' THEN 1 ELSE 2 END, a.dataPedido"
    )
    fun obterAbertasDaSecao(secaoId: String): Flow<List<AutorizacaoResumo>>

    /** Autorizações aprovadas ou em curso das avarias destas oficinas (o que passa para o turno seguinte). */
    @Query(
        "$SELECT_RESUMO WHERE v.especialidade IN (:especialidades) AND a.estado IN ('${Autorizacao.APROVADA}', '${Autorizacao.EM_CURSO}') " +
            "ORDER BY a.validaAte"
    )
    fun obterAtivasDasEspecialidades(especialidades: List<String>): Flow<List<AutorizacaoResumo>>

    /** Todas as autorizações da fábrica, as mais recentes primeiro (o separador “Autorizações” da engenharia). */
    @Query("$SELECT_RESUMO ORDER BY a.dataPedido DESC")
    fun obterTodas(): Flow<List<AutorizacaoResumo>>

    /** O que está à espera do chefe: pedidas, e em curso com o trabalho já terminado (falta encerrar). */
    @Query(
        "SELECT COUNT(*) FROM autorizacoes WHERE secaoId = :secaoId " +
            "AND (estado = '${Autorizacao.PEDIDA}' OR (estado = '${Autorizacao.EM_CURSO}' AND dataTrabalhoTerminado IS NOT NULL))"
    )
    fun contarPorTratar(secaoId: String): Flow<Int>

    @Query("SELECT * FROM autorizacoes WHERE avariaId = :avariaId AND estado IN ('${Autorizacao.PEDIDA}', '${Autorizacao.APROVADA}', '${Autorizacao.EM_CURSO}')")
    suspend fun obterAbertasDaAvaria(avariaId: Long): List<Autorizacao>
}
