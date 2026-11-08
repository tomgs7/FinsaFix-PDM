package pt.ubi.di.pdm.finsafix.ui.formulario

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import kotlinx.coroutines.flow.map
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.util.Posto
import pt.ubi.di.pdm.finsafix.util.Validacao

/** As respostas dadas até agora. Fica no ViewModel, por isso não se perde ao rodar o ecrã. */
data class Rascunho(
    val equipamentoId: String? = null,
    val especialidade: String? = null,
    val sintoma: String? = null,
    val descricao: String = "",
    val prioridade: String? = null,
    val maquinaParou: Boolean = false
)

/** Formulário "Reportar avaria" em 5 passos. Serve também para editar (mesmos passos, já preenchidos). */
class FormularioViewModel(application: Application) : ViewModelBase(application) {

    // A secção dos equipamentos: a do posto, ou outra (“Outra secção”)
    private val _secaoAtual = MutableLiveData<Secao?>()
    val secaoAtual: LiveData<Secao?> = _secaoAtual
    private val secaoId = MutableLiveData(Posto.obterId(application))

    val equipamentos: LiveData<List<Equipamento>> =
        secaoId.switchMap { repositorio.equipamentosDaSecao(it).paraLiveData(emptyList()) }

    /** Os equipamentos da secção que já estão parados (“Já parada” no cartão). */
    val paradas: LiveData<Set<String>> =
        secaoId.switchMap { id -> repositorio.paragensAtivas(id).map { lista -> lista.map { it.paragem.equipamentoId }.toSet() }.paraLiveData(emptySet()) }

    val secoes: LiveData<List<Secao>> = repositorio.secoes().paraLiveData(emptyList())

    init {
        // Mostra o nome do posto no cartão “Outra secção” desde o início
        executar(R.string.erro_abrir_dados) { _secaoAtual.value = repositorio.secaoDoPosto(Posto.obterId(application)) }
    }

    private val _passo = MutableLiveData(1)
    val passo: LiveData<Int> = _passo

    private val _rascunho = MutableLiveData(Rascunho())
    val rascunho: LiveData<Rascunho> = _rascunho

    /** Passa a true depois de gravar: o Fragment fecha o formulário. */
    private val _guardado = MutableLiveData(false)
    val guardado: LiveData<Boolean> = _guardado

    private var avariaOriginal: Avaria? = null
    private var jaCarregou = false

    /** Na edição, lê a avaria uma só vez (ao rodar o ecrã o rascunho já está no ViewModel). */
    fun carregar(avariaId: Long) {
        if (jaCarregou || avariaId == SEM_AVARIA) return
        jaCarregou = true
        executar(R.string.erro_abrir_dados) {
            val avaria = repositorio.avariaParaEditar(avariaId) ?: return@executar
            avariaOriginal = avaria
            _rascunho.value = Rascunho(
                equipamentoId = avaria.equipamentoId,
                especialidade = avaria.especialidade,
                sintoma = avaria.titulo,
                descricao = avaria.descricao,
                prioridade = avaria.prioridade
            )
        }
    }

    fun escolherEquipamento(id: String) = atualizar { copy(equipamentoId = id) }

    /** “Outra secção”: mostra os equipamentos dessa secção e limpa o equipamento escolhido. */
    fun escolherSecao(secao: Secao) {
        _secaoAtual.value = secao
        secaoId.value = secao.id
        atualizar { copy(equipamentoId = null) }
    }

    fun escolherMaquinaParou(parou: Boolean) = atualizar { copy(maquinaParou = parou) }

    /** “Alterar” no resumo: volta a um passo anterior. */
    fun irParaPasso(passo: Int) {
        _passo.value = passo
    }
    fun escolherEspecialidade(especialidade: String) = atualizar { copy(especialidade = especialidade) }
    fun escolherSintoma(sintoma: String?) = atualizar { copy(sintoma = sintoma) }
    fun escreverDescricao(descricao: String) = atualizar { copy(descricao = descricao) }
    // Numa avaria crítica a máquina costuma estar parada: “Sim” vem escolhido e o operador pode mudar.
    fun escolherPrioridade(prioridade: String) = atualizar { copy(prioridade = prioridade, maquinaParou = prioridade == Avaria.CRITICA) }

    private fun atualizar(alteracao: Rascunho.() -> Rascunho) {
        val atual = _rascunho.value ?: Rascunho()
        val novo = atual.alteracao()
        if (novo != atual) _rascunho.value = novo
    }

    /** Os passos 1 a 4 são obrigatórios; o 3 também exige a descrição dentro do limite. */
    fun passoValido(passo: Int, rascunho: Rascunho): Boolean = when (passo) {
        1 -> rascunho.equipamentoId != null
        2 -> rascunho.especialidade != null
        3 -> rascunho.sintoma != null && Validacao.descricaoValida(rascunho.descricao)
        4 -> rascunho.prioridade != null
        else -> (1..4).all { passoValido(it, rascunho) }
    }

    fun seguinte() {
        val passoAtual = _passo.value ?: 1
        if (passoAtual < TOTAL_PASSOS && passoValido(passoAtual, _rascunho.value ?: Rascunho())) {
            _passo.value = passoAtual + 1
        }
    }

    /** Volta um passo. Devolve false se já estava no primeiro (o Fragment fecha o formulário). */
    fun anterior(): Boolean {
        val passoAtual = _passo.value ?: 1
        if (passoAtual == 1) return false
        _passo.value = passoAtual - 1
        return true
    }

    fun guardar(quem: Pessoa) {
        val r = _rascunho.value ?: return
        if (!passoValido(TOTAL_PASSOS, r)) return
        executar {
            val original = avariaOriginal
            if (original == null) {
                val nova = Avaria(
                    codigo = "", // o Repositorio atribui o código seguinte
                    equipamentoId = r.equipamentoId!!,
                    titulo = r.sintoma!!,
                    descricao = r.descricao.trim(),
                    prioridade = r.prioridade!!,
                    especialidade = r.especialidade!!,
                    estado = Avaria.PENDENTE,
                    reportadaPorId = quem.id,
                    dataCriacao = System.currentTimeMillis()
                )
                repositorio.criarAvaria(nova, quem, abrirParagem = r.maquinaParou)
            } else {
                val editada = original.copy(
                    equipamentoId = r.equipamentoId!!,
                    titulo = r.sintoma!!,
                    descricao = r.descricao.trim(),
                    prioridade = r.prioridade!!,
                    especialidade = r.especialidade!!
                )
                repositorio.editarAvaria(editada, quem)
            }
            _guardado.value = true
        }
    }

    companion object {
        const val TOTAL_PASSOS = 5
        const val SEM_AVARIA = -1L
    }
}
