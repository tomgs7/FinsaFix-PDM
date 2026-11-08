package pt.ubi.di.pdm.finsafix.ui.detalhe

import android.app.Application
import androidx.annotation.StringRes
import pt.ubi.di.pdm.finsafix.data.AutorizacaoResumo
import pt.ubi.di.pdm.finsafix.ui.autorizacoes.EstiloAutorizacao
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.data.EventoAvaria
import pt.ubi.di.pdm.finsafix.data.MembroEquipa
import pt.ubi.di.pdm.finsafix.data.Paragem
import pt.ubi.di.pdm.finsafix.data.PecaResumo
import pt.ubi.di.pdm.finsafix.data.PecaUsadaResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.data.Repositorio
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase

/** Detalhe de uma avaria e ações do técnico. Atualiza-se sozinho quando a avaria muda (Flow do Room). */
class DetalheViewModel(application: Application) : ViewModelBase(application) {

    private val avariaId = MutableLiveData<Long>()

    val avaria: LiveData<AvariaResumo?> = avariaId.switchMap { repositorio.avaria(it).paraLiveData(null) }
    val cronologia: LiveData<List<EventoAvaria>> =
        avariaId.switchMap { repositorio.cronologia(it).paraLiveData(emptyList()) }

    /** Quem está agora na intervenção (o responsável e os ajudantes). */
    val equipa: LiveData<List<MembroEquipa>> = avariaId.switchMap { repositorio.equipaDaAvaria(it).paraLiveData(emptyList()) }

    /** A paragem que esta avaria abriu, se a máquina ainda estiver parada (para perguntar se retoma). */
    val autorizacao: LiveData<AutorizacaoResumo?> =
        avariaId.switchMap { repositorio.autorizacaoDaAvaria(it).paraLiveData(null) }

    /** A secção da avaria: dá a unidade da produção perdida (“≈ 2,4 tab. perdidos”). */
    val secao: LiveData<Secao?> = avaria.switchMap { resumo ->
        resumo?.let { repositorio.secao(it.secaoId).paraLiveData(null) } ?: MutableLiveData<Secao?>(null)
    }

    val paragemAtiva: LiveData<Paragem?> =
        avariaId.switchMap { repositorio.paragemAtivaDaAvaria(it).paraLiveData(null) }

    /** Os 3 pontos LOTO marcados. Ficam aqui para não se perderem ao rodar o ecrã. */
    val pontosLoto = BooleanArray(3)

    /** Resultado de uma ação (ex.: "Intervenção assumida."), para o Fragment mostrar numa Snackbar. */
    private val _mensagem = MutableLiveData<Int?>()
    val mensagem: LiveData<Int?> = _mensagem

    fun carregar(id: Long) {
        if (avariaId.value != id) avariaId.value = id
    }

    fun lotoCompleto(): Boolean = pontosLoto.all { it }

    fun mensagemMostrada() {
        _mensagem.value = null
    }

    /** [especialidade] só se passa quando a avaria era "Não sei" e o técnico a classificou. */
    fun assumir(tecnico: Pessoa, especialidade: String?) {
        val id = avariaId.value ?: return
        if (!lotoCompleto()) return
        executar {
            _mensagem.value = when (repositorio.assumir(id, tecnico, especialidade)) {
                Repositorio.ResultadoAssumir.OK -> {
                    // Se voltar a ficar pendente (deixada em progresso), o LOTO tem de ser marcado de novo.
                    pontosLoto.fill(false)
                    R.string.intervencao_assumida
                }
                Repositorio.ResultadoAssumir.JA_TEM_INTERVENCAO -> R.string.erro_ja_tem_intervencao
                Repositorio.ResultadoAssumir.JA_NAO_ESTA_PENDENTE -> R.string.erro_ja_assumida
                Repositorio.ResultadoAssumir.ATRIBUIDA_A_OUTRO -> R.string.erro_atribuida_a_outro
            }
        }
    }

    /** “Juntar-me à intervenção”: o LOTO já foi confirmado na janela; o Repositorio volta a verificar todas as regras. */
    fun juntar(tecnico: Pessoa) {
        val id = avariaId.value ?: return
        executar {
            _mensagem.value = when (repositorio.juntarAIntervencao(id, tecnico, lotoConfirmado = true)) {
                Repositorio.ResultadoJuntar.OK -> R.string.equipa_juntou
                Repositorio.ResultadoJuntar.NAO_ESTA_EM_CURSO -> R.string.erro_juntar_nao_em_curso
                Repositorio.ResultadoJuntar.OUTRA_OFICINA -> R.string.erro_juntar_outra_oficina
                Repositorio.ResultadoJuntar.JA_TEM_INTERVENCAO -> R.string.erro_ja_tem_intervencao
                Repositorio.ResultadoJuntar.SEM_LOTO -> R.string.erro_juntar_loto
            }
        }
    }

