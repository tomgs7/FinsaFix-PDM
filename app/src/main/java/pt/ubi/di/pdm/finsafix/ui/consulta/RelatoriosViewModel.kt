package pt.ubi.di.pdm.finsafix.ui.consulta

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import kotlinx.coroutines.flow.combine
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.data.Indicadores
import pt.ubi.di.pdm.finsafix.data.ParagemResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Preventiva
import pt.ubi.di.pdm.finsafix.data.PreventivaResumo
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.LinhaRelatorio
import pt.ubi.di.pdm.finsafix.util.Relatorio
import pt.ubi.di.pdm.finsafix.util.TabelaRelatorio

/** O que a engenharia pediu: período, secção (null = toda a fábrica) e o que incluir. */
data class PedidoRelatorio(
    val dias: Int = 7,
    val secaoId: String? = null,
    val avarias: Boolean = true,
    val paragens: Boolean = true,
    val indicadores: Boolean = true,
    val preventivas: Boolean = true
)

/** Monta o [Relatorio] (já em texto) sempre que o pedido ou os dados mudam; a pré-visualização e a exportação usam o mesmo. */
class RelatoriosViewModel(application: Application) : ViewModelBase(application) {

    private val pessoa = MutableLiveData<Pessoa>()
    private val pedido = MutableLiveData(PedidoRelatorio())
    val secoes: LiveData<List<Secao>> = repositorio.secoes().paraLiveData(emptyList())

    val pedidoAtual: PedidoRelatorio get() = pedido.value ?: PedidoRelatorio()

    val relatorio: LiveData<Relatorio> = pedido.switchMap { p ->
        pessoa.switchMap { quem ->
            val desde = System.currentTimeMillis() - p.dias * DIA_MS
            combine(
                repositorio.avariasDeConsulta(quem, p.secaoId, Avaria.ESTADOS),
                repositorio.paragensRecentes(p.secaoId, desde),
                repositorio.preventivas()
            ) { avarias, paragens, preventivas -> montar(p, quem, avarias, paragens, preventivas) }
                .paraLiveData(montar(p, quem, emptyList(), emptyList(), emptyList()))
        }
    }

    fun definirPessoa(quem: Pessoa) {
        if (pessoa.value != quem) pessoa.value = quem
    }

    fun mudar(novo: PedidoRelatorio) {
        pedido.value = novo
    }

    private fun texto(@StringRes id: Int, vararg argumentos: Any) = getApplication<Application>().getString(id, *argumentos)

    private fun duracao(milissegundos: Long) = Datas.duracao(getApplication(), milissegundos)

    private fun montar(
        p: PedidoRelatorio, quem: Pessoa, avarias: List<AvariaResumo>, paragens: List<ParagemResumo>, preventivas: List<PreventivaResumo>
    ): Relatorio {
        val agora = System.currentTimeMillis()
        val desde = agora - p.dias * DIA_MS
        val doPeriodo = Indicadores.doPeriodo(avarias, desde, agora + 1)
        val paragensDoPeriodo = paragens.filter { it.paragem.inicio >= desde }
        val secao = secoes.value.orEmpty().firstOrNull { it.id == p.secaoId }?.nome
        // Só as preventivas por fazer (vencidas e a vencer), da secção escolhida
        val porFazer = preventivas.filter {
            it.preventiva.estado(agora) != Preventiva.Estado.EM_DIA && (secao == null || it.secaoNome == secao)
        }

        val tabelas = listOfNotNull(
            tabela(p.avarias, R.string.relatorio_tipo_avaria, R.string.relatorio_incluir_avarias, doPeriodo.map { linhaDeAvaria(it) }),
            tabela(p.paragens, R.string.relatorio_tipo_paragem, R.string.relatorio_incluir_paragens, paragensDoPeriodo.map { linhaDeParagem(it, agora) }),
            tabela(p.preventivas, R.string.relatorio_tipo_preventiva, R.string.relatorio_incluir_preventivas, porFazer.map { linhaDePreventiva(it, agora) })
        )
        return Relatorio(
            titulo = texto(R.string.relatorio_titulo),
            secao = secao ?: texto(R.string.consulta_todas_secoes),
            periodo = texto(R.string.relatorio_periodo, Datas.data(desde), Datas.data(agora)),
            rodape = texto(R.string.relatorio_gerado, quem.nome, Datas.dataHora(agora)),
            textoPagina = texto(R.string.relatorio_pagina, "%d"),
            colunaTipo = texto(R.string.relatorio_coluna_tipo),
            colunas = listOf(R.string.relatorio_coluna_data, R.string.relatorio_coluna_id, R.string.relatorio_coluna_equipamento,
                R.string.relatorio_coluna_detalhe, R.string.relatorio_coluna_duracao).map { texto(it) },
            tituloIndicadores = texto(R.string.relatorio_incluir_indicadores),
            numeros = if (p.indicadores) numeros(doPeriodo, paragens, desde, agora) else emptyList(),
            tabelas = tabelas,
            temDados = tabelas.any { it.linhas.isNotEmpty() } || (p.indicadores && doPeriodo.isNotEmpty())
        )
    }

