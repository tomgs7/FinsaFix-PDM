package pt.ubi.di.pdm.finsafix.util

/** Regras de validação (secção 7 da especificação). Não dependem do Android, para se poderem testar. */
object Validacao {

    const val TAMANHO_PIN = 4
    const val MAX_TENTATIVAS_PIN = 3
    const val BLOQUEIO_PIN_MS = 30_000L

    const val MAX_DESCRICAO = 300

    fun pinTemFormatoValido(pin: String): Boolean =
        pin.length == TAMANHO_PIN && pin.all { it.isDigit() }

    /** A descrição é opcional, mas tem no máximo 300 caracteres. */
    fun descricaoValida(descricao: String): Boolean = descricao.length <= MAX_DESCRICAO

    /** Produção: mais do que zero e menos de um milhão (um valor maior é quase de certeza um erro). */
    fun quantidadeValida(quantidade: Double): Boolean = quantidade > 0 && quantidade < 1_000_000

    /** O que se vai fazer numa autorização de trabalho: pelo menos 5 caracteres e no máximo 300. */
    fun descricaoAutorizacaoValida(descricao: String): Boolean = descricao.trim().length in 5..MAX_DESCRICAO

    const val MIN_NOTA = 5

    /** A nota para quem vem a seguir é obrigatória: pelo menos 5 caracteres (sem contar espaços nas pontas). */
    fun notaValida(nota: String): Boolean = nota.trim().length >= MIN_NOTA
}
