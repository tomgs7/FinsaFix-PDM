package pt.ubi.di.pdm.finsafix.ui.pedidos

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.RegistoResumo
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase

/** Pedidos de ajuda e de material de uma secção: o operador faz e confirma, o chefe responde e fecha. */
class PedidosViewModel(application: Application) : ViewModelBase(application) {

    private val posto = MutableLiveData<String>()
    val pedidos: LiveData<List<RegistoResumo>> = posto.switchMap { repositorio.pedidosDaSecao(it).paraLiveData(emptyList()) }

    fun definirPosto(id: String) {
        if (posto.value != id) posto.value = id
    }

    fun criar(tipo: String, destino: String?, texto: String, urgente: Boolean, quem: Pessoa) {
        executar {
            repositorio.criarPedido(tipo, destino, texto, urgente, quem)
            avisar(R.string.pedido_enviado)
        }
    }

    /** “Já fui atendido” (ajuda) ou “Recebido” (material). */
    fun confirmar(id: Long, quem: Pessoa) {
        executar { if (!repositorio.confirmarPedido(id, quem)) avisar(R.string.erro_pedido_mudou) }
    }

    fun responder(id: Long, resposta: String, quem: Pessoa) {
        executar { if (!repositorio.responderPedido(id, resposta, quem)) avisar(R.string.erro_pedido_mudou) }
    }

    fun fechar(id: Long, quem: Pessoa) {
        executar { if (!repositorio.fecharPedido(id, quem)) avisar(R.string.erro_pedido_mudou) }
    }
}
