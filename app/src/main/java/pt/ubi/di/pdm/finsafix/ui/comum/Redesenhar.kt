package pt.ubi.di.pdm.finsafix.ui.comum

import android.annotation.SuppressLint
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Para listas que mudam só com a hora (destaque dos 3 minutos, fim da vigilância de fogo),
 * sem mudança na base de dados: redesenha a cada 30 s enquanto o ecrã está visível.
 */
@SuppressLint("NotifyDataSetChanged") // todas as linhas podem mudar ao mesmo tempo
fun Fragment.redesenharACada30s(adapter: RecyclerView.Adapter<*>) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(30_000)
                adapter.notifyDataSetChanged()
            }
        }
    }
}
