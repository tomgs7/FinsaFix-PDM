package pt.ubi.di.pdm.finsafix.ui.autorizacoes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.ui.comum.Opcao
import pt.ubi.di.pdm.finsafix.ui.comum.escolherEConfirmar
import pt.ubi.di.pdm.finsafix.data.Autorizacao
import pt.ubi.di.pdm.finsafix.data.AutorizacaoResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.RegistoResumo
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.lista.Acao
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.databinding.FragmentValidarBinding
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarMensagem
import pt.ubi.di.pdm.finsafix.ui.comum.pedirPin
import pt.ubi.di.pdm.finsafix.ui.comum.redesenharACada30s
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** Chefe de secção: autorizações de trabalho da secção. Tocar numa abre a decisão que o estado permite. */
class ValidarFragment : Fragment() {

    private var _binding: FragmentValidarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ValidarViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentValidarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val chefe = pessoaOuVoltarAEntrada() ?: return
        viewModel.definirSecao(chefe.postoId ?: return)
        val oficina = chefe.eChefeDeOficina()
        // O chefe de oficina não decide autorizações: valida rondas e confirma passagens da sua oficina
        binding.blocoAutorizacoes.isVisible = !oficina
        binding.tituloChecklists.isVisible = !oficina
        binding.tituloRenovar.isVisible = !oficina
        binding.cartaoRenovar.isVisible = !oficina
        binding.cartaoChecklists.isVisible = !oficina
        binding.tituloRondas.isVisible = oficina
        binding.cartaoRondas.isVisible = oficina
        if (oficina) viewModel.definirEspecialidade(chefe.especialidade ?: "")
        ligarRegistos(chefe)
        if (!oficina) ligarRenovar(chefe)

