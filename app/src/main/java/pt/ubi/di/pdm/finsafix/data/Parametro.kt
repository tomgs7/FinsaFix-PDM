package pt.ubi.di.pdm.finsafix.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Um parâmetro do processo que o operador lê no painel da máquina (ex.: "Desperdício de corte", máx. 4 %).
 * id = "<secção>-<parâmetro>" (ex.: "SCHEL-desp"), porque o mesmo parâmetro pode existir em várias secções.
 */
@Entity(
    tableName = "parametros",
    foreignKeys = [ForeignKey(entity = Secao::class, parentColumns = ["id"], childColumns = ["secaoId"])],
    indices = [Index("secaoId")]
)
data class Parametro(
    @PrimaryKey val id: String,
    val secaoId: String,
    val nome: String,
    val unidade: String,
    val minimo: Double,
    val maximo: Double,
    val decimais: Int,
    val tipoLimite: String
) {
    enum class Estado { DENTRO, PERTO, FORA }

    /** "Perto do limite" = a menos de 10 % da amplitude (máximo - mínimo) de um limite. */
    fun estado(valor: Double): Estado {
        val margem = (maximo - minimo) * 0.1
        val temMinimo = tipoLimite == LIMITE_INTERVALO
        return when {
            valor > maximo || (temMinimo && valor < minimo) -> Estado.FORA
            valor >= maximo - margem || (temMinimo && valor <= minimo + margem) -> Estado.PERTO
            else -> Estado.DENTRO
        }
    }

    /** Valor muito longe dos limites (mais de uma amplitude): provavelmente foi mal escrito. */
    fun improvavel(valor: Double): Boolean {
        val amplitude = maximo - minimo
        return valor > maximo + amplitude || valor < minimo - amplitude
    }

    companion object {
        /** Só há limite máximo (ex.: desperdício). */
        const val LIMITE_MAXIMO = "maximo"

        /** O valor tem de ficar entre o mínimo e o máximo (ex.: pressão). */
        const val LIMITE_INTERVALO = "intervalo"
    }
}
