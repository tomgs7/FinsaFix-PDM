package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AvariaDao {

    companion object {
        // Início comum a todas as consultas de AvariaResumo: junta os nomes do equipamento, da secção e das pessoas.
        private const val SELECT_RESUMO =
            "SELECT a.*, e.nome AS equipamentoNome, s.id AS secaoId, s.nome AS secaoNome, " +
                "r.nome AS reportadaPorNome, t.nome AS tecnicoNome, p.nome AS progressoPorNome, " +
                "(SELECT COUNT(*) FROM avaria_tecnicos x WHERE x.avariaId = a.id AND x.dataSaida IS NULL " +
                "AND x.tecnicoId <> IFNULL(a.tecnicoId, '')) AS ajudantes " +
                "FROM avarias a " +
                "JOIN equipamentos e ON e.id = a.equipamentoId " +
                "JOIN secoes s ON s.id = e.secaoId " +
                "LEFT JOIN pessoas r ON r.id = a.reportadaPorId " +
                "LEFT JOIN pessoas t ON t.id = a.tecnicoId " +
                "LEFT JOIN pessoas p ON p.id = a.progressoPorId "

        private const val POR_PRIORIDADE =
            "CASE a.prioridade WHEN '${Avaria.CRITICA}' THEN 0 WHEN '${Avaria.MEDIA}' THEN 1 ELSE 2 END"

        // O técnico está na equipa da avaria (responsável ou ajudante) neste momento
        private const val NA_EQUIPA =
            "EXISTS (SELECT 1 FROM avaria_tecnicos e WHERE e.avariaId = a.id AND e.tecnicoId = :tecnicoId AND e.dataSaida IS NULL)"

        // Pendente com nota de quem saiu: aparece à parte, em "Em progresso · para retomar".
        private const val EM_PROGRESSO = "(a.estado = '${Avaria.PENDENTE}' AND a.progressoNota IS NOT NULL)"
    }

    @Insert
    suspend fun inserir(avaria: Avaria): Long

    @Update
    suspend fun atualizar(avaria: Avaria)

    @Delete
    suspend fun eliminar(avaria: Avaria)

    @Query("SELECT * FROM avarias WHERE id = :id")
    suspend fun obter(id: Long): Avaria?

    @Query("$SELECT_RESUMO WHERE a.id = :id")
    fun obterResumo(id: Long): Flow<AvariaResumo?>

    /** Avarias de uma secção nos estados pedidos (ex.: pendente e em curso), mais recentes primeiro. */
    @Query("$SELECT_RESUMO WHERE s.id = :secaoId AND a.estado IN (:estados) ORDER BY a.dataCriacao DESC")
    fun obterDaSecao(secaoId: String, estados: List<String>): Flow<List<AvariaResumo>>

    /** Consulta da engenharia: toda a fábrica (com [secaoId] null) ou uma secção. */
    @Query("$SELECT_RESUMO WHERE (:secaoId IS NULL OR s.id = :secaoId) AND a.estado IN (:estados) ORDER BY a.dataCriacao DESC")
    fun obterDaFabrica(secaoId: String?, estados: List<String>): Flow<List<AvariaResumo>>

    /** Consulta do chefe de oficina: a sua especialidade e as "Não sei" (ainda por classificar). */
    @Query("$SELECT_RESUMO WHERE a.especialidade IN (:especialidades) AND a.estado IN (:estados) ORDER BY a.dataCriacao DESC")
    fun obterDasEspecialidades(especialidades: List<String>, estados: List<String>): Flow<List<AvariaResumo>>

    /** Lista principal do técnico: por prioridade e, dentro da mesma prioridade, as mais antigas primeiro. */
    @Query(
        "$SELECT_RESUMO WHERE a.especialidade IN (:especialidades) AND a.estado IN (:estados) " +
            "AND (:tecnicoId IS NULL OR a.atribuidaAId IS NULL OR a.atribuidaAId = :tecnicoId OR $NA_EQUIPA) " +
            "AND NOT $EM_PROGRESSO ORDER BY $POR_PRIORIDADE, a.dataCriacao"
    )
    fun obterDaOficina(especialidades: List<String>, estados: List<String>, tecnicoId: String?): Flow<List<AvariaResumo>>

    @Query("$SELECT_RESUMO WHERE a.especialidade IN (:especialidades) AND $EM_PROGRESSO ORDER BY a.progressoData")
    fun obterEmProgresso(especialidades: List<String>): Flow<List<AvariaResumo>>

    @Query("$SELECT_RESUMO WHERE a.tecnicoId = :tecnicoId AND a.estado = '${Avaria.RESOLVIDA}' ORDER BY a.dataFim DESC LIMIT 20")
    fun obterConcluidasDoTecnico(tecnicoId: String): Flow<List<AvariaResumo>>

    @Query("$SELECT_RESUMO WHERE (a.tecnicoId = :tecnicoId OR $NA_EQUIPA) AND a.estado = '${Avaria.EM_CURSO}' ORDER BY a.dataInicio DESC")
    fun obterDoTecnico(tecnicoId: String): Flow<List<AvariaResumo>>

    @Query("$SELECT_RESUMO WHERE a.estado = '${Avaria.RESOLVIDA}' AND a.especialidade IN (:especialidades) ORDER BY a.dataFim DESC")
    fun obterHistoricoDaOficina(especialidades: List<String>): Flow<List<AvariaResumo>>

    @Query("SELECT COUNT(*) FROM avarias WHERE tecnicoId = :tecnicoId AND estado = '${Avaria.EM_CURSO}'")
    suspend fun contarEmCursoDoTecnico(tecnicoId: String): Int
}
