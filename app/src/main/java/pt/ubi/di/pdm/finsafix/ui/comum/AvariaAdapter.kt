package pt.ubi.di.pdm.finsafix.ui.comum

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.ContextCompat
import android.widget.Chronometer
import android.os.SystemClock
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.databinding.ItemAvariaBinding
import pt.ubi.di.pdm.finsafix.util.Datas

/** Uma linha de avaria. É usado por todas as listas (Início, Avarias do técnico, Minhas, Histórico). */
class AvariaAdapter(
    private val aoClicar: (AvariaResumo) -> Unit
) : ListAdapter<AvariaResumo, AvariaAdapter.ViewHolder>(Diferencas) {

    class ViewHolder(val binding: ItemAvariaBinding) : RecyclerView.ViewHolder(binding.root)

    // avaria → início da paragem da máquina; só os ecrãs que a conhecem a preenchem (Estado da oficina)
    private var paradas: Map<Long, Long> = emptyMap()

    // avarias em curso em que o técnico do ecrã é ajudante: levam “Na equipa”
    private var naEquipa: Set<Long> = emptySet()

    @SuppressLint("NotifyDataSetChanged") // a linha “Máquina parada” de qualquer avaria pode aparecer ou desaparecer
    fun definirParadas(novas: Map<Long, Long>) {
        if (paradas == novas) return
        paradas = novas
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged") // a etiqueta pode aparecer ou desaparecer em qualquer linha
    fun definirNaEquipa(novas: Set<Long>) {
        if (naEquipa == novas) return
        naEquipa = novas
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAvariaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val resumo = getItem(position)
        val avaria = resumo.avaria
        val context = holder.itemView.context
        with(holder.binding) {
            EstiloAvaria.aplicarPrioridade(textoPrioridade, avaria.prioridade)
            EstiloAvaria.aplicar(textoEstado, EstiloAvaria.estado(avaria.estado))
            textoCodigo.text = avaria.codigo
            iconeEspecialidade.setImageResource(EstiloAvaria.iconeEspecialidade(avaria.especialidade))
            val critica = avaria.prioridade == Avaria.CRITICA && avaria.estado != Avaria.RESOLVIDA
            iconeEspecialidade.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, if (critica) R.color.if_error_text else R.color.if_on_surface_variant))
            mostrarParada(cronometroParada, paradas[avaria.id].takeIf { avaria.estado != Avaria.RESOLVIDA })
            textoTitulo.text = avaria.titulo
            textoLocal.text = context.getString(
                R.string.avaria_local,
                resumo.equipamentoNome,
                resumo.secaoNome,
                context.getString(EstiloAvaria.especialidade(avaria.especialidade))
            )
            textoTempo.text = descreverTempo(context, resumo)

            // Trabalho deixado em progresso: mostra quando se retoma e a nota de quem saiu.
            val emProgresso = avaria.estado == Avaria.PENDENTE && avaria.progressoNota != null
            textoRetoma.isVisible = emProgresso
            textoNota.isVisible = emProgresso
            if (emProgresso) {
                textoRetoma.setText(
                    if (avaria.progressoRetoma == Avaria.RETOMA_DIA) R.string.em_progresso_retoma_dia
                    else R.string.em_progresso_retoma_turno
                )
                textoNota.text = context.getString(
                    R.string.avaria_nota_progresso, avaria.progressoNota, resumo.progressoPorNome ?: ""
                )
            }
            root.setOnClickListener { aoClicar(resumo) }
        }
    }

    /** "Máquina parada 00:12:03", a contar sozinha; sem paragem a linha desaparece. */
    private fun mostrarParada(cronometro: Chronometer, inicio: Long?) {
        cronometro.isVisible = inicio != null
        if (inicio == null) {
            cronometro.stop()
            return
        }
        cronometro.base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - inicio)
        cronometro.setOnChronometerTickListener { escreverTempoParado(it) }
        escreverTempoParado(cronometro)
        cronometro.start()
    }

    private fun escreverTempoParado(cronometro: Chronometer) {
        val contexto = cronometro.context
        val segundos = (SystemClock.elapsedRealtime() - cronometro.base) / 1000
        cronometro.text = contexto.getString(
            R.string.avaria_maquina_parada, contexto.getString(R.string.tempo_hms, segundos / 3600, segundos / 60 % 60, segundos % 60)
        )
    }

    /** Ex.: "Aberta há 40 min · Sem técnico atribuído", "… · Técnico: Carlos Mendes +1" ou, para um ajudante, "… · Na equipa". */
    private fun descreverTempo(context: Context, resumo: AvariaResumo): String {
        val avaria = resumo.avaria
        val quando = if (avaria.estado == Avaria.RESOLVIDA && avaria.dataFim != null) {
            context.getString(R.string.avaria_resolvida_em, Datas.dataHora(avaria.dataFim))
        } else {
            context.getString(R.string.avaria_aberta_ha, Datas.duracao(context, System.currentTimeMillis() - avaria.dataCriacao))
        }
        val quem = resumo.tecnicoNome?.let { context.getString(R.string.avaria_tecnico, EstiloAvaria.tecnicoComEquipa(context, it, resumo.ajudantes)) }
            ?: context.getString(R.string.avaria_sem_tecnico)
        val etiqueta = if (avaria.id in naEquipa) EstiloAvaria.SEPARADOR + context.getString(R.string.na_equipa) else ""
        return quando + EstiloAvaria.SEPARADOR + quem + etiqueta
    }

    private object Diferencas : DiffUtil.ItemCallback<AvariaResumo>() {
        override fun areItemsTheSame(antigo: AvariaResumo, novo: AvariaResumo) = antigo.avaria.id == novo.avaria.id
        override fun areContentsTheSame(antigo: AvariaResumo, novo: AvariaResumo) = antigo == novo
    }

}
