package pt.ubi.di.pdm.finsafix.ui.producao

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.ProducaoResumo
import pt.ubi.di.pdm.finsafix.databinding.FragmentProducaoBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemEventoBinding
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarMensagem
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Numeros
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** Produção: o plano do turno, o total e os registos. O registo abre em [ProducaoDialogo]. */
class ProducaoFragment : Fragment() {

    private var _binding: FragmentProducaoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProducaoViewModel by viewModels()
    private val adapter = RegistoAdapter()
    private var unidade = ""
    private var unidadeCodigo = ""
    private var pessoaAtual: Pessoa? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProducaoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return

        pessoaAtual = pessoa
        binding.botaoAbrirRegisto.setOnClickListener { ProducaoDialogo().show(childFragmentManager, "producao") }
        viewModel.secoes.observe(viewLifecycleOwner) { }
        viewModel.ordens.observe(viewLifecycleOwner) { mostrarPlano() }

        binding.listaRegistos.adapter = adapter
        binding.listaRegistos.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))

        viewModel.secao.observe(viewLifecycleOwner) { secao ->
            unidadeCodigo = secao?.unidade ?: ""
            unidade = getString(nomeDaUnidade(unidadeCodigo))
            mostrarTotal()
            mostrarPlano()
        }
        viewModel.registosDoTurno.observe(viewLifecycleOwner) { registos ->
            adapter.submitList(registos)
            binding.textoSemRegistos.isVisible = registos.isEmpty()
            mostrarTotal()
            mostrarPlano()
        }
        viewModel.mensagem.observe(viewLifecycleOwner) { mensagem ->
            if (mensagem != null) {
                mostrarMensagem(mensagem)
                viewModel.mensagemMostrada()
            }
        }
        mostrarErrosDe(viewModel)
    }

    override fun onResume() {
        super.onResume()
        viewModel.atualizarTurno()
    }

    /** “Planeado 75 · Produzido 46 · 61 % · Falta 29 · Ritmo necessário 5,2 tab/h” e as ordens do turno. */
    private fun mostrarPlano() {
        val binding = _binding ?: return
        val ordens = viewModel.ordens.value.orEmpty()
        val registos = viewModel.registosDoTurno.value.orEmpty()
        val planeado = ordens.sumOf { it.ordem.planeado }
        val produzido = registos.sumOf { it.producao.quantidade }
        val falta = (planeado - produzido).coerceAtLeast(0.0)
        val horasRestantes = ((Datas.inicioDoTurnoAtual() + OITO_HORAS_MS - System.currentTimeMillis()) / 3_600_000.0).coerceAtLeast(0.25)
        val casas = decimaisDe(unidadeCodigo)
        val percentagem = if (planeado > 0) (produzido * 100 / planeado).toInt() else 0
        // A produzir agora: a primeira ordem ainda por cumprir
        val agora = ordens.firstOrNull { ordem -> registos.filter { it.equipamentoNome == ordem.equipamentoNome }.sumOf { it.producao.quantidade } < ordem.ordem.planeado }
        val linhas = ordens.joinToString("\n") {
            getString(R.string.plano_ordem, it.equipamentoNome, Numeros.formatar(it.ordem.planeado, casas), unidade)
        }
        binding.textoPlano.text = getString(
            R.string.plano_kpis, Numeros.formatar(planeado, casas), Numeros.formatar(produzido, casas), percentagem,
            Numeros.formatar(falta, casas), Numeros.formatar(falta / horasRestantes, 1), unidade,
            agora?.equipamentoNome ?: getString(R.string.plano_nada_agora), linhas
        )
    }

    /** Chamado quando chegam os registos e quando chega a unidade (o Room não garante qual vem primeiro). */
    private fun mostrarTotal() {
        val total = viewModel.registosDoTurno.value.orEmpty().sumOf { it.producao.quantidade }
        binding.textoTotal.text = getString(R.string.producao_total, Numeros.formatar(total, decimaisDe(unidadeCodigo)), unidade)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** Uma linha do turno: "46 tabuleiros cortados · Serra Schelling" e "José Martins · 12:07". */
    private inner class RegistoAdapter : ListAdapter<ProducaoResumo, RegistoAdapter.ViewHolder>(Diferencas) {
        inner class ViewHolder(val binding: ItemEventoBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(ItemEventoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val registo = getItem(position)
            holder.binding.textoEvento.text = getString(
                R.string.producao_linha, Numeros.formatar(registo.producao.quantidade, decimaisDe(unidadeCodigo)), unidade, registo.equipamentoNome
            )
            holder.binding.textoData.text = getString(R.string.detalhe_local, registo.pessoaNome, Datas.hora(registo.producao.data))
            registo.producao.cortes?.let { holder.binding.textoEvento.append(resources.getQuantityString(R.plurals.producao_cortes_n, it, it)) }
            // Quem registou pode anular o seu registo (toque na linha)
            val meu = registo.producao.pessoaId == pessoaAtual?.id
            if (meu) holder.binding.textoData.append(getString(R.string.producao_toque_anular))
            holder.binding.root.setOnClickListener(if (!meu) null else View.OnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.resumo_anular_titulo)
                    .setMessage(R.string.resumo_anular_mensagem)
                    .setPositiveButton(R.string.resumo_anular) { _, _ -> pessoaAtual?.let { viewModel.anular(registo.producao.id, it) } }
                    .setNegativeButton(R.string.botao_cancelar, null)
                    .show()
            })
        }
    }

    private object Diferencas : DiffUtil.ItemCallback<ProducaoResumo>() {
        override fun areItemsTheSame(antigo: ProducaoResumo, novo: ProducaoResumo) = antigo.producao.id == novo.producao.id
        override fun areContentsTheSame(antigo: ProducaoResumo, novo: ProducaoResumo) = antigo == novo
    }

    companion object {
        private const val OITO_HORAS_MS = 8 * 60 * 60 * 1000L
        private val BOTAO_DO_TURNO = mapOf("M" to R.id.botaoManha, "T" to R.id.botaoTarde, "N" to R.id.botaoNoite)

        /** Unidade de produção de cada secção (código do JSON → texto em strings.xml). */
        fun nomeDaUnidade(codigo: String): Int = when (codigo) {
            "t" -> R.string.unidade_t
            "tf" -> R.string.unidade_tf
            "mwh" -> R.string.unidade_mwh
            "m3" -> R.string.unidade_m3
            "tabc" -> R.string.unidade_tabc
            "fol" -> R.string.unidade_fol
            "pain" -> R.string.unidade_pain
            else -> R.string.unidade_tab
        }

        /** Toneladas, m³ e MWh têm uma casa decimal; o que se conta (tabuleiros, folhas, painéis) é inteiro. */
        fun decimaisDe(codigo: String): Int = if (codigo in listOf("t", "tf", "mwh", "m3")) 1 else 0
    }
}
