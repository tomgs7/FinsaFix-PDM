package pt.ubi.di.pdm.finsafix.ui.producao

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.OrdemResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.ProducaoResumo
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Posto
import pt.ubi.di.pdm.finsafix.util.Validacao

/** Registar produção: equipamento, turno e quantidade. As escolhas ficam aqui para sobreviver à rotação. */
class ProducaoViewModel(application: Application) : ViewModelBase(application) {

    // A secção do posto, ou outra (“Registar noutra secção”)
    private val secaoAtual = MutableLiveData(Posto.obterId(application))
    val secaoId: String get() = secaoAtual.value ?: Posto.obterId(getApplication())
    val secoes: LiveData<List<Secao>> = repositorio.secoes().paraLiveData(emptyList())

    val secao: LiveData<Secao?> = secaoAtual.switchMap { repositorio.secao(it).paraLiveData(null) }
    val equipamentos: LiveData<List<Equipamento>> = secaoAtual.switchMap { repositorio.equipamentosDaSecao(it).paraLiveData(emptyList()) }
    // O tablet fica ligado durante horas: o turno em curso volta a ser calculado sempre que o ecrã aparece.
    private val turnoEmCurso = MutableLiveData(Datas.turnoAtual())
    val registosDoTurno: LiveData<List<ProducaoResumo>> = turnoEmCurso.switchMap { turno ->
        secaoAtual.switchMap { repositorio.producaoDoTurno(it, turno, Datas.inicioDoTurnoAtual()).paraLiveData(emptyList()) }
    }
    /** O plano do turno em curso (uma ordem por equipamento). */
    val ordens: LiveData<List<OrdemResumo>> = turnoEmCurso.switchMap { turno ->
        secaoAtual.switchMap { repositorio.ordensDoTurno(it, turno).paraLiveData(emptyList()) }
    }

    fun escolherSecao(id: String) {
        secaoAtual.value = id
        equipamentoId = null
    }

    var equipamentoId: String? = null
    var turno: String = Datas.turnoAtual()
    var quantidade: Double? = null
    var cortes: Int? = null

    private val _mensagem = MutableLiveData<Int?>()
    val mensagem: LiveData<Int?> = _mensagem

    /** O que falta escolher (para a mensagem de erro), ou null se está tudo pronto. */
    fun oQueFalta(): Int? = when {
        equipamentoId == null -> R.string.erro_equipamento
        quantidade.let { it == null || !Validacao.quantidadeValida(it) } -> R.string.erro_quantidade
        else -> null
    }

    fun registar(quem: Pessoa) {
        val equipamento = equipamentoId ?: return
        val valor = quantidade ?: return
        executar {
            repositorio.registarProducao(equipamento, turno, valor, quem, cortes)
            quantidade = null
            cortes = null
            _mensagem.value = R.string.producao_registada
        }
    }

    fun anular(id: Long, quem: Pessoa) {
        executar { _mensagem.value = if (repositorio.anularProducao(id, quem)) R.string.producao_anulada else R.string.erro_anular_producao }
    }

    fun atualizarTurno() {
        val agora = Datas.turnoAtual()
        if (turnoEmCurso.value != agora) turnoEmCurso.value = agora
    }

    fun mensagemMostrada() {
        _mensagem.value = null
    }
}
