package pt.ubi.di.pdm.finsafix.ui.comum

import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.databinding.DialogPinBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemPontoSimnaoBinding
import pt.ubi.di.pdm.finsafix.databinding.DialogTextoBinding
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Posto
import pt.ubi.di.pdm.finsafix.util.Sessao

/**
 * Diálogo de PIN (entrada do administrador e do chefe, aprovar e encerrar autorizações).
 * [itens] é a checklist a confirmar antes do PIN (pode estar vazia).
 * Cada item tem “Sim / Não”: só conta como completa com todos em “Sim”. [aviso] aparece por cima da lista e
 * [dicaExtra] acrescenta um campo de texto obrigatório (ex.: nome do vigilante de fogo).
 * [aoConfirmar] recebe o PIN, se a checklist está toda em “Sim” e o texto extra; devolve null se correu bem
 * (o diálogo fecha) ou a mensagem de erro a mostrar no campo (o diálogo fica aberto).
 */
fun Fragment.pedirPin(
    @StringRes titulo: Int,
    itens: List<String> = emptyList(),
    aviso: String? = null,
    @StringRes dicaExtra: Int? = null,
    aoConfirmar: suspend (pin: String, checklistCompleta: Boolean, extra: String) -> String?
) {
    val dialogo = DialogPinBinding.inflate(layoutInflater)
    val pontos = itens.map { texto ->
        val ponto = ItemPontoSimnaoBinding.inflate(layoutInflater, dialogo.listaItens, false)
        ponto.textoPonto.text = texto
        dialogo.listaItens.addView(ponto.root)
        ponto
    }
    dialogo.textoAviso.isVisible = aviso != null
    dialogo.textoAviso.text = aviso
    dialogo.campoExtra.isVisible = dicaExtra != null
    dicaExtra?.let { dialogo.campoExtra.setHint(it) }
    val alerta = MaterialAlertDialogBuilder(requireContext())
        .setTitle(titulo)
        .setView(dialogo.root)
        .setNegativeButton(R.string.botao_cancelar, null)
        .create()

    val pin = StringBuilder()
    fun mostrarPontos() {
        dialogo.textoPontos.text = (0 until TAMANHO_PIN).joinToString(" ") { if (it < pin.length) "●" else "○" }
        dialogo.textoErroPin.isVisible = false
    }
    mostrarPontos()

    var aVerificar = false
    fun confirmar() {
        // Enquanto verifica, outra tecla OK não conta como segunda tentativa.
        if (aVerificar) return
        aVerificar = true
        viewLifecycleOwner.lifecycleScope.launch {
            val erro = aoConfirmar(pin.toString(), pontos.all { it.grupoSimNao.checkedButtonId == it.botaoSim.id }, dialogo.campoExtra.editText?.text.toString().trim())
            aVerificar = false
            if (erro == null) {
                alerta.dismiss()
            } else {
                pin.clear()
                mostrarPontos()
                dialogo.textoErroPin.text = erro
                dialogo.textoErroPin.isVisible = true
            }
        }
    }

    preencherTeclas(dialogo.grelhaTeclas, TECLAS_PIN) { tecla ->
        when (tecla) {
            APAGAR -> if (pin.isNotEmpty()) pin.deleteCharAt(pin.length - 1)
            OK -> confirmar()
            else -> if (pin.length < TAMANHO_PIN) pin.append(tecla)
        }
        if (tecla != OK) mostrarPontos()
    }
    alerta.show()
}

private const val TAMANHO_PIN = 4

/**
 * Diálogo com um campo de texto (descrição de um pedido, número do cadeado).
 * [validar] devolve a mensagem de erro, ou null se o texto serve; só então chama [aoConfirmar].
 */
fun Fragment.pedirTexto(
    @StringRes titulo: Int,
    @StringRes dica: Int,
    @StringRes botao: Int,
    validar: (String) -> String?,
    aoConfirmar: (String) -> Unit
) {
    val dialogo = DialogTextoBinding.inflate(layoutInflater)
    dialogo.campoTexto.setHint(dica)
    val alerta = MaterialAlertDialogBuilder(requireContext())
        .setTitle(titulo)
        .setView(dialogo.root)
        .setPositiveButton(botao, null)
        .setNegativeButton(R.string.botao_cancelar, null)
        .create()

    dialogo.campoTexto.editText?.doAfterTextChanged { dialogo.campoTexto.error = null }
    alerta.setOnShowListener {
        alerta.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val texto = dialogo.campoTexto.editText?.text.toString().trim()
            val erro = validar(texto)
            if (erro == null) {
                alerta.dismiss()
                aoConfirmar(texto)
            } else {
                dialogo.campoTexto.error = erro
            }
        }
    }
    alerta.show()
}

/**
 * “Configurar posto”: escolhe a secção ou oficina deste tablet (grupos Secções e Oficinas, com uma segunda linha
 * em cada opção) e só muda ao tocar em Guardar, que fica ativo depois de escolher outro posto.
 */
fun Fragment.escolherPosto(postos: List<Secao>, equipamentos: Map<String, Int>, atualId: String, aoGuardar: (Secao) -> Unit) {
    val nomeAtual = postos.firstOrNull { it.id == atualId }?.nome ?: ""
    val sessao = Sessao.pessoa
    val ultima = Posto.ultimaAlteracao(requireContext())?.let {
        getString(R.string.configurar_posto_ultima, it.de, it.para, Datas.dataHora(it.quando))
    }
    val atual = if (sessao != null) getString(R.string.configurar_posto_atual, nomeAtual, sessao.nome)
    else getString(R.string.configurar_posto_atual_sem_sessao, nomeAtual)
    fun opcoesDe(tipo: String) = postos.filter { it.tipo == tipo }.let { l -> if (tipo == Secao.TIPO_OFICINA) l.sortedByDescending { it.id } else l }.map { posto ->
        val sub = if (tipo == Secao.TIPO_SECAO) resources.getQuantityString(R.plurals.posto_equipamentos, equipamentos[posto.id] ?: 0, equipamentos[posto.id] ?: 0)
        else when (posto.id) {
            "OFI-MEC" -> getString(R.string.posto_sub_ofi_mec)
            "OFI-ELE" -> getString(R.string.posto_sub_ofi_ele)
            else -> null
        }
        Opcao(posto.id, posto.nome, sub)
    }
    escolher(
        EscolhaConfig(
            etiqueta = getString(R.string.configurar_posto_etiqueta),
            titulo = getString(R.string.configurar_posto_titulo),
            chipAviso = getString(R.string.configurar_posto_chip),
            aviso = listOfNotNull(atual, ultima).joinToString("\n"),
            grupos = listOf(
                Grupo(getString(R.string.posto_grupo_secoes), opcoesDe(Secao.TIPO_SECAO)),
                Grupo(getString(R.string.posto_grupo_oficinas), opcoesDe(Secao.TIPO_OFICINA))
            ),
            inicial = setOf(atualId),
            textoConfirmar = getString(R.string.botao_guardar),
            colunasTablet = 3,
            larguraTabletDp = 880,
            aoConfirmar = { ids -> postos.firstOrNull { it.id == ids.first() }?.let(aoGuardar) }
        )
    )
}