        val adapter = AutorizacaoAdapter { decidir(it, chefe) }
        binding.listaAutorizacoes.adapter = adapter
        binding.listaAutorizacoes.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL)
        )
        viewModel.autorizacoes.observe(viewLifecycleOwner) { lista ->
            adapter.submitList(lista)
            binding.textoSemAutorizacoes.isVisible = lista.isEmpty()
        }
        redesenharACada30s(adapter) // o fim da vigilância de fogo depende só da hora
        viewModel.mensagem.observe(viewLifecycleOwner) { mensagem ->
            if (mensagem != null) {
                mostrarMensagem(mensagem)
                viewModel.mensagemMostrada()
            }
        }
        mostrarErrosDe(viewModel)
    }

    /** Checklists com Não OK (“Visto”), rondas não conformes (“Validar”) e passagens de turno (“Confirmar”). */
    private fun ligarRegistos(chefe: Pessoa) {
        fun lista(recycler: androidx.recyclerview.widget.RecyclerView, vazio: View, dados: androidx.lifecycle.LiveData<List<RegistoResumo>>, rotulo: Int, texto: (RegistoResumo) -> String, acao: (Long) -> Unit) {
            val adapter = LinhaAdapter()
            recycler.adapter = adapter
            recycler.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
            dados.observe(viewLifecycleOwner) { registos ->
                adapter.submitList(registos.map {
                    Linha(
                        id = it.registo.id.toString(), titulo = texto(it),
                        sub = listOf(it.pessoaNome, Datas.dataHora(it.registo.dataCriacao)).joinToString(EstiloAvaria.SEPARADOR),
                        acoes = listOf(Acao(getString(rotulo)) { acao(it.registo.id) })
                    )
                })
                vazio.isVisible = registos.isEmpty()
            }
        }
        lista(binding.listaChecklists, binding.textoSemChecklists, viewModel.checklists, R.string.checklist_visto,
            { it.registo.extra.orEmpty().split(",").count { r -> r == "0" }.let { n -> resources.getQuantityString(R.plurals.checklist_com_nao_ok, n, n) } }) { viewModel.validarChecklist(it, chefe) }
        lista(binding.listaRondas, binding.textoSemRondas, viewModel.rondas, R.string.ronda_validar,
            { getString(R.string.ronda_titulo_anomalia, it.secaoNome, it.registo.extra.orEmpty()) }) { viewModel.validarRonda(it, chefe) }
        lista(binding.listaPassagens, binding.textoSemPassagens, viewModel.passagens, R.string.passagem_confirmar,
            { getString(R.string.rececao_titulo, getString(Datas.nomeTurno(it.registo.extra ?: "M")).lowercase(), it.pessoaNome) }) { viewModel.confirmarPassagem(it, chefe) }
    }

    /** Passagem de turno das autorizações: as que passaram do fim do turno em que foram aprovadas, com “Renovar”. */
    private fun ligarRenovar(chefe: Pessoa) {
        val adapter = LinhaAdapter()
        binding.listaRenovar.adapter = adapter
        binding.listaRenovar.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        viewModel.autorizacoes.observe(viewLifecycleOwner) { lista ->
            val expiradas = lista.filter { it.autorizacao.expirada(System.currentTimeMillis()) }
            adapter.submitList(expiradas.map {
                Linha(
                    id = it.autorizacao.id.toString(), titulo = EstiloAutorizacao.nomes(requireContext(), it.autorizacao.tipo),
                    sub = listOf(it.equipamentoNome, getString(R.string.aut_expirada_as, Datas.hora(it.autorizacao.validaAte ?: 0))).joinToString(EstiloAvaria.SEPARADOR),
                    acoes = listOf(Acao(getString(R.string.aut_renovar)) { viewModel.renovar(it.autorizacao.id, chefe) })
                )
            })
            binding.textoSemRenovar.isVisible = expiradas.isEmpty()
        }
    }

    private fun decidir(resumo: AutorizacaoResumo, chefe: Pessoa) {
        val aut = resumo.autorizacao
        when {
            aut.estado == Autorizacao.PEDIDA -> aprovarOuRecusar(resumo, chefe)
            aut.estado == Autorizacao.EM_CURSO && aut.dataTrabalhoTerminado != null ->
                if (aut.vigilanciaRestanteMs(System.currentTimeMillis()) > 0) mostrarMensagem(R.string.erro_aut_vigilancia)
                else encerrar(aut, chefe)
            else -> mostrarMensagem(R.string.aut_aguarda_tecnico) // aprovada ou em curso: o próximo passo é do técnico
        }
    }

    private fun aprovarOuRecusar(resumo: AutorizacaoResumo, chefe: Pessoa) {
        val aut = resumo.autorizacao
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(EstiloAutorizacao.nomes(requireContext(), aut.tipo))
            .setMessage(
                getString(R.string.aut_pedido_detalhe, aut.descricao, resumo.equipamentoNome, resumo.pedidaPorNome, Datas.hora(aut.dataPedido))
            )
            .setPositiveButton(R.string.aut_aprovar) { _, _ -> aprovar(aut, chefe) }
            .setNegativeButton(R.string.aut_recusar) { _, _ -> recusar(aut, chefe) }
            .setNeutralButton(R.string.botao_cancelar, null)
            .show()
    }

    /** Sim/Não em cada ponto, aviso se a máquina não está confirmada como parada e, a quente, o vigilante de fogo. */
    private fun aprovar(aut: Autorizacao, chefe: Pessoa) {
        viewLifecycleOwner.lifecycleScope.launch {
            val aviso = if (viewModel.maquinaParada(aut.avariaId)) null else getString(R.string.aut_aviso_maquina)
            pedirPin(
                R.string.aut_aprovar_titulo, itens = EstiloAutorizacao.checklistDe(resources, aut.tipo), aviso = aviso,
                dicaExtra = if (aut.temQuente()) R.string.aut_vigilante_dica else null
            ) { pin, completa, vigilante -> viewModel.aprovar(aut, chefe, pin, completa, vigilante) }
        }
    }

    /** Escolher o motivo já é a confirmação: a recusa não pede PIN (não põe ninguém em risco). */
    private fun recusar(aut: Autorizacao, chefe: Pessoa) {
        val motivos = resources.getStringArray(R.array.aut_motivos_recusa)
        escolherEConfirmar(
            titulo = getString(R.string.aut_recusar_titulo),
            opcoes = motivos.map { Opcao(it, it) },
            textoConfirmar = getString(R.string.aut_recusar)
        ) { motivo -> viewModel.recusar(aut, chefe, motivo) }
    }

    private fun encerrar(aut: Autorizacao, chefe: Pessoa) {
        pedirPin(R.string.aut_encerrar_titulo, itens = EstiloAutorizacao.fechoDe(resources, aut.tipo)) { pin, completa, _ ->
            viewModel.encerrar(aut, chefe, pin, completa)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
