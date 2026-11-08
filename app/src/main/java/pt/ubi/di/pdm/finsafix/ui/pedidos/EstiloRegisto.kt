package pt.ubi.di.pdm.finsafix.ui.pedidos

import android.content.Context
import androidx.annotation.StringRes
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Registo
import pt.ubi.di.pdm.finsafix.data.RegistoResumo
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.lista.Acao
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.util.Datas

/** Como se mostram os registos (pedidos e notas): texto, ícone e cor. Usado pelo operador, pelo chefe e pelo técnico. */
object EstiloRegisto {

    @StringRes
    fun destino(extra: String?): Int = when (extra) {
        Registo.DESTINO_CHEFE -> R.string.destino_chefe
        Registo.DESTINO_QUALIDADE -> R.string.destino_qualidade
        Registo.DESTINO_LOGISTICA -> R.string.destino_logistica
        Registo.DESTINO_SEGURANCA -> R.string.destino_seguranca
        else -> R.string.pedido_material
    }

    @StringRes
    fun descricaoDestino(extra: String): Int = when (extra) {
        Registo.DESTINO_CHEFE -> R.string.destino_chefe_sub
        Registo.DESTINO_QUALIDADE -> R.string.destino_qualidade_sub
        Registo.DESTINO_LOGISTICA -> R.string.destino_logistica_sub
        else -> R.string.destino_seguranca_sub
    }

    fun estadoPedido(estado: String): EstiloAvaria.Estilo = when (estado) {
        Registo.A_CAMINHO -> EstiloAvaria.Estilo(R.string.pedido_a_caminho, R.drawable.ic_run, R.color.if_info_text)
        Registo.ATENDIDO -> EstiloAvaria.Estilo(R.string.pedido_atendido, R.drawable.ic_check_circle, R.color.if_ok_text)
        Registo.RECEBIDO -> EstiloAvaria.Estilo(R.string.pedido_recebido, R.drawable.ic_check_circle, R.color.if_ok_text)
        Registo.FECHADO -> EstiloAvaria.Estilo(R.string.pedido_fechado, R.drawable.ic_check_circle, R.color.if_ok_text)
        else -> EstiloAvaria.Estilo(R.string.pedido_enviado_estado, R.drawable.ic_clock, R.color.if_warn_text)
    }

    fun estadoNota(categoria: String?): EstiloAvaria.Estilo = when (categoria) {
        Registo.NOTA_ATENCAO -> EstiloAvaria.Estilo(R.string.nota_atencao, R.drawable.ic_alert, R.color.if_warn_text)
        Registo.NOTA_PENDENTE -> EstiloAvaria.Estilo(R.string.nota_pendente, R.drawable.ic_clock, R.color.if_on_surface_variant)
        Registo.NOTA_INFORMACAO -> EstiloAvaria.Estilo(R.string.nota_informacao, R.drawable.ic_info, R.color.if_info_text)
        else -> EstiloAvaria.Estilo(R.string.nota_nota, R.drawable.ic_note, R.color.if_on_surface_variant)
    }

    /** “há 7 min” */
    fun haQuanto(context: Context, data: Long): String =
        context.getString(R.string.tempo_ha, Datas.duracao(context, System.currentTimeMillis() - data))

    /** Uma linha de pedido, com os botões que [acoes] indicar. */
    fun linhaDePedido(context: Context, resumo: RegistoResumo, acoes: List<Acao>): Linha {
        val registo = resumo.registo
        val partes = listOfNotNull(
            context.getString(R.string.pedido_urgente).takeIf { registo.urgente },
            registo.texto.ifBlank { null },
            haQuanto(context, registo.dataCriacao)
        )
        val resposta = registo.resposta?.let {
            context.getString(R.string.pedido_resposta, it, resumo.tratadoPorNome ?: "", Datas.hora(registo.dataTratamento ?: registo.dataCriacao))
        }
        val fechado = registo.estado == Registo.FECHADO
        return Linha(
            id = registo.id.toString(),
            estado = estadoPedido(registo.estado),
            textoEstado = if (fechado) context.getString(R.string.pedido_fechado_por, resumo.tratadoPorNome ?: "", Datas.hora(registo.dataTratamento ?: registo.dataCriacao)) else null,
            titulo = if (registo.tipo == Registo.AJUDA) context.getString(destino(registo.extra)) else context.getString(R.string.pedido_material),
            sub = partes.joinToString(EstiloAvaria.SEPARADOR),
            extra = resposta,
            acoes = acoes
        )
    }
}
