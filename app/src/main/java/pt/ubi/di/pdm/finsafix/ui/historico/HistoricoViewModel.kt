package pt.ubi.di.pdm.finsafix.ui.historico

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Aviso
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Registo
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.util.Numeros
import pt.ubi.di.pdm.finsafix.util.Posto

/** Os tipos de registo do Histórico, na ordem dos filtros. */
enum class TipoHistorico(val nome: Int) {
    AVARIAS(R.string.hist_avarias), PARAGENS(R.string.hist_paragens), PRODUCAO(R.string.hist_producao), PARAMETROS(R.string.hist_parametros),
    CHECKLISTS(R.string.hist_checklists), MATERIAL(R.string.hist_material), AJUDA(R.string.hist_ajuda), AVISOS(R.string.hist_avisos)
}

/** Uma linha do Histórico: o que aconteceu, quando e, nas avarias, qual (para abrir o detalhe). */
data class EntradaHistorico(val tipo: TipoHistorico, val data: Long, val titulo: String, val sub: String, val avariaId: Long? = null, val codigo: String? = null)

/**
 * Histórico: o operador vê tudo o que a sua secção fez (avarias, paragens, produção, leituras, checklists, pedidos e avisos);
 * o técnico só as avarias resolvidas da sua especialidade.
 */
class HistoricoViewModel(application: Application) : ViewModelBase(application) {

    private val pessoa = MutableLiveData<Pessoa>()
    private fun texto(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    val entradas: LiveData<List<EntradaHistorico>> = pessoa.switchMap { quem ->
        if (quem.papel == Pessoa.PAPEL_TECNICO) {
            repositorio.historicoDaOficina(listOfNotNull(quem.especialidade, Avaria.NAO_SEI)).map { lista ->
                lista.map { EntradaHistorico(TipoHistorico.AVARIAS, it.avaria.dataFim ?: it.avaria.dataCriacao, it.avaria.titulo, "${it.avaria.codigo} · ${it.equipamentoNome}", it.avaria.id, it.avaria.codigo) }
            }.paraLiveData(emptyList())
        } else {
            val secao = Posto.obterId(getApplication())
            val primeiros = combine(
                repositorio.avariasDaSecao(secao, Avaria.ESTADOS), repositorio.paragensRecentes(secao, 0L),
                repositorio.producaoDesde(secao, 0L), repositorio.leiturasDaSecao(secao)
            ) { avarias, paragens, producao, leituras ->
                avarias.map { EntradaHistorico(TipoHistorico.AVARIAS, it.avaria.dataCriacao, it.avaria.titulo, "${it.avaria.codigo} · ${it.equipamentoNome}", it.avaria.id, it.avaria.codigo) } +
                    paragens.map { EntradaHistorico(TipoHistorico.PARAGENS, it.paragem.inicio, it.equipamentoNome, it.paragem.causa) } +
                    producao.map {
                        EntradaHistorico(TipoHistorico.PRODUCAO, it.producao.data, texto(R.string.producao_linha, Numeros.formatar(it.producao.quantidade, 0), "", it.equipamentoNome).trim(), it.pessoaNome)
                    } +
                    leituras.map {
                        EntradaHistorico(TipoHistorico.PARAMETROS, it.leitura.data, texto(R.string.parametros_leitura, it.parametroNome, Numeros.formatar(it.leitura.valor, it.decimais), it.unidade), it.pessoaNome)
                    }
            }
            val segundos = combine(repositorio.checklistsDaSecao(secao), repositorio.pedidosDaSecao(secao), repositorio.avisosRecentes(secao)) { checklists, pedidos, avisos ->
                checklists.map {
                    EntradaHistorico(TipoHistorico.CHECKLISTS, it.registo.dataCriacao, texto(if (it.registo.estado == Registo.CONFORME) R.string.hist_checklist_ok else R.string.hist_checklist_nao_ok), it.pessoaNome)
                } + pedidos.map {
                    EntradaHistorico(
                        if (it.registo.tipo == Registo.MATERIAL) TipoHistorico.MATERIAL else TipoHistorico.AJUDA, it.registo.dataCriacao,
                        it.registo.texto.ifBlank { texto(EstiloRegistoTexto.destino(it.registo.extra)) }, it.pessoaNome
                    )
                } + avisos.map {
                    EntradaHistorico(TipoHistorico.AVISOS, it.aviso.dataCriacao, texto(textoDoAviso(it.aviso.tipo)), "${it.equipamentoNome ?: ""} · ${it.criadoPorNome}")
                }
            }
            combine(primeiros, segundos) { a, b -> (a + b).sortedByDescending { it.data } }.paraLiveData(emptyList())
        }
    }

    private fun textoDoAviso(tipo: String) = when (tipo) {
        Aviso.PARAR -> R.string.aviso_parar
        Aviso.ARRANCAR -> R.string.aviso_arrancar
        Aviso.A_CAMINHO -> R.string.aviso_a_caminho
        Aviso.ESPERA_PECA -> R.string.aviso_espera_peca
        Aviso.DEMORA -> R.string.aviso_demora
        else -> R.string.hist_aviso_chefe
    }

    fun definirPessoa(quem: Pessoa) {
        if (pessoa.value != quem) pessoa.value = quem
    }

}

private object EstiloRegistoTexto {
    fun destino(extra: String?) = pt.ubi.di.pdm.finsafix.ui.pedidos.EstiloRegisto.destino(extra)
}
