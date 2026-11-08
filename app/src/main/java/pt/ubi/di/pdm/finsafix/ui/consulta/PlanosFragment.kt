package pt.ubi.di.pdm.finsafix.ui.consulta

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import com.google.android.material.chip.Chip
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.ui.comum.Opcao
import pt.ubi.di.pdm.finsafix.ui.comum.escolherUma
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.PecaResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Preventiva
import pt.ubi.di.pdm.finsafix.data.PreventivaResumo
import pt.ubi.di.pdm.finsafix.databinding.FragmentListaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.pedirNumero
import pt.ubi.di.pdm.finsafix.ui.comum.pedirTexto
import pt.ubi.di.pdm.finsafix.ui.lista.Acao
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.util.Validacao
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

class PlanosViewModel(application: Application) : ViewModelBase(application) {
    val planos: LiveData<List<PreventivaResumo>> = repositorio.preventivas().paraLiveData(emptyList())
    val pecas: LiveData<List<PecaResumo>> = repositorio.pecas().paraLiveData(emptyList())
    val equipamentos: LiveData<List<Equipamento>> = repositorio.todosOsEquipamentos().paraLiveData(emptyList())

    fun criarPlano(equipamentoId: String, titulo: String, especialidade: String, intervaloH: Int, eng: Pessoa) {
        executar {
            repositorio.criarPreventiva(equipamentoId, titulo, especialidade, intervaloH, eng)
            avisar(R.string.planos_criado)
        }
    }

    fun ajustarMinimo(pecaId: String, variacao: Int, eng: Pessoa) {
        executar { repositorio.ajustarMinimo(pecaId, variacao, eng) }
    }
}

/** “Planos” da engenharia: planos preventivos (com “Novo plano”) e mínimos de stock (− / +), com filtro por oficina. */
class PlanosFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlanosViewModel by viewModels()
    private val adapter = LinhaAdapter()
    private var verStock = false
    private var especialidade: String? = null // null = todas as oficinas

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val eng = pessoaOuVoltarAEntrada() ?: return
        binding.textoResumo.isVisible = false
        binding.textoIntro.setText(R.string.planos_intro)
        // Separadores e filtros de oficina: chips de escolha única criados aqui
        binding.filtros.isSingleSelection = true
        for ((texto, aoMarcar) in listOf<Pair<Int, () -> Unit>>(
            R.string.planos_preventivos to { verStock = false },
            R.string.planos_stock to { verStock = true },
            R.string.filtro_todas_oficinas to { especialidade = null },
            R.string.especialidade_mecanica to { especialidade = Avaria.MECANICA },
            R.string.especialidade_eletrica to { especialidade = Avaria.ELETRICA }
        )) {
            binding.filtros.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                setText(texto)
                isCheckable = true
                setOnClickListener {
                    aoMarcar()
                    mostrar(eng)
                }
            })
        }
        binding.filtros.isSingleSelection = false // dois grupos de escolha: tratados à mão
        marcarChips()
        binding.botaoAcao.setText(R.string.planos_novo)
        binding.botaoAcao.setOnClickListener { novoPlano(eng) }
        binding.lista.adapter = adapter
        binding.lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        for (dados in listOf(viewModel.planos, viewModel.pecas, viewModel.equipamentos)) dados.observe(viewLifecycleOwner) { mostrar(eng) }
        mostrarErrosDe(viewModel)
    }

    private fun marcarChips() {
        val ativos = listOf(!verStock, verStock, especialidade == null, especialidade == Avaria.MECANICA, especialidade == Avaria.ELETRICA)
        for ((i, ativo) in ativos.withIndex()) (binding.filtros.getChildAt(i) as Chip).isChecked = ativo
    }

    private fun mostrar(eng: Pessoa) {
        marcarChips()
        binding.botaoAcao.isVisible = !verStock
        val agora = System.currentTimeMillis()
        val linhas = if (!verStock) {
            viewModel.planos.value.orEmpty().filter { especialidade == null || it.preventiva.especialidade == especialidade }.map { resumo ->
                val plano = resumo.preventiva
                Linha(
                    id = "p" + plano.id, titulo = plano.titulo,
                    sub = listOf(resumo.equipamentoNome, resumo.secaoNome).joinToString(EstiloAvaria.SEPARADOR),
                    extra = getString(R.string.planos_cada, plano.intervaloH, plano.horasDesdeUltima(agora)),
                    estado = when (plano.estado(agora)) {
                        Preventiva.Estado.VENCIDA -> EstiloAvaria.Estilo(R.string.preventiva_vencida, R.drawable.ic_alert, R.color.if_error_text)
                        Preventiva.Estado.A_VENCER -> EstiloAvaria.Estilo(R.string.preventiva_a_vencer, R.drawable.ic_clock, R.color.if_warn_text)
                        Preventiva.Estado.EM_DIA -> EstiloAvaria.Estilo(R.string.preventiva_em_dia, R.drawable.ic_check_circle, R.color.if_ok_text)
                    }
                )
            }
        } else {
            viewModel.pecas.value.orEmpty().filter { especialidade == null || it.peca.especialidade == especialidade }.map { resumo ->
                val peca = resumo.peca
                Linha(
                    id = "s" + peca.id, titulo = peca.nome,
                    sub = getString(R.string.planos_minimo, peca.minimo, peca.stock, resumo.secaoNome),
                    acoes = listOf(
                        Acao("−") { viewModel.ajustarMinimo(peca.id, -1, eng) },
                        Acao("+") { viewModel.ajustarMinimo(peca.id, 1, eng) }
                    )
                )
            }
        }
        adapter.submitList(linhas)
        binding.textoVazio.setText(R.string.planos_vazio)
        binding.textoVazio.isVisible = linhas.isEmpty()
    }

    /** Novo plano: equipamento, oficina, título e intervalo (em horas), um passo de cada vez. */
    private fun novoPlano(eng: Pessoa) {
        val equipamentos = viewModel.equipamentos.value.orEmpty()
        escolherUma(getString(R.string.paragens_escolher_equipamento), equipamentos.map { Opcao(it.id, it.nome) }, colunasTablet = 2) { equipamentoId ->
            val oficinas = listOf(Avaria.MECANICA, Avaria.ELETRICA)
            val opcoes = oficinas.map { Opcao(it, getString(EstiloAvaria.especialidade(it))) }
            escolherUma(getString(R.string.planos_oficina), opcoes) { oficina ->
                pedirTexto(
                    R.string.planos_titulo_plano, R.string.planos_titulo_dica, R.string.planos_seguinte,
                    validar = { if (Validacao.notaValida(it)) null else getString(R.string.erro_nota) }
                ) { titulo ->
                    pedirNumero(getString(R.string.planos_intervalo), getString(R.string.planos_horas), 0, info = { getString(R.string.planos_intervalo_dica) }) { horas ->
                        viewModel.criarPlano(equipamentoId, titulo, oficina, horas.toInt(), eng)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
