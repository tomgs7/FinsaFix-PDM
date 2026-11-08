package pt.ubi.di.pdm.finsafix.ui.comum

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import pt.ubi.di.pdm.finsafix.R

/**
 * Base dos assistentes em janela: uma janela modal no tablet e uma folha inferior no telemóvel
 * (R.bool.janela_em_folha). O conteúdo é o mesmo: quem herda só escreve o onCreateView.
 * Mostra-se com show(childFragmentManager, ...) a partir de um ecrã, para o NavController continuar acessível.
 */
abstract class JanelaOuFolha : DialogFragment() {

    private val emFolha get() = resources.getBoolean(R.bool.janela_em_folha)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        if (emFolha) BottomSheetDialog(requireContext()) else AppCompatDialog(requireContext(), R.style.Theme_FinsaFix_Janela)

    override fun onStart() {
        super.onStart()
        if (emFolha) {
            // A folha abre por inteiro (o conteúdo pode ser alto) em vez de ficar a meio
            (dialog as BottomSheetDialog).behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        } else {
            dialog?.window?.setLayout(resources.getDimensionPixelSize(R.dimen.largura_janela), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}
