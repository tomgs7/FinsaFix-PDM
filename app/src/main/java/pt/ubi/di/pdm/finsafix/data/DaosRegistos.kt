package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RegistoDao {

    companion object {
        private const val SELECT_RESUMO =
            "SELECT r.*, p.nome AS pessoaNome, t.nome AS tratadoPorNome, s.nome AS secaoNome FROM registos r " +
                "JOIN pessoas p ON p.id = r.pessoaId " +
                "LEFT JOIN pessoas t ON t.id = r.tratadoPorId " +
                "JOIN secoes s ON s.id = r.secaoId "
    }

    @Insert
    suspend fun inserir(registo: Registo): Long

    @Insert
    suspend fun inserirTodos(lista: List<Registo>)

    @Update
    suspend fun atualizar(registo: Registo)

    @Query("SELECT * FROM registos WHERE id = :id")
    suspend fun obter(id: Long): Registo?

    /** Os registos de uns tipos numa secção, os mais recentes primeiro. */
    @Query(
        "$SELECT_RESUMO WHERE r.secaoId = :secaoId AND r.tipo IN (:tipos) " +
            "AND (r.tipo != '${Registo.NOTA}' OR r.resposta IS NULL OR r.resposta != '${Registo.DESTINO_CHEFE}') ORDER BY r.dataCriacao DESC"
    )
    fun obterDaSecao(secaoId: String, tipos: List<String>): Flow<List<RegistoResumo>>

    /**
     * As notas que outro chefe de oficina deixou para este: só as escritas depois da última vez que este confirmou a receção
     * (por isso desaparecem quando carrega em “Li as notas”).
     */
    @Query(
        "$SELECT_RESUMO WHERE r.secaoId = :secaoId AND r.tipo = '${Registo.NOTA}' AND r.resposta = '${Registo.DESTINO_CHEFE}' " +
            "AND r.pessoaId != :pessoaId AND r.dataCriacao > IFNULL((SELECT MAX(x.dataCriacao) FROM registos x " +
            "WHERE x.secaoId = :secaoId AND x.tipo = '${Registo.RECECAO}' AND x.pessoaId = :pessoaId), 0) ORDER BY r.dataCriacao DESC"
    )
    fun obterNotasParaChefe(secaoId: String, pessoaId: String): Flow<List<RegistoResumo>>

    /** Rondas feitas por técnicos de uma especialidade, as mais recentes primeiro. */
    @Query("$SELECT_RESUMO WHERE r.tipo = '${Registo.RONDA}' AND p.especialidade = :especialidade ORDER BY r.dataCriacao DESC")
    fun obterRondas(especialidade: String): Flow<List<RegistoResumo>>

    /** Pedidos de ajuda por responder: o número do menu do chefe. */
    @Query("SELECT COUNT(*) FROM registos WHERE secaoId = :secaoId AND tipo = '${Registo.AJUDA}' AND estado IN ('${Registo.ABERTO}', '${Registo.A_CAMINHO}')")
    fun contarPedidosEmAberto(secaoId: String): Flow<Int>

    /** Pedidos do operador ainda por fechar: o número do menu do operador. */
    @Query("SELECT COUNT(*) FROM registos WHERE secaoId = :secaoId AND tipo IN ('${Registo.AJUDA}', '${Registo.MATERIAL}') AND estado IN ('${Registo.ABERTO}', '${Registo.A_CAMINHO}')")
    fun contarPedidosPorFechar(secaoId: String): Flow<Int>

    /** Notas da secção (ou oficina) escritas por outras pessoas: o número do menu Turno. */
    @Query("SELECT COUNT(*) FROM registos WHERE secaoId = :secaoId AND tipo = '${Registo.NOTA}' AND pessoaId != :pessoaId AND (resposta IS NULL OR resposta != '${Registo.DESTINO_CHEFE}')")
    fun contarNotas(secaoId: String, pessoaId: String): Flow<Int>

    /** Checklists de arranque feitas desde [desde] (o início do turno): é uma por turno e secção. */
    @Query("SELECT COUNT(*) FROM registos WHERE secaoId = :secaoId AND tipo = '${Registo.CHECKLIST}' AND dataCriacao >= :desde")
    fun contarChecklists(secaoId: String, desde: Long): Flow<Int>

    /** O número do Validar: passagens de turno por confirmar e checklists com Não OK. */
    @Query("SELECT COUNT(*) FROM registos WHERE secaoId = :secaoId AND ((tipo = '${Registo.RECECAO}' AND estado = '${Registo.POR_CONFIRMAR}') OR (tipo = '${Registo.CHECKLIST}' AND estado = '${Registo.NAO_CONFORME}'))")
    fun contarPorValidar(secaoId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM registos WHERE secaoId = :secaoId AND tipo = '${Registo.RECECAO}' AND pessoaId = :pessoaId AND dataCriacao >= :desde")
    suspend fun contarRececoes(secaoId: String, pessoaId: String, desde: Long): Int
}

@Dao
interface InstrucaoDao {

    @Insert
    suspend fun inserirTodas(lista: List<Instrucao>)

    @Query(
        "SELECT i.*, s.nome AS secaoNome, p.nome AS validadaPorNome FROM instrucoes i " +
            "JOIN secoes s ON s.id = i.secaoId LEFT JOIN pessoas p ON p.id = i.validadaPorId " +
            "WHERE i.secaoId = :secaoId ORDER BY i.id"
    )
    fun obterDaSecao(secaoId: String): Flow<List<InstrucaoResumo>>

    @Query(
        "SELECT i.*, s.nome AS secaoNome, p.nome AS validadaPorNome FROM instrucoes i " +
            "JOIN secoes s ON s.id = i.secaoId LEFT JOIN pessoas p ON p.id = i.validadaPorId ORDER BY i.secaoId, i.id"
    )
    fun obterTodas(): Flow<List<InstrucaoResumo>>

    @Query("SELECT * FROM instrucoes WHERE id = :id")
    suspend fun obter(id: String): Instrucao?

    @androidx.room.Update
    suspend fun atualizar(instrucao: Instrucao)

    @Query("SELECT COUNT(*) FROM instrucoes WHERE estado = '${Instrucao.POR_VALIDAR}'")
    fun contarPorValidar(): Flow<Int>
}

@Dao
interface PreventivaDao {

    @Insert
    suspend fun inserirTodas(lista: List<Preventiva>)

    @Update
    suspend fun atualizar(preventiva: Preventiva)

    @Insert
    suspend fun inserir(preventiva: Preventiva)

    @Query("SELECT * FROM preventivas WHERE id = :id")
    suspend fun obter(id: Long): Preventiva?

    @Query(
        "SELECT v.*, e.nome AS equipamentoNome, s.nome AS secaoNome, p.nome AS ultimaPorNome FROM preventivas v " +
            "JOIN equipamentos e ON e.id = v.equipamentoId JOIN secoes s ON s.id = e.secaoId " +
            "LEFT JOIN pessoas p ON p.id = v.ultimaPorId ORDER BY v.titulo"
    )
    fun obterTodas(): Flow<List<PreventivaResumo>>
}

@Dao
interface PecaDao {

    @Insert
    suspend fun inserirTodas(lista: List<Peca>)

    @Update
    suspend fun atualizar(peca: Peca)

    @Query("SELECT * FROM pecas WHERE id = :id")
    suspend fun obter(id: String): Peca?

    @Query(
        "SELECT c.*, s.nome AS secaoNome, p.nome AS pedidaPorNome FROM pecas c " +
            "JOIN equipamentos e ON e.id = c.equipamentoId JOIN secoes s ON s.id = e.secaoId " +
            "LEFT JOIN pessoas p ON p.id = c.pedidaPorId ORDER BY c.nome"
    )
    fun obterTodas(): Flow<List<PecaResumo>>
}
