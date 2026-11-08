package pt.ubi.di.pdm.finsafix

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.ubi.di.pdm.finsafix.data.Peca
import pt.ubi.di.pdm.finsafix.data.Preventiva
import pt.ubi.di.pdm.finsafix.data.Preventiva.Estado.A_VENCER
import pt.ubi.di.pdm.finsafix.data.Preventiva.Estado.EM_DIA
import pt.ubi.di.pdm.finsafix.data.Preventiva.Estado.VENCIDA

/** Estados da preventiva (vencida, a vencer, em dia) e do stock, sem Android. */
class EstadosTest {

    private val agora = 1_000_000 * Preventiva.HORA_MS

    private fun preventiva(intervaloH: Int, horasDesde: Long) = Preventiva(
        equipamentoId = "EQ", titulo = "t", especialidade = "mecanica", intervaloH = intervaloH,
        ultimaData = agora - horasDesde * Preventiva.HORA_MS
    )

    @Test
    fun a_preventiva_passa_de_em_dia_a_vencer_aos_85_por_cento_e_a_vencida_no_intervalo() {
        assertEquals(EM_DIA, preventiva(100, 84).estado(agora))
        assertEquals(A_VENCER, preventiva(100, 85).estado(agora))
        assertEquals(A_VENCER, preventiva(100, 99).estado(agora))
        assertEquals(VENCIDA, preventiva(100, 100).estado(agora))
        assertEquals(VENCIDA, preventiva(250, 262).estado(agora))
    }

    @Test
    fun os_exemplos_do_prototipo_ficam_no_estado_certo() {
        assertEquals(A_VENCER, preventiva(72, 70).estado(agora)) // “Faltam 2 h”
        assertEquals(EM_DIA, preventiva(500, 420).estado(agora)) // 84 %
        assertEquals(EM_DIA, preventiva(80, 61).estado(agora))
    }

    @Test
    fun uma_execucao_no_futuro_nao_da_horas_negativas() {
        val p = Preventiva(equipamentoId = "EQ", titulo = "t", especialidade = "mecanica", intervaloH = 10, ultimaData = agora + 5 * Preventiva.HORA_MS)
        assertEquals(0L, p.horasDesdeUltima(agora))
        assertEquals(EM_DIA, p.estado(agora))
    }

    private fun peca(minimo: Int, stock: Int) = Peca(id = "P", nome = "n", equipamentoId = "EQ", especialidade = "mecanica", minimo = minimo, stock = stock)

    @Test
    fun o_stock_esta_em_rutura_abaixo_no_limite_ou_ok() {
        assertEquals(Peca.Estado.RUTURA, peca(2, 0).estado())
        assertEquals(Peca.Estado.ABAIXO, peca(4, 3).estado())
        assertEquals(Peca.Estado.NO_LIMITE, peca(2, 2).estado())
        assertEquals(Peca.Estado.OK, peca(2, 3).estado())
    }
}
