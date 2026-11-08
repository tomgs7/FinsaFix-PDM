package pt.ubi.di.pdm.finsafix

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.data.Indicadores
import pt.ubi.di.pdm.finsafix.data.Paragem

/** MTBF e MTTR com os casos limite: sem avarias, uma só, por resolver, tempo em espera. */
class IndicadoresTest {

    private val hora = 60 * 60_000L

    @Test
    fun sem_avarias_nao_ha_indicadores() {
        assertEquals(emptyList<Indicadores.PorEquipamento>(), Indicadores.porEquipamento(emptyList()))
        assertNull(Indicadores.mttr(emptyList()))
        assertEquals(Indicadores.Resumo(0, 0, 0, 0, null), Indicadores.resumo(emptyList()))
    }

    @Test
    fun com_uma_so_avaria_resolvida_ha_mttr_mas_nao_mtbf() {
        val (indicador) = Indicadores.porEquipamento(listOf(resolvida("EQ", abertaEm = 0, duracao = 2 * hora)))
        assertEquals(1, indicador.avarias)
        assertNull(indicador.mtbfMs) // com uma só avaria não há intervalo
        assertEquals(2 * hora, indicador.mttrMs)
    }

    @Test
    fun com_uma_so_avaria_por_resolver_nao_ha_nenhum_dos_dois() {
        val (indicador) = Indicadores.porEquipamento(listOf(aberta("EQ", abertaEm = 0)))
        assertEquals(1, indicador.avarias)
        assertNull(indicador.mtbfMs)
        assertNull(indicador.mttrMs)
    }

    @Test
    fun o_mtbf_e_a_media_dos_intervalos_e_conta_a_avaria_por_resolver() {
        val avarias = listOf(
            resolvida("EQ", abertaEm = 0, duracao = hora),
            resolvida("EQ", abertaEm = 10 * hora, duracao = hora),
            aberta("EQ", abertaEm = 40 * hora) // intervalos: 10 h e 30 h
        )
        val (indicador) = Indicadores.porEquipamento(avarias)
        assertEquals(3, indicador.avarias)
        assertEquals(20 * hora, indicador.mtbfMs)
    }

    @Test
    fun a_ordem_das_avarias_na_lista_nao_muda_o_mtbf() {
        val avarias = listOf(aberta("EQ", abertaEm = 40 * hora), aberta("EQ", abertaEm = 0), aberta("EQ", abertaEm = 10 * hora))
        assertEquals(20 * hora, Indicadores.porEquipamento(avarias).single().mtbfMs)
    }

    @Test
    fun o_mttr_ignora_as_por_resolver_e_exclui_o_tempo_em_espera() {
        val avarias = listOf(
            resolvida("EQ", abertaEm = 0, duracao = 4 * hora, emEspera = 3 * hora), // reparação efetiva: 1 h
            resolvida("EQ", abertaEm = 10 * hora, duracao = 3 * hora),
            aberta("EQ", abertaEm = 20 * hora).let { it.copy(avaria = it.avaria.copy(dataInicio = 20 * hora)) } // em curso: fora
        )
        assertEquals(2 * hora, Indicadores.porEquipamento(avarias).single().mttrMs) // (1 h + 3 h) / 2
    }

    @Test
    fun uma_resolvida_sem_inicio_ou_sem_fim_nao_entra_no_mttr() {
        val semInicio = resolvida("EQ", abertaEm = 0, duracao = hora).let { it.copy(avaria = it.avaria.copy(dataInicio = null)) }
        val semFim = resolvida("EQ", abertaEm = 5 * hora, duracao = hora).let { it.copy(avaria = it.avaria.copy(dataFim = null)) }
        assertNull(Indicadores.porEquipamento(listOf(semInicio, semFim)).single().mttrMs)
    }

    @Test
    fun cada_equipamento_tem_os_seus_indicadores_e_o_com_mais_avarias_vem_primeiro() {
        val avarias = listOf(
            resolvida("B", abertaEm = 0, duracao = hora),
            resolvida("A", abertaEm = 0, duracao = 2 * hora),
            resolvida("A", abertaEm = 6 * hora, duracao = 4 * hora)
        )
        val lista = Indicadores.porEquipamento(avarias)
        assertEquals(listOf("A", "B"), lista.map { it.equipamentoId })
        assertEquals(6 * hora, lista[0].mtbfMs)
        assertEquals(3 * hora, lista[0].mttrMs)
        assertNull(lista[1].mtbfMs)
        assertEquals(hora, lista[1].mttrMs)
    }

