package pt.ubi.di.pdm.finsafix.ui.comum

import android.view.LayoutInflater
import android.widget.LinearLayout
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.MembroEquipa
import pt.ubi.di.pdm.finsafix.data.iniciaisDe
import pt.ubi.di.pdm.finsafix.databinding.ItemMembroEquipaBinding
import pt.ubi.di.pdm.finsafix.util.Datas

/**
 * Desenha a equipa de uma avaria: um membro por linha, com avatar de iniciais, nome e “Responsável · desde as 10:35”
 * (ou “Ajudante · …”). Usado no detalhe da avaria e nos cartões do ecrã Equipa.
 */
fun mostrarMembros(inflater: LayoutInflater, destino: LinearLayout, membros: List<MembroEquipa>, responsavelId: String?) {
    destino.removeAllViews()
    for (membro in membros) {
        val item = ItemMembroEquipaBinding.inflate(inflater, destino, false)
        val desde = Datas.hora(membro.participacao.dataEntrada)
        item.textoIniciais.text = iniciaisDe(membro.nome)
        item.textoNome.text = membro.nome
        item.textoPapel.text = destino.context.getString(
            if (membro.participacao.tecnicoId == responsavelId) R.string.equipa_responsavel else R.string.equipa_ajudante, desde
        )
        destino.addView(item.root)
    }
}
