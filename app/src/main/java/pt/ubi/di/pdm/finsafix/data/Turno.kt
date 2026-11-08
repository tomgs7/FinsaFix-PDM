package pt.ubi.di.pdm.finsafix.data

/** Os três turnos, guardados como uma letra (Pessoa.turno, Producao.turno, OrdemProducao.turno e Registo.extra nas receções). */
object Turno {
    const val MANHA = "M"
    const val TARDE = "T"
    const val NOITE = "N"
    val TODOS = listOf(MANHA, TARDE, NOITE)
}
