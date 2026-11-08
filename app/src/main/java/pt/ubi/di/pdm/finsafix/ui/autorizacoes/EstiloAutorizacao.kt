package pt.ubi.di.pdm.finsafix.ui.autorizacoes

import android.widget.TextView
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Autorizacao
import pt.ubi.di.pdm.finsafix.data.Repositorio.ResultadoAutorizacao
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.util.Datas

/** Como se mostra uma autorização de trabalho: nome e ícone do tipo, checklists, estado e mensagens. */
object EstiloAutorizacao {

    @StringRes
    fun nome(tipo: String): Int = when (tipo) {
        Autorizacao.QUENTE -> R.string.aut_tipo_quente
        Autorizacao.CONSIGNACAO -> R.string.aut_tipo_consignacao
        Autorizacao.BLOQUEIO -> R.string.aut_tipo_bloqueio
        Autorizacao.ALTURA -> R.string.aut_tipo_altura
        else -> R.string.aut_tipo_confinado
    }

    /** “Corte e soldadura + Trabalho em altura”: o nome de todos os tipos pedidos. */
    fun nomes(context: android.content.Context, tipo: String): String = tipo.split(",").joinToString(" + ") { context.getString(nome(it)) }

    /** A checklist do chefe: os pontos de cada tipo pedido, sem repetir. */
    fun checklistDe(recursos: android.content.res.Resources, tipo: String): List<String> =
        tipo.split(",").flatMap { recursos.getStringArray(checklist(it)).toList() }.distinct()

    /** A checklist de fecho de todos os tipos pedidos, sem repetir. */
    fun fechoDe(recursos: android.content.res.Resources, tipo: String): List<String> =
        tipo.split(",").flatMap { recursos.getStringArray(fecho(it)).toList() }.distinct()

    @StringRes
    fun legenda(tipo: String): Int = when (tipo) {
        Autorizacao.QUENTE -> R.string.aut_legenda_quente
        Autorizacao.CONSIGNACAO -> R.string.aut_legenda_consignacao
        Autorizacao.BLOQUEIO -> R.string.aut_legenda_bloqueio
        Autorizacao.ALTURA -> R.string.aut_legenda_altura
        else -> R.string.aut_legenda_confinado
    }

    @DrawableRes
    fun icone(tipo: String): Int = when (tipo) {
        Autorizacao.QUENTE -> R.drawable.ic_local_fire_department
        Autorizacao.CONSIGNACAO -> R.drawable.ic_bolt
        Autorizacao.BLOQUEIO -> R.drawable.ic_lock
        Autorizacao.ALTURA -> R.drawable.ic_stairs
        else -> R.drawable.ic_meeting_room
    }

    /** O que o chefe confirma antes de aprovar. */
    @ArrayRes
    fun checklist(tipo: String): Int = when (tipo) {
        Autorizacao.QUENTE -> R.array.aut_chk_quente
        Autorizacao.CONSIGNACAO -> R.array.aut_chk_consignacao
        Autorizacao.BLOQUEIO -> R.array.aut_chk_bloqueio
        Autorizacao.ALTURA -> R.array.aut_chk_altura
        else -> R.array.aut_chk_confinado
    }

    /** O que o chefe confirma antes de encerrar (no trabalho a quente a lista é vazia: conta a vigilância). */
    @ArrayRes
    fun fecho(tipo: String): Int = when (tipo) {
        Autorizacao.QUENTE -> R.array.aut_fecho_quente
        Autorizacao.CONSIGNACAO -> R.array.aut_fecho_consignacao
        Autorizacao.BLOQUEIO -> R.array.aut_fecho_bloqueio
        Autorizacao.ALTURA -> R.array.aut_fecho_altura
        else -> R.array.aut_fecho_confinado
    }

    /** Estado com ícone + texto + cor. Em vigilância de fogo mostra até que horas. */
    fun mostrarEstado(texto: TextView, aut: Autorizacao) {
        val terminado = aut.dataTrabalhoTerminado
        val estilo = when (aut.estado) {
            Autorizacao.PEDIDA -> EstiloAvaria.Estilo(R.string.aut_estado_pedida, R.drawable.ic_clock, R.color.if_warn_text)
            Autorizacao.APROVADA -> EstiloAvaria.Estilo(R.string.aut_estado_aprovada, R.drawable.ic_check_circle, R.color.if_ok_text)
            Autorizacao.RECUSADA -> EstiloAvaria.Estilo(R.string.aut_estado_recusada, R.drawable.ic_stop_x, R.color.if_error_text)
            Autorizacao.ENCERRADA -> EstiloAvaria.Estilo(R.string.aut_estado_encerrada, R.drawable.ic_check_circle, R.color.if_ok_text)
            else -> when {
                terminado == null -> EstiloAvaria.Estilo(R.string.aut_estado_em_curso, R.drawable.ic_construction, R.color.if_info_text)
                aut.vigilanciaRestanteMs(System.currentTimeMillis()) > 0 ->
                    EstiloAvaria.Estilo(R.string.aut_estado_vigilancia, R.drawable.ic_local_fire_department, R.color.if_warn_text)
                else -> EstiloAvaria.Estilo(R.string.aut_estado_por_encerrar, R.drawable.ic_clock, R.color.if_warn_text)
            }
        }
        EstiloAvaria.aplicar(texto, estilo)
        if (estilo.texto == R.string.aut_estado_vigilancia && terminado != null) {
            texto.text = texto.context.getString(R.string.aut_estado_vigilancia, Datas.hora(terminado + Autorizacao.VIGILANCIA_MS))
        }
    }

    @StringRes
    fun mensagem(resultado: ResultadoAutorizacao): Int = when (resultado) {
        ResultadoAutorizacao.OK -> R.string.aut_guardada
        ResultadoAutorizacao.SEM_PERMISSAO -> R.string.erro_aut_sem_permissao
        ResultadoAutorizacao.ESTADO_INVALIDO -> R.string.erro_aut_estado
        ResultadoAutorizacao.CHECKLIST_INCOMPLETA -> R.string.erro_aut_checklist
        ResultadoAutorizacao.PIN_INCORRETO -> R.string.erro_aut_pin
        ResultadoAutorizacao.FALTA_CADEADO -> R.string.erro_aut_cadeado
        ResultadoAutorizacao.FALTA_VIGILANTE -> R.string.erro_aut_vigilante
        ResultadoAutorizacao.VIGILANCIA_EM_CURSO -> R.string.erro_aut_vigilancia
        ResultadoAutorizacao.JA_EXISTE_ABERTA -> R.string.erro_aut_ja_existe
        ResultadoAutorizacao.EXPIRADA -> R.string.erro_aut_expirada
    }
}
