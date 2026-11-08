package pt.ubi.di.pdm.finsafix.ui.comum

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.ui.detalhe.DetalheFragment

/** Mostra numa Snackbar os erros que o ViewModel indicar (ex.: "Não foi possível guardar."). */
fun Fragment.mostrarErrosDe(viewModel: ViewModelBase) {
    viewModel.erro.observe(viewLifecycleOwner) { mensagem ->
        if (mensagem != null) {
            mostrarMensagem(mensagem)
            viewModel.erroMostrado()
        }
    }
    viewModel.aviso.observe(viewLifecycleOwner) { mensagem ->
        if (mensagem != null) {
            mostrarMensagem(mensagem)
            viewModel.avisoMostrado()
        }
    }
}

/**
 * A Snackbar procura sozinha a vista raiz da Activity a partir da vista do Fragment e fica lá,
 * por isso continua visível depois de mudar de ecrã (ex.: "Avaria reportada." ao voltar ao Início).
 */
fun Fragment.mostrarMensagem(@StringRes mensagem: Int) {
    mostrarMensagem(getString(mensagem))
}

fun Fragment.mostrarMensagem(mensagem: String) {
    Snackbar.make(requireView(), mensagem, Snackbar.LENGTH_LONG).show()
}

/** Abre o detalhe de uma avaria; usado por todas as listas. O código aparece como título da barra. */
fun Fragment.abrirDetalhe(resumo: AvariaResumo) = abrirDetalhe(resumo.avaria.id, resumo.avaria.codigo)

fun Fragment.abrirDetalhe(avariaId: Long, codigo: String) {
    findNavController().navigate(R.id.acao_para_detalhe, DetalheFragment.argumentos(requireContext(), avariaId, codigo))
}

/** Pede confirmação antes de uma ação que não se desfaz (ex.: enviar um aviso). */
fun Fragment.confirmar(@StringRes titulo: Int, mensagem: String, @StringRes botao: Int, aoConfirmar: () -> Unit) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(titulo)
        .setMessage(mensagem)
        .setPositiveButton(botao) { _, _ -> aoConfirmar() }
        .setNegativeButton(R.string.botao_cancelar, null)
        .show()
}
