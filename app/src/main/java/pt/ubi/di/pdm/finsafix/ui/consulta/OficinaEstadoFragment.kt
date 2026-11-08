package pt.ubi.di.pdm.finsafix.ui.consulta

import androidx.core.widget.TextViewCompat
import android.app.Application
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Registo
import pt.ubi.di.pdm.finsafix.databinding.FragmentOficinaEstadoBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemTecnicoEstadoBinding
import pt.ubi.di.pdm.finsafix.ui.comum.AvariaAdapter
import pt.ubi.di.pdm.finsafix.ui.comum.BannerParagem
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.LinhaPorTratar
import pt.ubi.di.pdm.finsafix.ui.comum.PorTratarAdapter
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.abrirDetalhe
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.paragens.ParagemAtivaViewModel
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** O que o chefe de oficina tem por tratar; cada tipo abre o ecrã onde se resolve (Distribuir ou Validar). */
class ItemPorTratar(val tipo: Tipo, val quantidade: Int, val urgente: Boolean) {
    enum class Tipo { NAO_SEI, POR_ATRIBUIR, PASSAGENS, RONDAS }
}

/** Um técnico da oficina: se está em turno, a intervenção em curso e quantas avarias lhe foram atribuídas. */
class TecnicoEstado(val tecnico: Pessoa, val emTurno: Boolean, val aIntervir: AvariaResumo?, val atribuidas: Int)

class EstadoOficina(
    val porTratar: List<ItemPorTratar>,
    val abertas: List<AvariaResumo>,
    val tecnicos: List<TecnicoEstado>,
    // avaria → início da paragem da máquina (para a linha "Máquina parada 00:00:00")
    val paradas: Map<Long, Long>
)

class OficinaEstadoViewModel(application: Application) : ViewModelBase(application) {

    private val chefe = MutableLiveData<Pessoa>()

    val estado: LiveData<EstadoOficina> = chefe.switchMap { quem ->
        val posto = quem.postoId.orEmpty()
        combine(
            repositorio.avariasDeConsulta(quem, null, listOf(Avaria.PENDENTE, Avaria.EM_CURSO)),
            repositorio.pessoasDoPosto(posto),
            repositorio.rondasDa(quem.especialidade ?: ""),
            repositorio.contarPorValidar(posto),
            repositorio.paragensAtivas(null)
        ) { abertas, pessoas, rondas, passagens, paragens ->
            val limite = System.currentTimeMillis() - ESCALADA_MS
            val pendentes = abertas.filter { it.avaria.estado == Avaria.PENDENTE }
            // as “Não sei” primeiro têm de ser classificadas; por atribuir são as desta oficina sem técnico
            val porAtribuir = pendentes.filter { it.avaria.especialidade == quem.especialidade && it.avaria.atribuidaAId == null }
            val itens = listOf(
                ItemPorTratar(ItemPorTratar.Tipo.NAO_SEI, pendentes.count { it.avaria.especialidade == Avaria.NAO_SEI && it.avaria.dataCriacao <= limite }, false),
                ItemPorTratar(ItemPorTratar.Tipo.POR_ATRIBUIR, porAtribuir.size, porAtribuir.any { it.avaria.prioridade == Avaria.CRITICA }),
                ItemPorTratar(ItemPorTratar.Tipo.PASSAGENS, passagens, false),
                ItemPorTratar(ItemPorTratar.Tipo.RONDAS, rondas.count { it.registo.estado == Registo.NAO_CONFORME }, false)
            ).filter { it.quantidade > 0 }
            val turnoAtual = Datas.turnoAtual()
            val tecnicos = pessoas.filter { it.papel == Pessoa.PAPEL_TECNICO }
                .map { t ->
                    TecnicoEstado(
                        t, t.turno == turnoAtual,
                        abertas.firstOrNull { it.avaria.tecnicoId == t.id && it.avaria.estado == Avaria.EM_CURSO },
                        pendentes.count { it.avaria.atribuidaAId == t.id }
                    )
                }
                .sortedByDescending { it.emTurno }
            EstadoOficina(itens, abertas, tecnicos, paragens.mapNotNull { p -> p.paragem.avariaId?.let { it to p.paragem.inicio } }.toMap())
        }.paraLiveData(EstadoOficina(emptyList(), emptyList(), emptyList(), emptyMap()))
    }

    fun definirChefe(quem: Pessoa) {
        if (chefe.value != quem) chefe.value = quem
    }

    private companion object {
        const val ESCALADA_MS = 15 * 60_000L
    }
}

/** “Estado da oficina” do chefe de oficina: paragem ativa, Por tratar, avarias abertas e técnicos. */
class OficinaEstadoFragment : Fragment() {

    private var _binding: FragmentOficinaEstadoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OficinaEstadoViewModel by viewModels()
    private val paragemAtiva: ParagemAtivaViewModel by activityViewModels()
    private val adapterAvarias = AvariaAdapter { abrirDetalhe(it) }
    private val adapterPorTratar = PorTratarAdapter()
    private val adapterTecnicos = TecnicoEstadoAdapter()
    private lateinit var banner: BannerParagem

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOficinaEstadoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val chefe = pessoaOuVoltarAEntrada() ?: return
        viewModel.definirChefe(chefe)

