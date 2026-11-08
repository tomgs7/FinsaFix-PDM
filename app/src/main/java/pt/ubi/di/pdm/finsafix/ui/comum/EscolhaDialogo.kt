package pt.ubi.di.pdm.finsafix.ui.comum

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.databinding.DialogEscolhaBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemEscolhaBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemEscolhaGrupoBinding

/** Uma opção da janela de escolha; o [id] é o que volta a quem a abriu. */
class Opcao(val id: String, val titulo: String, val subtitulo: String? = null, @param:DrawableRes val icone: Int? = null)

/** Um grupo de opções, com título opcional (ex.: "Oficinas · perfil técnico"). */
class Grupo(val titulo: String?, val opcoes: List<Opcao>)

/**
 * Tudo o que a janela de escolha precisa.
 * - [textoConfirmar] nulo: escolha imediata (tocar numa opção fecha a janela); com texto: Cancelar + botão principal.
 * - [exigeAlteracao]: o botão principal só se ativa quando a escolha é diferente da [inicial].
 * - [avisoDaEscolha]: texto que muda conforme a escolha (substitui o [aviso]).
 * - [confirmaSo]: quando existe, decide sozinho se o botão principal está ativo (ex.: todos os passos marcados).
 */
class EscolhaConfig(
    val titulo: String,
    val grupos: List<Grupo>,
    val aoConfirmar: (Set<String>) -> Unit,
    val etiqueta: String? = null,
    val aviso: String? = null,
    val chipAviso: String? = null,
    val avisoDaEscolha: ((Set<String>) -> String?)? = null,
    val inicial: Set<String> = emptySet(),
    val multipla: Boolean = false,
    val textoConfirmar: String? = null,
    val exigeAlteracao: Boolean = true,
    val confirmaSo: ((Set<String>) -> Boolean)? = null,
    val colunasTablet: Int = 1,
    val larguraTabletDp: Int = 640
)

/** Abre a janela de escolha comum (janela no tablet, folha no telemóvel). */
fun Fragment.escolher(config: EscolhaConfig) {
    EscolhaDialogo().apply { this.config = config }.show(childFragmentManager, "escolha")
}

/** Janela de escolha: opções em cartões com rádio (ou caixa), numa grelha no tablet e numa coluna no telemóvel. */
class EscolhaDialogo : JanelaOuFolha() {

    // Fica em memória: se o sistema recriar a janela (rotação), não há configuração e ela fecha-se.
    var config: EscolhaConfig? = null
    private var escolha = mutableSetOf<String>()

    private var _binding: DialogEscolhaBinding? = null
    private val binding get() = _binding!!