    private fun tabela(incluir: Boolean, tipo: Int, nome: Int, linhas: List<LinhaRelatorio>) =
        if (incluir) TabelaRelatorio(texto(tipo), texto(R.string.relatorio_titulo_tabela, texto(nome), linhas.size), linhas) else null

    private fun numeros(doPeriodo: List<AvariaResumo>, paragens: List<ParagemResumo>, desde: Long, agora: Long): List<Pair<String, String>> {
        val semDados = texto(R.string.indicador_sem_dados)
        return listOf(
            texto(R.string.relatorio_numero_avarias) to doPeriodo.size.toString(),
            texto(R.string.relatorio_numero_mtbf) to (Indicadores.mtbfMedio(Indicadores.porEquipamento(doPeriodo))?.let { duracao(it) } ?: semDados),
            texto(R.string.relatorio_numero_mttr) to (Indicadores.mttr(doPeriodo.map { it.avaria })?.let { duracao(it) } ?: semDados),
            texto(R.string.relatorio_numero_parado) to duracao(Indicadores.paradoPorAvaria(paragens.map { it.paragem }, desde, agora))
        )
    }

    private fun linhaDeAvaria(a: AvariaResumo): LinhaRelatorio {
        val reparacao = a.avaria.takeIf { it.estado == Avaria.RESOLVIDA }?.duracaoMs(agora = 0)
        return LinhaRelatorio(
            Datas.data(a.avaria.dataCriacao), a.avaria.codigo, a.equipamentoNome, a.avaria.titulo,
            reparacao?.let { duracao(it) } ?: texto(R.string.relatorio_aberta)
        )
    }

    private fun linhaDeParagem(p: ParagemResumo, agora: Long): LinhaRelatorio {
        val fim = p.paragem.fim
        val tempo = duracao((fim ?: agora) - p.paragem.inicio)
        return LinhaRelatorio(
            Datas.data(p.paragem.inicio), p.avariaCodigo.orEmpty(), p.equipamentoNome, p.paragem.causa,
            if (fim == null) texto(R.string.relatorio_ativa, tempo) else tempo
        )
    }

    private fun linhaDePreventiva(p: PreventivaResumo, agora: Long): LinhaRelatorio {
        val estado = if (p.preventiva.estado(agora) == Preventiva.Estado.VENCIDA) R.string.preventiva_vencida else R.string.preventiva_a_vencer
        return LinhaRelatorio(
            Datas.data(p.preventiva.ultimaData), "", p.equipamentoNome, texto(R.string.relatorio_preventiva_estado, p.preventiva.titulo, texto(estado)),
            texto(R.string.relatorio_horas, p.preventiva.horasDesdeUltima(agora), p.preventiva.intervaloH)
        )
    }

    private companion object {
        const val DIA_MS = 24L * 60 * 60 * 1000
    }
}
