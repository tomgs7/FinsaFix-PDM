package pt.ubi.di.pdm.finsafix

import org.junit.Assert.assertEquals
import pt.ubi.di.pdm.finsafix.util.ProducaoPerdida
import org.junit.Test

class ProducaoPerdidaTest {

    private val minuto = 60_000L

    @Test
    fun schelling_a_10_por_hora_perde_cerca_de_2_4_em_14_minutos_e_meio() {
        assertEquals(2.42, ProducaoPerdida.estimar("SCHEL", 0, 14 * minuto + 30_000), 0.01)
    }

    @Test
    fun seccao_sem_ritmo_ou_inicio_no_futuro_nao_perde_nada() {
        assertEquals(0.0, ProducaoPerdida.estimar("XPTO", 0, 60 * minuto), 0.0)
        assertEquals(0.0, ProducaoPerdida.estimar("SCHEL", 10 * minuto, 0), 0.0)
    }
}
