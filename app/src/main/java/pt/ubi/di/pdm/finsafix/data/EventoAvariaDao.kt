package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Um evento com o nome de quem o fez (as atualizações do ecrã Equipa). */
data class EventoComNome(
    @androidx.room.Embedded val evento: EventoAvaria,
    val pessoaNome: String?
)

@Dao
interface EventoAvariaDao {

    /** Os eventos das avarias em curso destas oficinas, os mais recentes primeiro (o ecrã escolhe os 3 últimos de cada). */
    @Query(
        "SELECT e.*, p.nome AS pessoaNome FROM eventos_avaria e JOIN avarias a ON a.id = e.avariaId " +
            "LEFT JOIN pessoas p ON p.id = e.pessoaId " +
            "WHERE a.estado = '${Avaria.EM_CURSO}' AND a.especialidade IN (:especialidades) ORDER BY e.data DESC, e.id DESC"
    )
    fun obterDasAvariasEmCurso(especialidades: List<String>): Flow<List<EventoComNome>>

    @Insert
    suspend fun inserir(evento: EventoAvaria)

    @Insert
    suspend fun inserirTodos(eventos: List<EventoAvaria>)

    @Query("SELECT * FROM eventos_avaria WHERE avariaId = :avariaId ORDER BY data, id")
    fun obterDaAvaria(avariaId: Long): Flow<List<EventoAvaria>>

    @Query("SELECT * FROM eventos_avaria WHERE avariaId = :avariaId")
    suspend fun obterDaAvariaLista(avariaId: Long): List<EventoAvaria>
}
