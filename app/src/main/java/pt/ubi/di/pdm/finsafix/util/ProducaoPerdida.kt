package pt.ubi.di.pdm.finsafix.util

/**
 * Produção perdida durante uma paragem: ritmo fixo de cada secção (unidades por hora, valores do protótipo)
 * a multiplicar pelas horas paradas. É uma estimativa para o banner, não um registo.
 */
object ProducaoPerdida {

    private val RITMO_POR_HORA = mapOf(
        "PARQ-EST" to 25.0, "PARQ-REC" to 15.0, "CALD" to 35.0, "DESF" to 18.0, "SEC" to 18.0,
        "PREN-MDF" to 55.0, "PREN-SP" to 60.0, "LIX-MDF" to 12.0, "LIX-SP" to 12.0,
        "SCHEL" to 10.0, "MULTI" to 8.0, "IMP" to 900.0, "MEL" to 120.0
    )

    /** Unidades por hora de uma secção (0 se não tiver ritmo): serve também para o plano do turno. */
    fun ritmo(secaoId: String): Double = RITMO_POR_HORA[secaoId] ?: 0.0

    /** Unidades perdidas desde [inicio] até [agora] (0 se a secção não tiver ritmo ou o início for futuro). */
    fun estimar(secaoId: String, inicio: Long, agora: Long): Double {
        val ritmo = RITMO_POR_HORA[secaoId] ?: return 0.0
        return ritmo * (agora - inicio).coerceAtLeast(0) / 3_600_000.0
    }
}
