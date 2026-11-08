package pt.ubi.di.pdm.finsafix.ui.consulta

import pt.ubi.di.pdm.finsafix.util.Numeros
import android.app.Application
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import kotlinx.coroutines.flow.combine
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Indicadores
import pt.ubi.di.pdm.finsafix.data.Peca
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Preventiva
import pt.ubi.di.pdm.finsafix.databinding.FragmentEngenhariaInicioBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemDecisaoBinding
import pt.ubi.di.pdm.finsafix.databinding.ParteKpiBinding
import pt.ubi.di.pdm.finsafix.ui.comum.BannerParagem
import pt.ubi.di.pdm.finsafix.ui.paragens.ParagemAtivaViewModel
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.EstadoGuardado
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** Os números do Início da engenharia: o que precisa de decisão e a consulta dos últimos 30 dias. */
data class DadosEngenharia(
    val paragensCriticas: Int, val porClassificar: Int, val porAprovar: Int, val preventivasVencidas: Int,
    val pecasAbaixo: Int, val instrucoesPorValidar: Int, val naoSeiSemTecnico: Int,
    val abertas: Int, val emCurso: Int, val avarias30: Int, val mtbfMs: Long?, val mttrMs: Long?
)

class EngenhariaInicioViewModel(application: Application) : ViewModelBase(application) {

    private val pessoa = MutableLiveData<Pessoa>()

    val dados: LiveData<DadosEngenharia> = pessoa.switchMap { quem ->
        combine(
            repositorio.avariasDeConsulta(quem, null, Avaria.ESTADOS),
            repositorio.paragensAtivas(null),
            repositorio.preventivas(),
            repositorio.pecas(),
            repositorio.contarInstrucoesPorValidar()
        ) { avarias, paragens, preventivas, pecas, instrucoes ->
            val agora = System.currentTimeMillis()
            val ultimos30 = avarias.filter { it.avaria.dataCriacao >= agora - TRINTA_DIAS_MS }
            val mtbfs = Indicadores.porEquipamento(ultimos30).mapNotNull { it.mtbfMs }
            DadosEngenharia(
                paragensCriticas = paragens.size,
                porClassificar = avarias.count { it.avaria.especialidade == Avaria.NAO_SEI && it.avaria.estado == Avaria.PENDENTE },
                porAprovar = avarias.count { it.avaria.porAprovar() },
                preventivasVencidas = preventivas.count { it.preventiva.estado(agora) == Preventiva.Estado.VENCIDA },
                pecasAbaixo = pecas.count { it.peca.estado() <= Peca.Estado.ABAIXO },
                instrucoesPorValidar = instrucoes,
                // “Não sei” sem técnico há mais de 15 min
                naoSeiSemTecnico = avarias.count {
                    it.avaria.especialidade == Avaria.NAO_SEI && it.avaria.estado == Avaria.PENDENTE && it.avaria.dataCriacao <= agora - QUINZE_MIN_MS
                },
                abertas = avarias.count { it.avaria.estado != Avaria.RESOLVIDA },
                emCurso = avarias.count { it.avaria.estado == Avaria.EM_CURSO },
                avarias30 = ultimos30.size,
                mtbfMs = if (mtbfs.isEmpty()) null else mtbfs.sum() / mtbfs.size,
                mttrMs = Indicadores.mttr(ultimos30.map { it.avaria })
            )
        }.paraLiveData(DadosEngenharia(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null))
    }

    fun definirPessoa(quem: Pessoa) {
        if (pessoa.value != quem) pessoa.value = quem
    }

    private companion object {
        const val TRINTA_DIAS_MS = 30L * 24 * 60 * 60 * 1000
        const val QUINZE_MIN_MS = 15 * 60_000L
    }
}

/** Uma linha de “Precisa da sua decisão”; [critica] pinta o ícone e o número de vermelho. */
private class Decisao(val icone: Int, val titulo: String, val sub: String, val numero: Int, val destino: Int, val critica: Boolean = false)

/** “Início” da engenharia: paragem ativa, “Precisa da sua decisão” (cada linha abre o ecrã certo), consulta a 30 dias e PINs errados. */
class EngenhariaInicioFragment : Fragment() {

    private var _binding: FragmentEngenhariaInicioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EngenhariaInicioViewModel by viewModels()
    private val paragemAtiva: ParagemAtivaViewModel by activityViewModels()
    private val adapter = DecisaoAdapter { findNavController().navigate(it.destino) }
    private lateinit var banner: BannerParagem

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEngenhariaInicioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.definirPessoa(pessoaOuVoltarAEntrada() ?: return)
        banner = BannerParagem(this, binding.bannerParagem)
        banner.iniciar()
        paragemAtiva.paragem.observe(viewLifecycleOwner) { banner.mostrar(it) }
        binding.listaDecisoes.layoutManager = LinearLayoutManager(requireContext())
        binding.listaDecisoes.adapter = adapter
        binding.listaDecisoes.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.dados.observe(viewLifecycleOwner) { mostrar(it) }
        mostrarErrosDe(viewModel)
    }

    private fun mostrar(d: DadosEngenharia) {
        val classificarSub = if (d.naoSeiSemTecnico > 0) resources.getQuantityString(R.plurals.eng_decisao_classificar_sem_tecnico, d.naoSeiSemTecnico, d.naoSeiSemTecnico)
        else getString(R.string.eng_decisao_classificar_sub)
        adapter.submitList(
            listOf(
                Decisao(R.drawable.ic_stop_x, getString(R.string.eng_decisao_paragens), getString(R.string.eng_decisao_paragens_sub), d.paragensCriticas, R.id.paragensFragment, critica = true),
                Decisao(R.drawable.ic_help, getString(R.string.eng_decisao_classificar), classificarSub, d.porClassificar, R.id.avariasFabricaFragment),
                Decisao(R.drawable.ic_verified, getString(R.string.eng_decisao_aprovar), getString(R.string.eng_decisao_aprovar_sub), d.porAprovar, R.id.avariasFabricaFragment),
                Decisao(R.drawable.ic_calendar, getString(R.string.eng_decisao_preventivas), getString(R.string.eng_decisao_preventivas_sub), d.preventivasVencidas, R.id.preventivaFragment),
                Decisao(R.drawable.ic_box, getString(R.string.eng_decisao_pecas), getString(R.string.eng_decisao_pecas_sub), d.pecasAbaixo, R.id.stockFragment),
                Decisao(R.drawable.ic_book, getString(R.string.eng_decisao_instrucoes), getString(R.string.eng_decisao_instrucoes_sub), d.instrucoesPorValidar, R.id.instrucoesEngFragment)
            )
        )
        val semDados = getString(R.string.indicador_sem_dados)
        mostrarKpi(binding.kpiAbertas, R.string.eng_kpi_abertas, d.abertas.toString(), getString(R.string.eng_kpi_abertas_sub, d.emCurso))
        mostrarKpi(binding.kpiAvarias30, R.string.eng_kpi_30, d.avarias30.toString(), getString(R.string.eng_kpi_30_sub))
        mostrarKpi(binding.kpiMtbf, R.string.eng_kpi_mtbf, d.mtbfMs?.let { Datas.duracao(requireContext(), it) } ?: semDados, getString(R.string.eng_kpi_mtbf_sub))
        mostrarKpi(binding.kpiMttr, R.string.eng_kpi_mttr, d.mttrMs?.let { Datas.duracao(requireContext(), it) } ?: semDados, getString(R.string.eng_kpi_mttr_sub))
        binding.textoPins.text = textoPins()
    }

    private fun mostrarKpi(kpi: ParteKpiBinding, rotulo: Int, valor: String, sub: String) {
        kpi.textoRotulo.setText(rotulo)
        kpi.textoValor.text = valor
        kpi.textoSub.text = sub
    }

    /** “Nenhum PIN errado nas últimas 24 h” ou “2 PINs errados nas últimas 24 h · último às 14:02”. */
    private fun textoPins(): String {
        val erros = EstadoGuardado(requireContext()).errosDesde(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
        return if (erros.isEmpty()) getString(R.string.eng_pins_nenhum)
        else resources.getQuantityString(R.plurals.eng_pins_errados, erros.size, erros.size, Datas.hora(erros.last()))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class DecisaoAdapter(private val aoTocar: (Decisao) -> Unit) : ListAdapter<Decisao, DecisaoAdapter.ViewHolder>(Diferencas) {

    class ViewHolder(val binding: ItemDecisaoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemDecisaoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val contexto = holder.itemView.context
        // Vermelho só para o crítico e só quando há casos; o resto fica cinzento ou neutro
        val vermelho = item.critica && item.numero > 0
        with(holder.binding) {
            icone.setImageResource(item.icone)
            icone.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(contexto, if (vermelho) R.color.if_error_text else R.color.if_on_surface_variant))
            textoTitulo.text = item.titulo
            textoSub.text = item.sub
            textoNumero.text = Numeros.formatar(item.numero)
            textoNumero.setTextColor(ContextCompat.getColor(contexto, if (vermelho) R.color.if_error_text else if (item.numero > 0) R.color.if_on_surface else R.color.if_on_surface_variant))
            root.setOnClickListener { aoTocar(item) }
        }
    }

    private object Diferencas : DiffUtil.ItemCallback<Decisao>() {
        override fun areItemsTheSame(antigo: Decisao, novo: Decisao) = antigo.titulo == novo.titulo
        override fun areContentsTheSame(antigo: Decisao, novo: Decisao) =
            antigo.numero == novo.numero && antigo.sub == novo.sub
    }
}