    @Test
    fun o_resumo_conta_abertas_em_curso_criticas_e_resolvidas() {
        val avarias = listOf(
            aberta("EQ", abertaEm = 0).avaria,
            aberta("EQ", abertaEm = 0, prioridade = Avaria.CRITICA).avaria,
            aberta("EQ", abertaEm = 0).avaria.copy(estado = Avaria.EM_CURSO, dataInicio = 0),
            resolvida("EQ", abertaEm = 0, duracao = hora).avaria,
            resolvida("EQ", abertaEm = 0, duracao = 3 * hora, prioridade = Avaria.CRITICA).avaria // crítica já resolvida: não conta
        )
        assertEquals(Indicadores.Resumo(abertas = 3, emCurso = 1, criticasAbertas = 1, resolvidas = 2, mttrMs = 2 * hora), Indicadores.resumo(avarias))
    }

    // ---------- Auxiliares ----------

    private fun aberta(equipamentoId: String, abertaEm: Long, prioridade: String = Avaria.MEDIA) = AvariaResumo(
        avaria = Avaria(
            codigo = "AV", equipamentoId = equipamentoId, titulo = "t", descricao = "", prioridade = prioridade,
            especialidade = Avaria.MECANICA, estado = Avaria.PENDENTE, reportadaPorId = "p", dataCriacao = abertaEm
        ),
        equipamentoNome = "Equipamento $equipamentoId", secaoId = "S", secaoNome = "Secção", reportadaPorNome = null,
        tecnicoNome = null, progressoPorNome = null
    )

    /** Resolvida: o técnico começou logo ao abrir e acabou [duracao] depois (com [emEspera] deixada em progresso pelo meio). */
    private fun resolvida(equipamentoId: String, abertaEm: Long, duracao: Long, emEspera: Long = 0, prioridade: String = Avaria.MEDIA): AvariaResumo {
        val base = aberta(equipamentoId, abertaEm, prioridade)
        return base.copy(
            avaria = base.avaria.copy(
                estado = Avaria.RESOLVIDA, dataInicio = abertaEm, dataFim = abertaEm + duracao, tempoParadoMs = emEspera
            )
        )
    }

    @Test
    fun avarias_por_dia_conta_so_os_dias_do_periodo() {
        val dia = 24L * 60 * 60 * 1000
        val agora = 100 * dia
        fun avaria(abertaHa: Long) = Avaria(
            codigo = "A", equipamentoId = "EQ", titulo = "t", descricao = "", prioridade = Avaria.MEDIA, especialidade = Avaria.MECANICA,
            estado = Avaria.PENDENTE, reportadaPorId = "p", dataCriacao = agora - abertaHa
        )
        val lista = listOf(avaria(1000), avaria(2000), avaria(dia + 5), avaria(6 * dia + 5), avaria(7 * dia + 5), avaria(-5))
        // 7 dias: duas hoje, uma ontem, uma há 6 dias; a de há 7 dias e a do futuro ficam de fora
        assertArrayEquals(intArrayOf(1, 0, 0, 0, 0, 1, 2), Indicadores.avariasPorDia(lista, 7, agora))
    }

    @Test
    fun parado_por_avaria_conta_so_as_paragens_de_avaria_dentro_do_periodo() {
        val hora = 3_600_000L
        val agora = 100 * hora
        fun paragem(inicio: Long, fim: Long?, avariaId: Long?) = Paragem(equipamentoId = "EQ", causa = "x", inicio = inicio, fim = fim, avariaId = avariaId)
        val paragens = listOf(
            paragem(agora - 3 * hora, agora - 1 * hora, 1), // 2 h por avaria
            paragem(agora - 10 * hora, null, 2), // ainda ativa: conta até agora (10 h), mas o período é de 5 h
            paragem(agora - 2 * hora, agora - 1 * hora, null) // sem avaria: não conta
        )
        assertEquals(2 * hora + 5 * hora, Indicadores.paradoPorAvaria(paragens, agora - 5 * hora, agora))
    }
}
