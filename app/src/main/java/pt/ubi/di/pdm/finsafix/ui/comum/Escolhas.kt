package pt.ubi.di.pdm.finsafix.ui.comum

import androidx.fragment.app.Fragment
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Secao

/** Escolha imediata de uma opção: tocar nela fecha a janela e chama [aoEscolher] com o seu id. */
fun Fragment.escolherUma(
    titulo: String,
    opcoes: List<Opcao>,
    atualId: String? = null,
    etiqueta: String? = null,
    colunasTablet: Int = 1,
    aoEscolher: (String) -> Unit
) = escolher(
    EscolhaConfig(
        titulo = titulo,
        etiqueta = etiqueta,
        grupos = listOf(Grupo(null, opcoes)),
        inicial = setOfNotNull(atualId),
        colunasTablet = colunasTablet,
        larguraTabletDp = if (colunasTablet > 1) 720 else 560,
        aoConfirmar = { aoEscolher(it.first()) }
    )
)

/**
 * Escolha de uma opção com Cancelar e um botão principal, que só se ativa depois de escolher
 * (ou, havendo [atualId], de escolher outra). [avisoDaEscolha] muda o aviso conforme a opção.
 */
fun Fragment.escolherEConfirmar(
    titulo: String,
    opcoes: List<Opcao>,
    textoConfirmar: String,
    atualId: String? = null,
    etiqueta: String? = null,
    aviso: String? = null,
    avisoDaEscolha: ((String) -> String?)? = null,
    colunasTablet: Int = 1,
    aoConfirmar: (String) -> Unit
) = escolher(
    EscolhaConfig(
        titulo = titulo,
        etiqueta = etiqueta,
        aviso = aviso,
        avisoDaEscolha = avisoDaEscolha?.let { f -> { ids: Set<String> -> ids.firstOrNull()?.let(f) } },
        grupos = listOf(Grupo(null, opcoes)),
        inicial = setOfNotNull(atualId),
        textoConfirmar = textoConfirmar,
        colunasTablet = colunasTablet,
        larguraTabletDp = if (colunasTablet > 1) 720 else 560,
        aoConfirmar = { aoConfirmar(it.first()) }
    )
)

/** Escolha de uma secção (sem oficinas); com [comTodas] o primeiro cartão é “Todas as secções” e devolve null. */
fun Fragment.escolherSecao(
    titulo: String,
    secoes: List<Secao>,
    atualId: String? = null,
    comTodas: Boolean = false,
    aoEscolher: (Secao?) -> Unit
) {
    val todas = if (comTodas) listOf(Opcao(TODAS, getString(R.string.consulta_todas_secoes))) else emptyList()
    escolherUma(
        titulo = titulo,
        opcoes = todas + secoes.map { Opcao(it.id, it.nome) },
        atualId = atualId ?: if (comTodas) TODAS else null,
        colunasTablet = 2
    ) { id -> aoEscolher(secoes.firstOrNull { it.id == id }) }
}

private const val TODAS = ""

/** O id da opção “Sem atribuição” nas escolhas de técnico (não é número, por isso toLongOrNull dá null). */
const val PARA_TODOS = "todos"
