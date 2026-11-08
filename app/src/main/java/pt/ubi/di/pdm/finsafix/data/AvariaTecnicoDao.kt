package pt.ubi.di.pdm.finsafix.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AvariaTecnicoDao {

    @Insert
    suspend fun entrar(participacao: AvariaTecnico)

    /** O técnico sai da avaria (só a participação ainda aberta). */
    @Query("UPDATE avaria_tecnicos SET dataSaida = :agora WHERE avariaId = :avariaId AND tecnicoId = :tecnicoId AND dataSaida IS NULL")
    suspend fun sair(avariaId: Long, tecnicoId: String, agora: Long): Int

    /** Concluir e deixar em progresso fecham a participação de todos. */
    @Query("UPDATE avaria_tecnicos SET dataSaida = :agora WHERE avariaId = :avariaId AND dataSaida IS NULL")
    suspend fun sairTodos(avariaId: Long, agora: Long)

    /** Os técnicos que estão agora na avaria (o mais antigo primeiro). */
    @Query(
        "SELECT t.*, p.nome AS nome FROM avaria_tecnicos t JOIN pessoas p ON p.id = t.tecnicoId " +
            "WHERE t.avariaId = :avariaId AND t.dataSaida IS NULL ORDER BY t.dataEntrada, p.nome"
    )
    fun obterAtuais(avariaId: Long): Flow<List<MembroEquipa>>

    /** Quem está agora nas intervenções em curso destas oficinas (o ecrã Equipa do chefe de oficina). */
    @Query(
        "SELECT t.*, p.nome AS nome FROM avaria_tecnicos t JOIN pessoas p ON p.id = t.tecnicoId " +
            "JOIN avarias a ON a.id = t.avariaId " +
            "WHERE a.estado = '${Avaria.EM_CURSO}' AND a.especialidade IN (:especialidades) AND t.dataSaida IS NULL " +
            "ORDER BY t.dataEntrada, p.nome"
    )
    fun obterAtuaisDasEspecialidades(especialidades: List<String>): Flow<List<MembroEquipa>>

    @Query("SELECT * FROM avaria_tecnicos WHERE avariaId = :avariaId AND dataSaida IS NULL")
    suspend fun obterAtuaisLista(avariaId: Long): List<AvariaTecnico>

    @Query("SELECT * FROM avaria_tecnicos WHERE avariaId = :avariaId")
    suspend fun obterDaAvaria(avariaId: Long): List<AvariaTecnico>

    /**
     * A avaria em curso em que o técnico está agora (como responsável ou ajudante); nula se está livre.
     * Um técnico só pode estar numa intervenção de cada vez.
     */
    @Query(
        "SELECT a.id FROM avarias a WHERE a.estado = '${Avaria.EM_CURSO}' AND (a.tecnicoId = :tecnicoId OR EXISTS (" +
            "SELECT 1 FROM avaria_tecnicos t WHERE t.avariaId = a.id AND t.tecnicoId = :tecnicoId AND t.dataSaida IS NULL)) LIMIT 1"
    )
    suspend fun avariaAtualDoTecnico(tecnicoId: String): Long?

    /** Os ajudantes escolhidos pelo chefe antes de a avaria ser assumida: a entrada passa a ser a hora em que o responsável assume. */
    @Query("UPDATE avaria_tecnicos SET dataEntrada = :agora WHERE avariaId = :avariaId AND dataSaida IS NULL")
    suspend fun reiniciarEntrada(avariaId: Long, agora: Long)

    /** Tira da equipa os ajudantes atribuídos a uma avaria pendente (o chefe vai escolher outra equipa). */
    @Query("DELETE FROM avaria_tecnicos WHERE avariaId = :avariaId AND dataSaida IS NULL")
    suspend fun apagarAtuais(avariaId: Long)

    /** As avarias em curso em que o técnico é ajudante (não responsável): levam a etiqueta “Na equipa”. */
    @Query(
        "SELECT a.id FROM avarias a JOIN avaria_tecnicos t ON t.avariaId = a.id WHERE a.estado = '${Avaria.EM_CURSO}' " +
            "AND t.tecnicoId = :tecnicoId AND t.dataSaida IS NULL AND IFNULL(a.tecnicoId, '') <> :tecnicoId"
    )
    fun avariasComoAjudante(tecnicoId: String): Flow<List<Long>>

    @Insert
    suspend fun inserirTodos(participacoes: List<AvariaTecnico>)
}
