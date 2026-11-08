package pt.ubi.di.pdm.finsafix.data

import androidx.room.Embedded

/** Uma avaria com os nomes já resolvidos (equipamento, secção e pessoas), para as listas e o detalhe. */
data class AvariaResumo(
    @Embedded val avaria: Avaria,
    val equipamentoNome: String,
    val secaoId: String,
    val secaoNome: String,
    val reportadaPorNome: String?,
    val tecnicoNome: String?,
    val progressoPorNome: String?,
    /** Quantos ajudantes tem a equipa (sem o responsável): “Carlos Mendes +1”. */
    val ajudantes: Int = 0
)
