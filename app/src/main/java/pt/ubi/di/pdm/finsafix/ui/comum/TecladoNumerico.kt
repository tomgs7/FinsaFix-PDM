package pt.ubi.di.pdm.finsafix.ui.comum

import android.widget.GridLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.databinding.DialogTecladoBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemTeclaBinding
import pt.ubi.di.pdm.finsafix.util.Numeros

/**
 * Teclado numérico grande (nunca botões −/+), para luvas e mãos sujas.
 * [decimais] = 0 não deixa escrever vírgula; [permitirNegativo] acrescenta a tecla "−". [info] devolve o texto por baixo do valor
 * (ex.: limite e estado) e é chamada a cada tecla. Só chama [aoConfirmar] com um número válido.
 */
fun Fragment.pedirNumero(
    titulo: String,
    unidade: String,
    decimais: Int,
    permitirNegativo: Boolean = false,
    info: (Double?) -> String,
    aoConfirmar: (Double) -> Unit
) {
    val vista = DialogTecladoBinding.inflate(layoutInflater)
    val texto = StringBuilder()

    fun atualizar() {
        vista.textoValor.text = texto
        vista.textoInfo.text = info(Numeros.ler(texto.toString()))
        vista.textoErro.isVisible = false
    }

    fun premir(tecla: String) {
        val casasDecimais = texto.toString().substringAfter(',', "").length
        when (tecla) {
            APAGAR -> if (texto.isNotEmpty()) texto.deleteCharAt(texto.length - 1)
            MENOS -> if (texto.startsWith("-")) texto.deleteCharAt(0) else texto.insert(0, '-')
            "," -> if (decimais > 0 && texto.isNotEmpty() && ',' !in texto) texto.append(',')
            else -> if (texto.length < MAX_DIGITOS && (',' !in texto || casasDecimais < decimais)) texto.append(tecla)
        }
        atualizar()
    }

    vista.textoUnidade.text = unidade
    preencherTeclas(vista.grelhaTeclas, if (permitirNegativo) TECLAS + MENOS else TECLAS, ativa = { it != "," || decimais > 0 }) { premir(it) }
    atualizar()

    val dialogo = MaterialAlertDialogBuilder(requireContext())
        .setTitle(titulo)
        .setView(vista.root)
        .setPositiveButton(R.string.teclado_registar, null)
        .setNegativeButton(R.string.botao_cancelar, null)
        .create()
    // O botão só fecha o diálogo com um valor válido; sem valor mostra o erro.
    dialogo.setOnShowListener {
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val valor = Numeros.ler(texto.toString())
            if (valor == null) {
                vista.textoErro.isVisible = true
            } else {
                dialogo.dismiss()
                aoConfirmar(valor)
            }
        }
    }
    dialogo.show()
}

internal const val APAGAR = "apagar"
private const val MENOS = "−"
private const val MAX_DIGITOS = 7
private val TECLAS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ",", "0", APAGAR)

/**
 * Enche uma grelha de 3 colunas com teclas grandes (usada pelo teclado de valores e pelo de PIN).
 * [ativa] diz que teclas estão ativas; [aoPremir] recebe o texto da tecla (ou APAGAR / OK).
 */
fun Fragment.preencherTeclas(grelha: GridLayout, teclas: List<String>, ativa: (String) -> Boolean = { true }, aoPremir: (String) -> Unit) {
    for (tecla in teclas) {
        val botao = ItemTeclaBinding.inflate(layoutInflater, grelha, false).root
        when (tecla) {
            APAGAR -> {
                botao.setIconResource(R.drawable.ic_backspace)
                botao.contentDescription = getString(R.string.tecla_apagar)
            }
            OK -> botao.setText(R.string.tecla_ok)
            else -> {
                botao.text = tecla
                botao.isEnabled = ativa(tecla)
            }
        }
        botao.setOnClickListener { aoPremir(tecla) }
        // 3 colunas da mesma largura
        botao.layoutParams = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
        ).apply { width = 0 }
        grelha.addView(botao)
    }
}

internal const val OK = "ok"
internal val TECLAS_PIN = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", APAGAR, "0", OK)
