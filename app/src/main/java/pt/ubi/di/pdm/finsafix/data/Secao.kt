package pt.ubi.di.pdm.finsafix.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Uma secção da fábrica (ex.: "Schelling") ou uma oficina (Mecânica, Elétrica). */
@Entity(tableName = "secoes")
data class Secao(
    @PrimaryKey val id: String,
    val nome: String,
    val unidade: String,
    val tipo: String
) {
    companion object {
        const val TIPO_SECAO = "secao"
        const val TIPO_OFICINA = "oficina"
    }
}
