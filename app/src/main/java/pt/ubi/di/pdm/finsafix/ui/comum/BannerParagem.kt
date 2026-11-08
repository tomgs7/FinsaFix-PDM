package pt.ubi.di.pdm.finsafix.ui.comum

import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.ParagemResumo
import pt.ubi.di.pdm.finsafix.databinding.ParteBannerParagemBinding
import pt.ubi.di.pdm.finsafix.ui.producao.ProducaoFragment
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Numeros
import pt.ubi.di.pdm.finsafix.util.ProducaoPerdida

/**
 * Banner vermelho com o equipamento parado e o tempo parado (o Chronometer conta sozinho).
 * [aoVerParagens] é o botão quando a paragem não tem avaria ligada; sem ele, o botão só aparece com avaria.
 */
class BannerParagem(
    private val fragment: Fragment,
    private val vista: ParteBannerParagemBinding,
    private val aoVerParagens: (() -> Unit)? = null
) {
    private var paragem: ParagemResumo? = null

    /** Põe o banner em duas colunas no tablet (texto | cronómetro e botão) e liga o cronómetro HH:MM:SS. */
    fun iniciar() {
        if (!fragment.resources.getBoolean(R.bool.janela_em_folha)) {
            vista.corpoParagem.orientation = LinearLayout.HORIZONTAL
            for (coluna in listOf(vista.colunaParagemTexto, vista.colunaParagemTempo)) {
                coluna.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            vista.colunaParagemTempo.gravity = Gravity.END
        }
        // Tempo parado em HH:MM:SS (o formato do Chronometer, MM:SS, parece uma hora do dia).
        vista.cronometroParagem.setOnChronometerTickListener { cronometro ->
            paragem?.let { vista.textoParagemDesde.text = textoDesde(it) }
            val segundos = (SystemClock.elapsedRealtime() - cronometro.base) / 1000
            cronometro.text = fragment.getString(R.string.tempo_hms, segundos / 3600, segundos / 60 % 60, segundos % 60)
        }
    }

    fun mostrar(resumo: ParagemResumo?) {
        vista.root.isVisible = resumo != null
        paragem = resumo
        if (resumo == null) {
            vista.cronometroParagem.stop()
            return
        }
        vista.textoParagemTitulo.text = fragment.getString(R.string.banner_paragem_titulo, resumo.secaoNome)
        vista.textoParagemEquipamento.text = resumo.equipamentoNome
        vista.textoParagemDesde.text = textoDesde(resumo)
        // O Chronometer mede a partir do arranque do aparelho (elapsedRealtime), por isso converte-se o início.
        vista.cronometroParagem.base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - resumo.paragem.inicio)
        vista.cronometroParagem.start()

        val codigo = resumo.avariaCodigo
        val avariaId = resumo.paragem.avariaId
        vista.botaoParagem.isVisible = true
        if (avariaId != null && codigo != null) {
            vista.botaoParagem.setText(R.string.banner_abrir_ordem)
            vista.botaoParagem.setOnClickListener { fragment.abrirDetalhe(avariaId, codigo) }
        } else if (aoVerParagens != null) {
            vista.botaoParagem.setText(R.string.banner_ver_paragens)
            vista.botaoParagem.setOnClickListener { aoVerParagens.invoke() }
        } else {
            vista.botaoParagem.isVisible = false
        }
    }

    /** “Causa · AV-… · desde as 14:02 · produção perdida ≈ 2,4 tabuleiros” (a estimativa cresce com o tempo). */
    private fun textoDesde(resumo: ParagemResumo): String {
        val paragem = resumo.paragem
        val causa = resumo.avariaCodigo?.let { fragment.getString(R.string.paragem_causa_avaria, paragem.causa, it) } ?: paragem.causa
        val perdida = ProducaoPerdida.estimar(resumo.secaoId, paragem.inicio, System.currentTimeMillis())
        return fragment.getString(
            R.string.banner_desde, causa, Datas.hora(paragem.inicio), Numeros.formatar(perdida, 1),
            fragment.getString(ProducaoFragment.nomeDaUnidade(resumo.unidade))
        )
    }
}
