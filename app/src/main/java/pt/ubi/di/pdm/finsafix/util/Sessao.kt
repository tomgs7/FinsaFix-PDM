package pt.ubi.di.pdm.finsafix.util

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Pessoa

/**
 * Quem está autenticado neste momento. Fica só em memória: se a aplicação for terminada,
 * a sessão acaba e volta-se ao ecrã de entrada (num tablet partilhado isto é o pretendido).
 */
object Sessao {
    var pessoa: Pessoa? = null
        private set
    var postoNome: String = ""
        private set

    /** Hora em que a pessoa entrou (aparece em "Mais"). */
    var entradaEm: Long = 0
        private set

    fun iniciar(pessoa: Pessoa, postoNome: String) {
        this.pessoa = pessoa
        this.postoNome = postoNome
        entradaEm = System.currentTimeMillis()
    }

    fun terminar() {
        pessoa = null
        postoNome = ""
    }

    /**
     * Tempo sem tocar no ecrã até perguntar "Ainda é X?". Operador e técnico: 10 min, e depois 60 s para responder.
     * Chefia e engenharia: 3 min no total, com a pergunta 30 s antes de terminar.
     */
    fun inatividadeMs(pessoa: Pessoa): Long =
        if (eOperadorOuTecnico(pessoa)) 10 * 60_000L else CHEFIA_MIN * 60_000L - respostaMs(pessoa)

    /** Minutos de uma sessão de chefia ou engenharia sem uso. */
    const val CHEFIA_MIN = 3

    /** Tempo para responder à pergunta; passado este tempo, a sessão termina. */
    fun respostaMs(pessoa: Pessoa): Long = if (eOperadorOuTecnico(pessoa)) 60_000L else 30_000L

    private fun eOperadorOuTecnico(pessoa: Pessoa) =
        pessoa.papel == Pessoa.PAPEL_OPERADOR || pessoa.papel == Pessoa.PAPEL_TECNICO
}

/** Devolve quem está autenticado ou, se a sessão acabou, volta ao ecrã de entrada e devolve null. */
fun Fragment.pessoaOuVoltarAEntrada(): Pessoa? {
    val pessoa = Sessao.pessoa
    if (pessoa == null) findNavController().navigate(R.id.acao_terminar_sessao)
    return pessoa
}
