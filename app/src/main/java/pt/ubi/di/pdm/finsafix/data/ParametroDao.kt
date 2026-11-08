package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Um parâmetro com a última leitura (valor, quando e quem); os três ficam nulos se ainda não houver leitura. */
data class ParametroComLeitura(
    @Embedded val parametro: Parametro,
    val valor: Double?,
    val data: Long?,
    val pessoaNome: String?
)

/** Uma leitura com o nome do parâmetro, a unidade e quem a registou (lista “Últimas leituras”). */
data class LeituraResumo(
    @Embedded val leitura: Leitura,
    val parametroNome: String,
    val unidade: String,
    val decimais: Int,
    val pessoaNome: String
)

@Dao
interface ParametroDao {

    @Query(
        "SELECT l.*, p.nome AS parametroNome, p.unidade AS unidade, p.decimais AS decimais, pe.nome AS pessoaNome FROM leituras l " +
            "JOIN parametros p ON p.id = l.parametroId JOIN pessoas pe ON pe.id = l.pessoaId " +
            "WHERE p.secaoId = :secaoId ORDER BY l.data DESC, l.id DESC LIMIT 8"
    )
    fun obterUltimas(secaoId: String): Flow<List<LeituraResumo>>

    /** Todas as leituras da secção (o Histórico). */
    @Query(
        "SELECT l.*, p.nome AS parametroNome, p.unidade AS unidade, p.decimais AS decimais, pe.nome AS pessoaNome FROM leituras l " +
            "JOIN parametros p ON p.id = l.parametroId JOIN pessoas pe ON pe.id = l.pessoaId " +
            "WHERE p.secaoId = :secaoId ORDER BY l.data DESC, l.id DESC"
    )
    fun obterTodasDaSecao(secaoId: String): Flow<List<LeituraResumo>>


    @Insert
    suspend fun inserirTodas(lista: List<Parametro>)

    // Cada parâmetro com a leitura mais recente (a subconsulta escolhe-a); secaoId nulo = todas as secções
    @Query(
        "SELECT p.*, l.valor AS valor, l.data AS data, pe.nome AS pessoaNome FROM parametros p " +
            "LEFT JOIN leituras l ON l.id = (SELECT id FROM leituras WHERE parametroId = p.id ORDER BY data DESC, id DESC LIMIT 1) " +
            "LEFT JOIN pessoas pe ON pe.id = l.pessoaId " +
            "WHERE (:secaoId IS NULL OR p.secaoId = :secaoId) ORDER BY p.nome"
    )
    fun obterComUltimaLeitura(secaoId: String?): Flow<List<ParametroComLeitura>>
}
