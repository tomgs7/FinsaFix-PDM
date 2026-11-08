package pt.ubi.di.pdm.finsafix.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabela genérica para o que se escreve e se lê no turno, em vez de uma tabela por ecrã:
 * pedidos de ajuda e de material, notas de turno, receções de turno e rondas. O [tipo] diz o que é.
 *
 * - AJUDA: [extra] é quem se chama (chefe, qualidade, logística, segurança); o chefe responde em [resposta].
 * - MATERIAL: [texto] é "10 × Paletes vazias".
 * - NOTA: [extra] é a categoria (atenção, pendente, informação, nota). As notas de um chefe de oficina guardam
 *   [DESTINO_CHEFE] em [resposta]: são só para o chefe de oficina do turno seguinte (os técnicos não as veem).
 * - RECECAO: [extra] é o turno ("M", "T" ou "N") cuja receção a pessoa confirma.
 * - CHECKLIST: checklist de arranque do turno; [extra] são os 6 resultados (1 = OK, 0 = Não OK).
 * - RONDA: [secaoId] é a secção inspecionada e [extra] os pontos não conformes (vazio se está tudo conforme).
 */
@Entity(
    tableName = "registos",
    foreignKeys = [
        ForeignKey(entity = Secao::class, parentColumns = ["id"], childColumns = ["secaoId"]),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["pessoaId"]),
        ForeignKey(entity = Pessoa::class, parentColumns = ["id"], childColumns = ["tratadoPorId"], onDelete = ForeignKey.SET_NULL)
    ],
    // quase todas as consultas são "desta secção, deste tipo, neste estado"
    // as rondas da oficina filtram só por tipo (de todas as secções) e ordenam por data
    indices = [Index("secaoId", "tipo", "estado"), Index("tipo", "dataCriacao"), Index("pessoaId"), Index("tratadoPorId")]
)
data class Registo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val secaoId: String,
    val pessoaId: String,
    val texto: String,
    val extra: String? = null,
    val urgente: Boolean = false,
    val estado: String,
    val dataCriacao: Long,
    val tratadoPorId: String? = null,
    val resposta: String? = null,
    val dataTratamento: Long? = null
) {
    companion object {
        const val AJUDA = "ajuda"
        const val MATERIAL = "material"
        const val NOTA = "nota"
        const val RECECAO = "rececao"
        const val RONDA = "ronda"
        const val CHECKLIST = "checklist"

        // Estados dos pedidos
        const val ABERTO = "aberto"
        const val A_CAMINHO = "a_caminho"
        const val ATENDIDO = "atendido"
        const val RECEBIDO = "recebido"
        const val FECHADO = "fechado"

        // Estados das receções de turno e das rondas
        const val POR_CONFIRMAR = "por_confirmar"
        const val CONFIRMADA = "confirmada"
        const val ATIVA = "ativa"
        const val CONFORME = "conforme"
        const val NAO_CONFORME = "nao_conforme"
        const val VALIDADA = "validada"

        // Categorias das notas e destinos dos pedidos de ajuda
        const val NOTA_ATENCAO = "atencao"
        const val NOTA_PENDENTE = "pendente"
        const val NOTA_INFORMACAO = "informacao"
        const val NOTA_GERAL = "nota"
        val CATEGORIAS_NOTA = listOf(NOTA_ATENCAO, NOTA_PENDENTE, NOTA_INFORMACAO, NOTA_GERAL)
        const val DESTINO_CHEFE = "chefe"
        const val DESTINO_QUALIDADE = "qualidade"
        const val DESTINO_LOGISTICA = "logistica"
        const val DESTINO_SEGURANCA = "seguranca"
        val DESTINOS = listOf(DESTINO_CHEFE, DESTINO_QUALIDADE, DESTINO_LOGISTICA, DESTINO_SEGURANCA)

        /** Pontos de uma ronda, por especialidade (as da especificação). */
        val PONTOS_RONDA = mapOf(
            Avaria.MECANICA to listOf(
                "Vibração", "Temperatura dos rolamentos", "Fugas (óleo, ar, vapor)", "Lubrificação",
                "Proteções e dispositivos de segurança", "Ruído", "Acumulação de pó / limpeza"
            ),
            Avaria.ELETRICA to listOf(
                "Quadros elétricos fechados e limpos", "Temperatura de motores e ligações", "Cabos e calhas sem danos",
                "Sensores e fotocélulas limpos", "Botões de emergência e sinalizadores",
                "Ventilação de quadros e variadores", "Iluminação e tomadas"
            )
        )
    }
}

/** Um registo com os nomes de quem o escreveu, de quem o tratou e da secção, para as listas. */
data class RegistoResumo(
    @androidx.room.Embedded val registo: Registo,
    val pessoaNome: String,
    val tratadoPorNome: String?,
    val secaoNome: String
)
