package pt.ubi.di.pdm.finsafix.ui.paragens

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import kotlinx.coroutines.flow.map
import pt.ubi.di.pdm.finsafix.data.ParagemResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase

/**
 * A paragem ativa que aparece no chip (tablet) ou na faixa (telemóvel) de todos os ecrãs.
 * O operador e o chefe de secção veem a da sua secção; os restantes perfis veem a mais recente da fábrica.
 * Fica na Activity, por isso vale para todos os ecrãs.
 */
class ParagemAtivaViewModel(application: Application) : ViewModelBase(application) {

    // "" quer dizer todas as secções
    private val secao = MutableLiveData<String>()

    val paragem: LiveData<ParagemResumo?> = secao.switchMap { id ->
        repositorio.paragensAtivas(id.ifEmpty { null }).map { it.firstOrNull() }.paraLiveData(null)
    }

    fun definirPessoa(pessoa: Pessoa) {
        val daSecao = pessoa.papel == Pessoa.PAPEL_OPERADOR || (pessoa.papel == Pessoa.PAPEL_CHEFE && !pessoa.eChefeDeOficina())
        val id = if (daSecao) pessoa.postoId.orEmpty() else ""
        if (secao.value != id) secao.value = id
    }
}