    fun sair(tecnico: Pessoa) {
        val id = avariaId.value ?: return
        executar { _mensagem.value = if (repositorio.sairDaIntervencao(id, tecnico)) R.string.equipa_saiu else R.string.erro_sair_equipa }
    }

    /** [tipo] = Aviso.PARAR ou Aviso.ARRANCAR. */
    fun avisarSecao(tipo: String, tecnico: Pessoa) {
        val id = avariaId.value ?: return
        executar {
            _mensagem.value = if (repositorio.avisarSecao(id, tipo, tecnico)) R.string.aviso_enviado else R.string.aviso_ja_enviado
        }
    }

    // ---------- Peças substituídas ----------

    val pecasUsadas: LiveData<List<PecaUsadaResumo>> = avariaId.switchMap { repositorio.pecasDaAvaria(it).paraLiveData(emptyList()) }
    val pecas: LiveData<List<PecaResumo>> = repositorio.pecas().paraLiveData(emptyList())

    fun registarPeca(pecaId: String, quantidade: Int, tecnico: Pessoa) {
        val id = avariaId.value ?: return
        executar { _mensagem.value = if (repositorio.registarPecaUsada(id, pecaId, quantidade, tecnico)) R.string.pecas_registada else R.string.erro_peca }
    }

    // ---------- Decisões da engenharia ----------

    fun aprovarFecho(eng: Pessoa) {
        val id = avariaId.value ?: return
        executar { _mensagem.value = if (repositorio.aprovarFecho(id, eng)) R.string.decisao_fecho_aprovado else R.string.erro_decisao }
    }

    fun devolver(motivo: String, eng: Pessoa) {
        val id = avariaId.value ?: return
        executar { _mensagem.value = if (repositorio.devolverAoTecnico(id, motivo, eng)) R.string.decisao_devolvida else R.string.erro_decisao }
    }

    val tecnicos: LiveData<List<Pessoa>> = repositorio.todosOsTecnicos().paraLiveData(emptyList())

    fun reclassificarOficina(especialidade: String, eng: Pessoa) {
        val id = avariaId.value ?: return
        executar { _mensagem.value = if (repositorio.reclassificarOficina(id, especialidade, eng)) R.string.decisao_oficina_alterada else R.string.erro_decisao }
    }

    fun atribuir(tecnicoId: String?, eng: Pessoa) {
        val id = avariaId.value ?: return
        executar { _mensagem.value = if (repositorio.atribuirAvaria(id, tecnicoId, eng)) R.string.distribuir_atribuida else R.string.erro_decisao }
    }

    fun reclassificar(prioridade: String, eng: Pessoa) {
        val id = avariaId.value ?: return
        executar { _mensagem.value = if (repositorio.reclassificar(id, prioridade, eng)) R.string.decisao_reclassificada else R.string.erro_decisao }
    }

    // ---------- Autorização de trabalho (o técnico pede, começa e termina; o chefe decide noutro ecrã) ----------

    fun pedirAutorizacao(tipo: String, descricao: String, tecnico: Pessoa, duracaoMin: Int) {
        val id = avariaId.value ?: return
        executar { mostrarResultado(repositorio.pedirAutorizacao(id, tipo, descricao, tecnico, duracaoMin), R.string.aut_pedida) }
    }

    fun iniciarTrabalho(autorizacaoId: Long, cadeado: String?, tecnico: Pessoa) {
        executar { mostrarResultado(repositorio.iniciarTrabalho(autorizacaoId, tecnico, cadeado), R.string.aut_iniciada) }
    }

    fun terminarTrabalho(autorizacaoId: Long, tecnico: Pessoa) {
        executar { mostrarResultado(repositorio.terminarTrabalho(autorizacaoId, tecnico), R.string.aut_terminada) }
    }

    private fun mostrarResultado(resultado: Repositorio.ResultadoAutorizacao, @StringRes seCorreuBem: Int) {
        _mensagem.value = if (resultado == Repositorio.ResultadoAutorizacao.OK) seCorreuBem else EstiloAutorizacao.mensagem(resultado)
    }

    fun concluir(tecnico: Pessoa, retomarProducao: Boolean) {
        val id = avariaId.value ?: return
        executar {
            val concluida = repositorio.concluir(id, tecnico, retomarProducao)
            _mensagem.value = if (concluida) R.string.avaria_concluida else R.string.erro_autorizacao_ativa
        }
    }
}
