package pt.ubi.di.pdm.finsafix.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A equipa de uma avaria: tabela de ligação muitos-para-muitos entre avarias e técnicos.
 * Uma linha por participação: [dataSaida] é nula enquanto o técnico está na intervenção. O mesmo técnico pode
 * voltar a entrar mais tarde (por isso [dataEntrada] faz parte da chave). O responsável continua em
 * [Avaria.tecnicoId] e também tem uma linha aqui; os ajudantes só têm a linha.
 * Ao eliminar a avaria ou o técnico, as linhas vão com eles (CASCADE).
 */
@Entity(
    tableName = "avaria_tecnicos",
    primaryKeys = ["avariaId", "tecnicoId", "dataEntrada"],
    foreignKeys = [
        ForeignKey(entity = Avaria::class, parentColumns = ["id"], childColumns = ["avariaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["tecnicoId"], onDelete = ForeignKey.CASCADE)
    ],
    // "quem está nesta avaria" e "em que avaria está este técnico"; o prefixo de cada índice serve a chave estrangeira
    indices = [Index("avariaId", "dataSaida"), Index("tecnicoId", "dataSaida")]
)
data class AvariaTecnico(
    val avariaId: Long,
    val tecnicoId: String,
    val dataEntrada: Long,
    val dataSaida: Long? = null
)

/** Um membro da equipa com o nome, para mostrar no detalhe. */
data class MembroEquipa(
    @Embedded val participacao: AvariaTecnico,
    val nome: String
)