        banner = BannerParagem(this, binding.bannerParagem)
        banner.iniciar()
        paragemAtiva.paragem.observe(viewLifecycleOwner) { banner.mostrar(it) }

        for (lista in listOf(binding.listaPorTratar, binding.listaAvarias, binding.listaTecnicos)) {
            lista.layoutManager = LinearLayoutManager(requireContext())
        }
        binding.listaPorTratar.adapter = adapterPorTratar
        binding.listaAvarias.adapter = adapterAvarias
        binding.listaTecnicos.adapter = adapterTecnicos
        for (lista in listOf(binding.listaPorTratar, binding.listaAvarias, binding.listaTecnicos)) {
            lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        }
        val oficina = getString(EstiloAvaria.especialidade(chefe.especialidade ?: "")).lowercase()
        viewModel.estado.observe(viewLifecycleOwner) { estado ->
            binding.blocoPorTratar.isVisible = estado.porTratar.isNotEmpty()
            adapterPorTratar.submitList(estado.porTratar.map { item ->
                val plural = when (item.tipo) {
                    ItemPorTratar.Tipo.NAO_SEI -> R.plurals.oficina_nao_sei
                    ItemPorTratar.Tipo.POR_ATRIBUIR -> R.plurals.oficina_por_atribuir
                    ItemPorTratar.Tipo.PASSAGENS -> R.plurals.oficina_passagens
                    ItemPorTratar.Tipo.RONDAS -> R.plurals.oficina_rondas
                }
                LinhaPorTratar(item.tipo.name, resources.getQuantityString(plural, item.quantidade, item.quantidade), item.urgente) { abrir(item) }
            })
            binding.textoAbertas.text = resources.getQuantityString(R.plurals.oficina_n_abertas, estado.abertas.size, estado.abertas.size)
            adapterAvarias.definirParadas(estado.paradas)
            adapterAvarias.submitList(estado.abertas)
            binding.textoSemAvarias.text = getString(R.string.oficina_sem_avarias, oficina)
            binding.textoSemAvarias.isVisible = estado.abertas.isEmpty()
            adapterTecnicos.submitList(estado.tecnicos)
        }
        mostrarErrosDe(viewModel)
    }

    private fun abrir(item: ItemPorTratar) {
        val destino = when (item.tipo) {
            ItemPorTratar.Tipo.NAO_SEI, ItemPorTratar.Tipo.POR_ATRIBUIR -> R.id.distribuirFragment
            ItemPorTratar.Tipo.PASSAGENS -> R.id.turnoFragment
            ItemPorTratar.Tipo.RONDAS -> R.id.validarFragment
        }
        findNavController().navigate(destino)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class TecnicoEstadoAdapter : ListAdapter<TecnicoEstado, TecnicoEstadoAdapter.ViewHolder>(Diferencas) {

    class ViewHolder(val binding: ItemTecnicoEstadoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemTecnicoEstadoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val contexto = holder.itemView.context
        with(holder.binding) {
            textoNome.text = item.tecnico.nome
            if (item.emTurno) {
                // Em turno: ícone + texto + cor (azul de informação)
                textoTurno.setText(R.string.tecnico_em_turno)
                textoTurno.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_clock, 0, 0, 0)
                val cor = ContextCompat.getColor(contexto, R.color.if_info_text)
                textoTurno.setTextColor(cor)
                TextViewCompat.setCompoundDrawableTintList(textoTurno, ColorStateList.valueOf(cor))
            } else {
                textoTurno.text = contexto.getString(R.string.tecnico_turno, contexto.getString(Datas.nomeTurno(item.tecnico.turno)).lowercase())
                textoTurno.setCompoundDrawablesRelative(null, null, null, null)
                textoTurno.setTextColor(ContextCompat.getColor(contexto, R.color.if_on_surface_variant))
            }
            val intervencao = item.aIntervir?.let { contexto.getString(R.string.tecnico_a_intervir, it.avaria.codigo, it.equipamentoNome) }
                ?: contexto.getString(R.string.oficina_tecnico_livre)
            val atribuidas = item.atribuidas.takeIf { it > 0 }?.let { contexto.resources.getQuantityString(R.plurals.tecnico_atribuidas, it, it) }
            textoEstado.text = listOfNotNull(intervencao, atribuidas).joinToString(EstiloAvaria.SEPARADOR)
        }
    }

    private object Diferencas : DiffUtil.ItemCallback<TecnicoEstado>() {
        override fun areItemsTheSame(antigo: TecnicoEstado, novo: TecnicoEstado) = antigo.tecnico.id == novo.tecnico.id
        override fun areContentsTheSame(antigo: TecnicoEstado, novo: TecnicoEstado) =
            antigo.emTurno == novo.emTurno && antigo.atribuidas == novo.atribuidas &&
                antigo.aIntervir?.avaria?.id == novo.aIntervir?.avaria?.id && antigo.tecnico == novo.tecnico
    }
}