    private val emTablet get() = !resources.getBoolean(R.bool.janela_em_folha)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogEscolhaBinding.inflate(inflater, container, false)
        val c = config ?: return binding.root
        escolha = c.inicial.toMutableSet()
        mostrarCabecalho(c)
        mostrarLista(c)
        mostrarRodape(c)
        atualizar(c)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val c = config
        if (c == null) {
            dismissAllowingStateLoss()
            return
        }
        if (emTablet) {
            // A largura pedida, mas sem passar do ecrã (menos 48 dp de margem)
            val densidade = resources.displayMetrics.density
            val maximo = resources.displayMetrics.widthPixels - (48 * densidade).toInt()
            dialog?.window?.setLayout(minOf((c.larguraTabletDp * densidade).toInt(), maximo), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun mostrarCabecalho(c: EscolhaConfig) {
        binding.textoTitulo.text = c.titulo
        binding.textoEtiqueta.text = c.etiqueta
        binding.textoEtiqueta.isVisible = c.etiqueta != null
        binding.chipAviso.text = c.chipAviso
        binding.chipAviso.isVisible = c.chipAviso != null
        binding.botaoFechar.setOnClickListener { dismiss() }
    }

    private fun mostrarLista(c: EscolhaConfig) {
        val colunas = if (emTablet) c.colunasTablet else 1
        val linhas = c.grupos.flatMap { grupo -> listOfNotNull(grupo.titulo?.let { Linha.Titulo(it) }) + grupo.opcoes.map { Linha.Item(it) } }
        binding.lista.layoutManager = GridLayoutManager(requireContext(), colunas).apply {
            // Os títulos dos grupos ocupam a largura toda
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(posicao: Int) = if (linhas[posicao] is Linha.Titulo) colunas else 1
            }
        }
        binding.lista.adapter = EscolhaAdapter(linhas, c.multipla, escolha) { opcao -> aoTocar(c, opcao) }
    }

    private fun mostrarRodape(c: EscolhaConfig) {
        binding.rodape.isVisible = c.textoConfirmar != null
        binding.botaoConfirmar.text = c.textoConfirmar
        binding.botaoCancelar.setOnClickListener { dismiss() }
        binding.botaoConfirmar.setOnClickListener {
            dismiss()
            c.aoConfirmar(escolha.toSet())
        }
    }

    @SuppressLint("NotifyDataSetChanged") // ao escolher, mudam a opção marcada e a que deixou de estar marcada
    private fun aoTocar(c: EscolhaConfig, opcao: Opcao) {
        when {
            c.multipla -> if (!escolha.remove(opcao.id)) escolha.add(opcao.id)
            else -> {
                escolha.clear()
                escolha.add(opcao.id)
            }
        }
        // Sem botão principal, tocar numa opção escolhe-a logo
        if (c.textoConfirmar == null) {
            dismiss()
            c.aoConfirmar(escolha.toSet())
            return
        }
        binding.lista.adapter?.notifyDataSetChanged()
        atualizar(c)
    }

    /** Atualiza o aviso e o botão principal conforme a escolha atual. */
    private fun atualizar(c: EscolhaConfig) {
        val aviso = c.avisoDaEscolha?.invoke(escolha) ?: c.aviso
        binding.textoAviso.text = aviso
        binding.textoAviso.isVisible = aviso != null
        binding.linhaAviso.isVisible = aviso != null || c.chipAviso != null
        val mudou = escolha != c.inicial
        binding.botaoConfirmar.isEnabled = c.confirmaSo?.invoke(escolha) ?: if (c.exigeAlteracao) escolha.isNotEmpty() && mudou else true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private sealed class Linha {
    class Titulo(val texto: String) : Linha()
    class Item(val opcao: Opcao) : Linha()
}

private class EscolhaAdapter(
    private val linhas: List<Linha>,
    private val multipla: Boolean,
    private val escolha: Set<String>,
    private val aoTocar: (Opcao) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(posicao: Int) = if (linhas[posicao] is Linha.Titulo) TITULO else ITEM

    override fun getItemCount() = linhas.size

    override fun onCreateViewHolder(pai: ViewGroup, tipo: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(pai.context)
        return if (tipo == TITULO) TituloHolder(ItemEscolhaGrupoBinding.inflate(inflater, pai, false))
        else ItemHolder(ItemEscolhaBinding.inflate(inflater, pai, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, posicao: Int) {
        when (val linha = linhas[posicao]) {
            is Linha.Titulo -> (holder as TituloHolder).binding.root.text = linha.texto
            is Linha.Item -> mostrarItem((holder as ItemHolder).binding, linha.opcao)
        }
    }

    private fun mostrarItem(item: ItemEscolhaBinding, opcao: Opcao) {
        val marcada = opcao.id in escolha
        item.textoTitulo.text = opcao.titulo
        item.textoSubtitulo.text = opcao.subtitulo
        item.textoSubtitulo.isVisible = opcao.subtitulo != null
        opcao.icone?.let { item.icone.setImageResource(it) }
        item.icone.isVisible = opcao.icone != null
        item.marcaRadio.isVisible = !multipla
        item.marcaCaixa.isVisible = multipla
        item.marcaRadio.isChecked = marcada
        item.marcaCaixa.isChecked = marcada
        item.cartao.isChecked = marcada
        item.cartao.setOnClickListener { aoTocar(opcao) }
    }

    private class TituloHolder(val binding: ItemEscolhaGrupoBinding) : RecyclerView.ViewHolder(binding.root)
    private class ItemHolder(val binding: ItemEscolhaBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val TITULO = 0
        const val ITEM = 1
    }
}
