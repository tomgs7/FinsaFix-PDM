package pt.ubi.di.pdm.finsafix.ui.mais

import androidx.core.content.edit
import android.content.Context
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.databinding.CartaoPrimeiraVezBinding

/** "Como usar o FinsaFix": 4 passos, conforme o perfil (técnico ou operador). */
fun Fragment.mostrarComoUsar(pessoa: Pessoa) {
    val tecnico = pessoa.papel == Pessoa.PAPEL_TECNICO
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.como_usar_titulo)
        .setMessage(if (tecnico) R.string.como_usar_tecnico else R.string.como_usar_operador)
        .setPositiveButton(R.string.como_usar_comecar, null)
        .show()
}

/** Contactos de emergência. Os valores são de exemplo: a Segurança da Luso Finsa preenche os reais. */
fun Fragment.mostrarEmergencia() {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.emergencia_titulo)
        .setMessage(R.string.emergencia_texto)
        .setPositiveButton(R.string.botao_fechar, null)
        .show()
}

/**
 * Cartão “Primeira vez no FinsaFix?” no topo do ecrã (operador e técnico). Só desaparece com “Já sei usar”;
 * a escolha fica guardada por pessoa em SharedPreferences (é uma preferência, não um registo).
 */
fun Fragment.mostrarCartaoPrimeiraVez(topo: ViewGroup, pessoa: Pessoa) {
    val preferencias = requireContext().getSharedPreferences("ajuda", Context.MODE_PRIVATE)
    val chave = "ja_sabe_" + pessoa.id
    if (preferencias.getBoolean(chave, false)) return
    val cartao = CartaoPrimeiraVezBinding.inflate(layoutInflater, topo, false)
    cartao.botaoVerComoUsar.setOnClickListener { mostrarComoUsar(pessoa) }
    cartao.botaoJaSei.setOnClickListener {
        preferencias.edit { putBoolean(chave, true) }
        topo.removeView(cartao.root)
    }
    topo.addView(cartao.root, 0)
}
