package pt.ubi.di.pdm.finsafix.util

import pt.ubi.di.pdm.finsafix.data.Turno
import android.content.Context
import pt.ubi.di.pdm.finsafix.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatação de datas e durações. As datas guardam-se como milissegundos (Long). */
object Datas {

    private val PORTUGUES = Locale.forLanguageTag("pt-PT")
    private val FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PORTUGUES)
    private val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", PORTUGUES)
    private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm", PORTUGUES)
    private val FORMATO_POR_EXTENSO = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", PORTUGUES)

    private fun paraData(milissegundos: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(milissegundos), ZoneId.systemDefault())

    fun dataHora(milissegundos: Long): String = paraData(milissegundos).format(FORMATO_DATA_HORA)

    fun data(milissegundos: Long): String = paraData(milissegundos).format(FORMATO_DATA)

    fun hora(milissegundos: Long): String = paraData(milissegundos).format(FORMATO_HORA)

    /** Ex.: "Quarta-feira, 4 de novembro de 2026". */
    fun hojePorExtenso(): String =
        LocalDateTime.now().format(FORMATO_POR_EXTENSO).replaceFirstChar { it.uppercase(PORTUGUES) }

    /** Ex.: "5 h 03 min" ou "40 min". */
    fun duracao(context: Context, milissegundos: Long): String {
        val minutosTotais = (milissegundos / 60_000).coerceAtLeast(0)
        val horas = minutosTotais / 60
        val minutos = minutosTotais % 60
        return if (horas > 0) context.getString(R.string.duracao_horas, horas, minutos)
        else context.getString(R.string.duracao_minutos, minutos)
    }

    /** Turno em curso: Manhã 08h–16h, Tarde 16h–00h, Noite 00h–08h. */
    fun turnoAtual(): String = when (LocalDateTime.now().hour) {
        in 8 until 16 -> Turno.MANHA
        in 16 until 24 -> Turno.TARDE
        else -> Turno.NOITE
    }

    /** Início do turno em curso (ex.: às 15:00 → hoje às 08:00), em milissegundos. */
    /** O fim do turno em curso (cada turno tem 8 horas). */
    fun fimDoTurnoAtual(): Long = inicioDoTurnoAtual() + 8 * 60 * 60 * 1000L

    fun inicioDoTurnoAtual(): Long {
        val agora = LocalDateTime.now()
        val hora = when (turnoAtual()) {
            Turno.MANHA -> 8
            Turno.TARDE -> 16
            else -> 0
        }
        return agora.toLocalDate().atTime(hora, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun nomeTurno(turno: String): Int = when (turno) {
        Turno.MANHA -> R.string.turno_manha
        Turno.TARDE -> R.string.turno_tarde
        else -> R.string.turno_noite
    }

    fun horarioTurno(turno: String): Int = when (turno) {
        Turno.MANHA -> R.string.turno_manha_horario
        Turno.TARDE -> R.string.turno_tarde_horario
        else -> R.string.turno_noite_horario
    }
}
