package pt.ubi.di.pdm.finsafix.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Um valor lido no painel da máquina, por quem e quando. O FinsaFix só regista; nunca altera a máquina. */
@Entity(
    tableName = "leituras",
    foreignKeys = [
        ForeignKey(entity = Parametro::class, parentColumns = ["id"], childColumns = ["parametroId"]),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["pessoaId"])
    ],
    // a última leitura de cada parâmetro: parametroId e data mais recente
    indices = [Index("parametroId", "data"), Index("pessoaId")]
)
data class Leitura(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parametroId: String,
    val valor: Double,
    val pessoaId: String,
    val data: Long
)
