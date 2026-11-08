package pt.ubi.di.pdm.finsafix.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Quem trabalha na fábrica. O PIN (só chefia e engenharia) guarda-se como hash, nunca em texto.
 * postoId é nulo para quem não pertence a um posto (a engenharia trabalha em toda a fábrica).
 */
@Entity(
    tableName = "pessoas",
    foreignKeys = [ForeignKey(entity = Secao::class, parentColumns = ["id"], childColumns = ["postoId"])],
    indices = [Index("postoId")]
)
data class Pessoa(
    @PrimaryKey val id: String,
    val nome: String,
    val funcao: String,
    val papel: String,
    val postoId: String?,
    val turno: String,
    val especialidade: String?,
    val pinHash: String?
) {
    /** Para cumprimentar: o primeiro nome, ou "Eng.ª Marta" quando o nome começa por um título. */
    fun nomeCurto(): String {
        val partes = nome.split(" ")
        return if (partes[0].endsWith(".") || partes[0].endsWith(".ª")) partes.take(2).joinToString(" ") else partes[0]
    }

    /** "José Martins" → "JM" (primeira letra do primeiro e do último nome). */
    fun iniciais(): String = iniciaisDe(nome)

    /** O chefe de oficina é o único chefe com especialidade (os de secção não têm). */
    fun eChefeDeOficina(): Boolean = papel == PAPEL_CHEFE && especialidade != null

    /** Engenharia e chefe de oficina só consultam: não respondem a avisos nem alteram nada. */
    fun soConsulta(): Boolean = papel == PAPEL_ENGENHEIRO || eChefeDeOficina()

    companion object {
        const val PAPEL_OPERADOR = "operador"
        const val PAPEL_TECNICO = "tecnico"
        const val PAPEL_CHEFE = "chefe"
        const val PAPEL_ENGENHEIRO = "engenheiro"
    }
}

/** "José Martins" → "JM" (primeira letra do primeiro e do último nome). */
fun iniciaisDe(nome: String): String {
    val partes = nome.split(" ").filter { it.isNotBlank() }
    return (partes.first().take(1) + partes.last().take(1)).uppercase()
}
