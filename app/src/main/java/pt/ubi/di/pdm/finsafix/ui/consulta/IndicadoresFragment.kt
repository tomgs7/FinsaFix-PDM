package pt.ubi.di.pdm.finsafix.ui.consulta

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
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.ui.comum.escolherSecao
import pt.ubi.di.pdm.finsafix.data.Indicadores
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.databinding.FragmentIndicadoresBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemIndicadorBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** Engenharia: MTBF e MTTR de cada equipamento (o cálculo está em [Indicadores], chamado pelo Repositório). */
class IndicadoresFragment : Fragment() {

    private var _binding: FragmentIndicadoresBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConsultaViewModel by viewModels()
    private val adapter = IndicadorAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIndicadoresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.definirPessoa(pessoaOuVoltarAEntrada() ?: return)

        binding.listaIndicadores.adapter = adapter
        binding.listaIndicadores.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL)
        )
        binding.botaoSecaoIndicadores.setOnClickListener { escolherSecao() }
        viewModel.secoes.observe(viewLifecycleOwner) { }
        viewModel.filtro.observe(viewLifecycleOwner) { binding.botaoSecaoIndicadores.text = it.secao?.nome ?: getString(R.string.consulta_todas_secoes) }
        binding.chipsPeriodo.setOnCheckedStateChangeListener { _, ids ->
            viewModel.mudarPeriodo(if (ids.firstOrNull() == R.id.chip30dias) 30 else 7)
        }
        viewModel.analise.observe(viewLifecycleOwner) { analise ->
            adapter.submitList(analise.porEquipamento)
            binding.textoSemIndicadores.isVisible = analise.porEquipamento.isEmpty()
            val semDados = getString(R.string.indicador_sem_dados)
            binding.textoKpis.text = getString(
                R.string.indicadores_kpis, analise.avarias, analise.anterior, Datas.duracao(requireContext(), analise.paradoPorAvariaMs),
                analise.mtbfMs?.let { Datas.duracao(requireContext(), it) } ?: semDados,
                analise.mttrMs?.let { Datas.duracao(requireContext(), it) } ?: semDados
            )
            binding.grafico.definir(analise.porDia)
            binding.textoPorSecao.text = analise.porSecao.joinToString("\n") { "${it.first} · ${it.second}" }.ifEmpty { semDados }
        }
        mostrarErrosDe(viewModel)
    }

    /** “Todas as secções” ou uma das secções (as oficinas não têm avarias). */
    private fun escolherSecao() {
        val secoes = viewModel.secoes.value.orEmpty().filter { it.tipo == Secao.TIPO_SECAO }
        escolherSecao(getString(R.string.consulta_escolher_secao), secoes, comTodas = true) { viewModel.mudarSecao(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class IndicadorAdapter : ListAdapter<Indicadores.PorEquipamento, IndicadorAdapter.ViewHolder>(Diferencas) {

    class ViewHolder(val binding: ItemIndicadorBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemIndicadorBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val indicador = getItem(position)
        val context = holder.itemView.context
        val semDados = context.getString(R.string.indicador_sem_dados)
        with(holder.binding) {
            textoEquipamento.text = listOf(indicador.equipamentoNome, indicador.secaoNome).joinToString(EstiloAvaria.SEPARADOR)
            textoAvarias.text = context.resources.getQuantityString(R.plurals.indicador_avarias, indicador.avarias, indicador.avarias)
            textoMtbf.text = context.getString(R.string.indicador_mtbf, indicador.mtbfMs?.let { Datas.duracao(context, it) } ?: semDados)
            textoMttr.text = context.getString(R.string.indicador_mttr, indicador.mttrMs?.let { Datas.duracao(context, it) } ?: semDados)
        }
    }

    private object Diferencas : DiffUtil.ItemCallback<Indicadores.PorEquipamento>() {
        override fun areItemsTheSame(antigo: Indicadores.PorEquipamento, novo: Indicadores.PorEquipamento) =
            antigo.equipamentoId == novo.equipamentoId
        override fun areContentsTheSame(antigo: Indicadores.PorEquipamento, novo: Indicadores.PorEquipamento) = antigo == novo
    }
}
