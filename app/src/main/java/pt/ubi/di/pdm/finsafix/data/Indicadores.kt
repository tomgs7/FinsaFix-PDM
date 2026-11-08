package pt.ubi.di.pdm.finsafix.data

/**
 * Indicadores de manutenção (MTBF, MTTR e contagens). São funções puras, sem Android:
 * testam-se no computador e só o [Repositorio] as chama (é o único sítio onde o cálculo se faz).
 */
object Indicadores {

    /** [mtbfMs] e [mttrMs] são null quando não há dados suficientes (ver [porEquipamento]). */
    data class PorEquipamento(
        val equipamentoId: String,
        val equipamentoNome: String,
        val secaoNome: String,
        val avarias: Int,
        val mtbfMs: Long?,
        val mttrMs: Long?
    )

    data class Resumo(
        val abertas: Int,
        val emCurso: Int,
        val criticasAbertas: Int,
        val resolvidas: Int,
        val mttrMs: Long?
    )

    /**
     * Um registo por equipamento que tem avarias (sem avarias não aparece).
     *
     * MTBF (tempo médio entre avarias): média dos intervalos entre as datas de abertura de avarias seguidas
     * do mesmo equipamento. Precisa de 2 avarias; com 1 não há intervalo e fica null. Conta também as por resolver.
     *
     * MTTR (tempo médio de reparação): média da duração das avarias resolvidas. [Avaria.duracaoMs] já exclui
     * o tempo em que a avaria esteve deixada em progresso. As por resolver não entram.
     */
    fun porEquipamento(avarias: List<AvariaResumo>): List<PorEquipamento> =
        avarias.groupBy { it.avaria.equipamentoId }
            .map { (id, lista) ->
                val aberturas = lista.map { it.avaria.dataCriacao }.sorted()
                PorEquipamento(
                    equipamentoId = id,
                    equipamentoNome = lista.first().equipamentoNome,
                    secaoNome = lista.first().secaoNome,
                    avarias = lista.size,
                    mtbfMs = media(aberturas.zipWithNext { antes, depois -> depois - antes }),
                    mttrMs = mttr(lista.map { it.avaria })
                )
            }
            .sortedWith(compareByDescending<PorEquipamento> { it.avarias }.thenBy { it.equipamentoNome })

    /** MTTR de um conjunto de avarias (as que não estão resolvidas ou não têm início e fim ficam de fora). */
    fun mttr(avarias: List<Avaria>): Long? = media(
        avarias.filter { it.estado == Avaria.RESOLVIDA && it.dataFim != null }.mapNotNull { it.duracaoMs(agora = 0) }
    )

    /** MTBF médio dos equipamentos que já têm intervalo (null se nenhum tem). */
    fun mtbfMedio(porEquipamento: List<PorEquipamento>): Long? = media(porEquipamento.mapNotNull { it.mtbfMs })

    fun resumo(avarias: List<Avaria>): Resumo {
        val abertas = avarias.filter { it.estado != Avaria.RESOLVIDA }
        return Resumo(
            abertas = abertas.size,
            emCurso = abertas.count { it.estado == Avaria.EM_CURSO },
            criticasAbertas = abertas.count { it.prioridade == Avaria.CRITICA },
            resolvidas = avarias.size - abertas.size,
            mttrMs = mttr(avarias)
        )
    }

    /** Tempo parado por avaria no período [de, ate]: só as paragens ligadas a uma avaria, cortadas pelos limites do período. */
    fun paradoPorAvaria(paragens: List<Paragem>, de: Long, ate: Long): Long =
        paragens.filter { it.avariaId != null }.sumOf { (minOf(it.fim ?: ate, ate) - maxOf(it.inicio, de)).coerceAtLeast(0) }

    /** As avarias abertas (criadas) no período [de, ate). */
    fun doPeriodo(avarias: List<AvariaResumo>, de: Long, ate: Long) = avarias.filter { it.avaria.dataCriacao in de until ate }

    /**
     * Quantas avarias foram abertas em cada um dos [dias] dias que acabam em [agora], do mais antigo para o mais recente
     * (o gráfico “Avarias por dia”). O último dia conta até [agora].
     */
    fun avariasPorDia(avarias: List<Avaria>, dias: Int, agora: Long): IntArray {
        val dia = 24L * 60 * 60 * 1000
        val contagem = IntArray(dias)
        for (avaria in avarias) {
            val atras = ((agora - avaria.dataCriacao) / dia).toInt() // 0 = nas últimas 24 h
            if (avaria.dataCriacao <= agora && atras in 0 until dias) contagem[dias - 1 - atras]++
        }
        return contagem
    }

    private fun media(valores: List<Long>): Long? = if (valores.isEmpty()) null else valores.sum() / valores.size
}
