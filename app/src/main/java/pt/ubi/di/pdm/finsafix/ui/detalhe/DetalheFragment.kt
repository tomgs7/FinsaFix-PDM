package pt.ubi.di.pdm.finsafix.ui.detalhe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.ui.comum.PARA_TODOS
import pt.ubi.di.pdm.finsafix.ui.comum.Opcao
import pt.ubi.di.pdm.finsafix.ui.comum.Grupo
import pt.ubi.di.pdm.finsafix.ui.comum.EscolhaConfig
import pt.ubi.di.pdm.finsafix.ui.comum.escolher
import pt.ubi.di.pdm.finsafix.ui.comum.escolherEConfirmar
import pt.ubi.di.pdm.finsafix.ui.comum.escolherUma
import pt.ubi.di.pdm.finsafix.data.Autorizacao
import pt.ubi.di.pdm.finsafix.ui.autorizacoes.EstiloAutorizacao
import pt.ubi.di.pdm.finsafix.ui.comum.pedirTexto
import pt.ubi.di.pdm.finsafix.util.Numeros
import pt.ubi.di.pdm.finsafix.util.Validacao
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.Aviso
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import com.google.android.material.checkbox.MaterialCheckBox
import pt.ubi.di.pdm.finsafix.databinding.DialogAutorizacaoBinding
import pt.ubi.di.pdm.finsafix.databinding.FragmentDetalheBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemCampoBinding
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarMembros
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.confirmar
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.pedirNumero
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarMensagem
import pt.ubi.di.pdm.finsafix.ui.formulario.FormularioDialogo
import pt.ubi.di.pdm.finsafix.ui.producao.ProducaoFragment
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.ProducaoPerdida
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** Detalhe de uma avaria. Recebe o argumento "avariaId". */
class DetalheFragment : Fragment() {

    private var _binding: FragmentDetalheBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetalheViewModel by viewModels()
    private val eliminacao: EliminarAvariaViewModel by activityViewModels()
    private val eventoAdapter = EventoAdapter()
    private var autorizacaoAtual: Autorizacao? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetalheBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return
        viewModel.carregar(requireArguments().getLong(ARG_AVARIA_ID))

        binding.cronologia.listaEventos.adapter = eventoAdapter
        binding.cronologia.listaEventos.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL)
        )

        viewModel.avaria.observe(viewLifecycleOwner) { resumo ->
            // null: a avaria deixou de existir (ex.: foi eliminada noutro ecrã).
            if (resumo == null) return@observe
            mostrarAvaria(resumo, pessoa)
            mostrarEquipa(resumo, pessoa)
        }
        viewModel.equipa.observe(viewLifecycleOwner) { viewModel.avaria.value?.let { resumo -> mostrarEquipa(resumo, pessoa) } }
        viewModel.cronologia.observe(viewLifecycleOwner) { eventoAdapter.submitList(it) }
        viewModel.autorizacao.observe(viewLifecycleOwner) { mostrarAutorizacao(it?.autorizacao) }
        // Peças substituídas: “2 unid. · 2 ref.” e a lista; o botão é do técnico com a intervenção em curso
        viewModel.pecas.observe(viewLifecycleOwner) { }
        viewModel.tecnicos.observe(viewLifecycleOwner) { }
        viewModel.pecasUsadas.observe(viewLifecycleOwner) { usadas ->
            binding.cabecalho.textoPecas.text = if (usadas.isEmpty()) getString(R.string.pecas_nenhuma) else
                getString(R.string.pecas_resumo, usadas.sumOf { it.usada.quantidade }, usadas.map { it.usada.pecaId }.distinct().size) + "\n" +
                    usadas.joinToString("\n") { getString(R.string.pecas_linha, it.usada.quantidade, it.pecaNome) }
        }
        // “Máquina parada há 00:14 · ≈ 2,4 unid. perdidas”; também fica pronta para a pergunta ao concluir
        viewModel.paragemAtiva.observe(viewLifecycleOwner) { mostrarParadaHa() }
        viewModel.secao.observe(viewLifecycleOwner) { mostrarParadaHa() }
        viewModel.mensagem.observe(viewLifecycleOwner) { mensagem ->
            if (mensagem != null) {
                mostrarMensagem(mensagem)
                viewModel.mensagemMostrada()
            }
        }
        ligarAcoesDoTecnico(pessoa)
        eliminacao.eliminada.observe(viewLifecycleOwner) { motivo ->
            if (motivo != null) {
                eliminacao.eliminacaoMostrada()
                mostrarDesfazer(motivo)
                findNavController().popBackStack()
            }
        }
        mostrarErrosDe(viewModel)
    }

    /**
     * “Equipa”: só com a avaria em curso. O responsável vem marcado; um técnico livre (da oficina da avaria ou, se for
     * “Não sei”, de qualquer uma) pode juntar-se, e um ajudante pode sair. O Repositorio é que decide; aqui só se esconde o que não faz sentido.
     */
    private fun mostrarEquipa(resumo: AvariaResumo, pessoa: Pessoa) {
        val avaria = resumo.avaria
        val equipa = viewModel.equipa.value.orEmpty()
        val parte = binding.equipa
        parte.root.isVisible = avaria.estado == Avaria.EM_CURSO && equipa.isNotEmpty()
        if (!parte.root.isVisible) return
        parte.textoTituloEquipa.text = resources.getQuantityString(R.plurals.equipa_titulo, equipa.size, equipa.size)
        mostrarMembros(layoutInflater, parte.listaEquipa, equipa, avaria.tecnicoId)
        val tecnico = pessoa.papel == Pessoa.PAPEL_TECNICO
        val naEquipa = equipa.any { it.participacao.tecnicoId == pessoa.id }
        val daOficina = pessoa.especialidade == avaria.especialidade || avaria.especialidade == Avaria.NAO_SEI
        parte.botaoJuntar.isVisible = tecnico && !naEquipa && daOficina
        parte.botaoSair.isVisible = naEquipa && avaria.tecnicoId != pessoa.id
        parte.botaoJuntar.setOnClickListener { confirmarLotoEJuntar(pessoa) }
        parte.botaoSair.setOnClickListener { viewModel.sair(pessoa) }
    }

    /** Antes de se juntar, o ajudante confirma os 3 pontos do LOTO (o botão só se ativa com os 3 marcados). */
    private fun confirmarLotoEJuntar(tecnico: Pessoa) {
        val pontos = listOf(R.string.loto_ponto_1, R.string.loto_ponto_2, R.string.loto_ponto_3)
        escolher(
            EscolhaConfig(
                titulo = getString(R.string.equipa_juntar_titulo),
                grupos = listOf(Grupo(null, pontos.mapIndexed { indice, texto -> Opcao(indice.toString(), getString(texto)) })),
                aviso = getString(R.string.equipa_juntar_aviso),
                multipla = true,
                textoConfirmar = getString(R.string.equipa_juntar),
                confirmaSo = { marcados -> marcados.size == pontos.size },
                aoConfirmar = { viewModel.juntar(tecnico) }
            )
        )
    }

    /** Liga as caixas LOTO e os botões do técnico ao ViewModel. */
    private fun ligarAcoesDoTecnico(tecnico: Pessoa) {
        val caixas = with(binding.tecnico) { listOf(checkLoto1, checkLoto2, checkLoto3) }
        caixas.forEachIndexed { indice, caixa ->
            caixa.isChecked = viewModel.pontosLoto[indice]
            caixa.setOnCheckedChangeListener { _, marcada ->
                // Só conta o que o técnico marca (com a avaria em curso as caixas estão desativadas).
                if (!caixa.isEnabled) return@setOnCheckedChangeListener
                viewModel.pontosLoto[indice] = marcada
                binding.tecnico.botaoAssumir.isEnabled = viewModel.lotoCompleto()
                mostrarEstadoDoLoto(true)
            }
        }
        binding.tecnico.botaoAssumir.setOnClickListener { assumir(tecnico) }
        binding.tecnico.botaoAvisoParar.setOnClickListener { confirmarAviso(Aviso.PARAR, R.string.aviso_parar, tecnico) }
        binding.tecnico.botaoAvisoCaminho.setOnClickListener { confirmarAviso(Aviso.A_CAMINHO, R.string.aviso_a_caminho, tecnico) }
        binding.tecnico.botaoAvisoPeca.setOnClickListener { confirmarAviso(Aviso.ESPERA_PECA, R.string.aviso_espera_peca, tecnico) }
        binding.tecnico.botaoAvisoDemora.setOnClickListener { confirmarAviso(Aviso.DEMORA, R.string.aviso_demora, tecnico) }
        binding.tecnico.botaoConcluir.setOnClickListener { confirmarConclusao(tecnico) }
        binding.tecnico.botaoAutorizacao.setOnClickListener { acaoDaAutorizacao(tecnico) }
        binding.tecnico.botaoDeixarProgresso.setOnClickListener {
            findNavController().navigate(
                R.id.acao_detalhe_para_progresso,
                Bundle().apply { putLong(ARG_AVARIA_ID, requireArguments().getLong(ARG_AVARIA_ID)) }
            )
        }
    }

    /** Parte do técnico: LOTO + Assumir com a avaria pendente; Concluir / Deixar em progresso com ela em curso. */
    private fun mostrarParteDoTecnico(avaria: Avaria, pessoa: Pessoa) {
        val pendente = avaria.estado == Avaria.PENDENTE
        val minhaEmCurso = avaria.estado == Avaria.EM_CURSO && avaria.tecnicoId == pessoa.id
        with(binding.tecnico) {
            root.isVisible = pessoa.papel == Pessoa.PAPEL_TECNICO && (pendente || minhaEmCurso)
            botaoAssumir.isVisible = pendente
            botaoAssumir.isEnabled = viewModel.lotoCompleto()
            acoesEmCurso.isVisible = minhaEmCurso
            blocoAvisos.isVisible = minhaEmCurso
            blocoAutorizacao.isVisible = minhaEmCurso
            mostrarEstadoDoLoto(pendente)
            val oficina = getString(EstiloAvaria.especialidade(avaria.especialidade))
            textoBlocoEstado.text = if (pendente) getString(R.string.bloco_pendente, oficina)
            else getString(R.string.bloco_em_curso, viewModel.avaria.value?.tecnicoNome ?: "")
            // Em curso, o LOTO já foi verificado ao assumir: fica marcado e só de leitura.
            listOf(checkLoto1, checkLoto2, checkLoto3).forEachIndexed { indice, caixa ->
                caixa.isEnabled = pendente
                caixa.isChecked = minhaEmCurso || viewModel.pontosLoto[indice]
            }
        }
    }

    /** “Por validar · 1 de 3” e a dica do botão Assumir enquanto faltam pontos; depois de assumir, “Verificada”. */
    private fun mostrarEstadoDoLoto(pendente: Boolean) {
        with(binding.tecnico) {
            val validados = viewModel.pontosLoto.count { it }
            textoLoto.text = if (pendente) getString(R.string.loto_por_validar, validados) else getString(R.string.loto_feito)
            textoDicaAssumir.isVisible = pendente && !viewModel.lotoCompleto()
        }
    }

    /** A autorização mais recente da avaria e o botão do passo seguinte (pedir, começar ou terminar). */
    private fun mostrarAutorizacao(aut: Autorizacao?) {
        autorizacaoAtual = aut
        with(binding.tecnico) {
            textoAutorizacaoEstado.isVisible = aut != null
            if (aut != null) EstiloAutorizacao.mostrarEstado(textoAutorizacaoEstado, aut)
            textoAutorizacaoDetalhe.text = if (aut == null) getString(R.string.aut_sem_pedido) else listOfNotNull(
                EstiloAutorizacao.nomes(requireContext(), aut.tipo),
                aut.descricao,
                getString(R.string.aut_duracao_n, aut.duracaoMin),
                aut.vigilante?.let { getString(R.string.aut_vigilante_n, it) },
                aut.validaAte?.let { getString(if (aut.expirada(System.currentTimeMillis())) R.string.aut_expirada_as else R.string.aut_valida_ate, Datas.hora(it)) },
                aut.cadeado?.let { getString(R.string.aut_cadeado_n, it) },
                aut.motivoRecusa?.let { getString(R.string.aut_motivo, it) }
            ).joinToString(EstiloAvaria.SEPARADOR)
            val acao = when {
                podePedirOutra(aut) -> R.string.aut_pedir
                aut?.estado == Autorizacao.APROVADA -> R.string.aut_comecar
                aut?.estado == Autorizacao.EM_CURSO && aut.dataTrabalhoTerminado == null -> R.string.aut_terminar
                else -> null // pedida (espera o chefe) ou terminada (o chefe encerra)
            }
            botaoAutorizacao.isVisible = acao != null
            acao?.let { botaoAutorizacao.setText(it) }
        }
    }

    /** Os 5 passos da consignação ou do bloqueio: só segue (para o número do cadeado) com todos confirmados. */
    private fun confirmarPassos(aut: Autorizacao, aoConfirmar: () -> Unit) {
        val passos = resources.getStringArray(R.array.aut_passos_consignacao)
        escolher(
            EscolhaConfig(
                titulo = getString(R.string.aut_passos_titulo, EstiloAutorizacao.nomes(requireContext(), aut.tipo)),
                grupos = listOf(Grupo(null, passos.mapIndexed { indice, passo -> Opcao(indice.toString(), passo) })),
                multipla = true,
                textoConfirmar = getString(R.string.botao_seguinte),
                // O botão só se ativa com os 5 passos confirmados
                confirmaSo = { marcados -> marcados.size == passos.size },
                aoConfirmar = { aoConfirmar() }
            )
        )
    }

    // Recusada ou encerrada já não conta: o técnico pode pedir outra.
    private fun podePedirOutra(aut: Autorizacao?) =
        aut == null || aut.estado == Autorizacao.RECUSADA || aut.estado == Autorizacao.ENCERRADA

    private fun acaoDaAutorizacao(tecnico: Pessoa) {
        val aut = autorizacaoAtual
        when {
            aut == null || podePedirOutra(aut) -> pedirAutorizacao(tecnico)
            aut.estado == Autorizacao.APROVADA && aut.exigeCadeado() -> confirmarPassos(aut) {
                pedirTexto(
                    R.string.aut_cadeado_titulo, R.string.aut_cadeado_dica, R.string.aut_comecar,
                    validar = { if (it.isBlank()) getString(R.string.erro_aut_cadeado) else null }
                ) { cadeado -> viewModel.iniciarTrabalho(aut.id, cadeado, tecnico) }
            }
            aut.estado == Autorizacao.APROVADA ->
                confirmar(R.string.aut_comecar_titulo, getString(R.string.aut_comecar_mensagem), R.string.aut_comecar) {
                    viewModel.iniciarTrabalho(aut.id, null, tecnico)
                }
            else -> confirmar(R.string.aut_terminar_titulo, getString(R.string.aut_terminar_mensagem), R.string.aut_terminar) {
                viewModel.terminarTrabalho(aut.id, tecnico)
            }
        }
    }

    /** Primeiro o tipo de trabalho, depois a descrição. */
    /** Janela com os 5 tipos em cartões (nome e legenda), o equipamento, o que vai fazer e a duração. */
    private fun pedirAutorizacao(tecnico: Pessoa) {
        val vista = DialogAutorizacaoBinding.inflate(layoutInflater)
        vista.textoEquipamento.text = viewModel.avaria.value?.equipamentoNome ?: ""
        // Pode pedir mais do que um tipo de uma vez; o habitual de cada oficina vem marcado (a quente na Mecânica, consignação na Elétrica)
        val habitual = if (tecnico.especialidade == Avaria.ELETRICA) Autorizacao.CONSIGNACAO else Autorizacao.QUENTE
        val caixas = Autorizacao.TIPOS.associateWith { tipo ->
            MaterialCheckBox(requireContext()).apply {
                text = getString(R.string.aut_cartao, getString(EstiloAutorizacao.nome(tipo)), getString(EstiloAutorizacao.legenda(tipo)))
                minHeight = resources.getDimensionPixelSize(R.dimen.if_touch_min)
                isChecked = tipo == habitual
                vista.grupoTipos.addView(this)
            }
        }
        val alerta = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.aut_escolher_tipo)
            .setView(vista.root)
            .setPositiveButton(R.string.aut_pedir, null)
            .setNegativeButton(R.string.botao_cancelar, null)
            .create()
        alerta.setOnShowListener {
            alerta.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val descricao = vista.campoDescricao.editText?.text.toString().trim()
                if (!Validacao.descricaoAutorizacaoValida(descricao)) {
                    vista.campoDescricao.error = getString(R.string.erro_aut_descricao)
                    return@setOnClickListener
                }
                val duracao = when (vista.grupoDuracao.checkedChipId) {
                    R.id.chip30 -> 30
                    R.id.chip120 -> 120
                    R.id.chipTurno -> 480
                    else -> 60
                }
                val escolhidos = caixas.filter { it.value.isChecked }.keys
                if (escolhidos.isEmpty()) {
                    vista.campoDescricao.error = getString(R.string.erro_aut_tipo)
                    return@setOnClickListener
                }
                alerta.dismiss()
                viewModel.pedirAutorizacao(escolhidos.joinToString(","), descricao, tecnico, duracao)
            }
        }
        alerta.show()
    }

    /** O aviso aparece no ecrã da secção até alguém responder: pede confirmação antes de enviar. */
    private fun confirmarAviso(tipo: String, @StringRes nome: Int, tecnico: Pessoa) {
        val secao = viewModel.avaria.value?.secaoNome ?: return
        confirmar(R.string.aviso_confirmar_titulo, getString(R.string.aviso_confirmar_mensagem, secao, getString(nome)), R.string.aviso_enviar) {
            viewModel.avisarSecao(tipo, tecnico)
        }
    }

    /** Uma avaria "Não sei" é classificada como Mecânica ou Elétrica por quem a assume. */
    private fun assumir(tecnico: Pessoa) {
        val avaria = viewModel.avaria.value?.avaria ?: return
        if (avaria.especialidade != Avaria.NAO_SEI) {
            viewModel.assumir(tecnico, null)
            return
        }
        val opcoes = listOf(
            Opcao(Avaria.MECANICA, getString(R.string.especialidade_mecanica), getString(R.string.escolha_oficina_mecanica), R.drawable.ic_wrench),
            Opcao(Avaria.ELETRICA, getString(R.string.especialidade_eletrica), getString(R.string.escolha_oficina_eletrica), R.drawable.ic_bolt)
        )
        escolherEConfirmar(
            titulo = getString(R.string.classificar_titulo),
            opcoes = opcoes,
            textoConfirmar = getString(R.string.botao_assumir),
            etiqueta = avaria.codigo,
            aviso = getString(R.string.classificar_aviso),
            colunasTablet = 2
        ) { viewModel.assumir(tecnico, it) }
    }

    /** Se a avaria parou a máquina, pergunta também se a produção retoma. */
    private fun confirmarConclusao(tecnico: Pessoa) {
        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.concluir_titulo)
            .setNegativeButton(R.string.botao_cancelar, null)
        if (viewModel.paragemAtiva.value != null) {
            dialogo.setMessage(R.string.concluir_mensagem_paragem)
                .setPositiveButton(R.string.concluir_e_retomar) { _, _ -> viewModel.concluir(tecnico, retomarProducao = true) }
                .setNeutralButton(R.string.so_concluir) { _, _ -> viewModel.concluir(tecnico, retomarProducao = false) }
        } else {
            dialogo.setMessage(R.string.concluir_mensagem)
                .setPositiveButton(R.string.concluir_avaria) { _, _ -> viewModel.concluir(tecnico, retomarProducao = false) }
        }
        dialogo.show()
    }

    private fun mostrarAvaria(resumo: AvariaResumo, pessoa: Pessoa) {
        mostrarParteDoTecnico(resumo.avaria, pessoa)
        mostrarCabecalho(resumo, pessoa)
        ligarDecisaoDeEngenharia(resumo.avaria, pessoa)
        mostrarNotaDeProgresso(resumo)
        mostrarDados(resumo)
    }

    /** Prioridade, estado, título e local; quem reportou vê Editar e Eliminar; o técnico da intervenção vê “Registar peça”. */
    private fun mostrarCabecalho(resumo: AvariaResumo, pessoa: Pessoa) {
        val avaria = resumo.avaria
        with(binding.cabecalho) {
            EstiloAvaria.aplicarPrioridade(textoPrioridade, avaria.prioridade)
            EstiloAvaria.aplicar(textoEstado, EstiloAvaria.estado(avaria.estado))
            textoCodigo.text = avaria.codigo
            textoTitulo.text = avaria.titulo
            textoLocal.text = getString(R.string.detalhe_local, resumo.equipamentoNome, resumo.secaoNome)

            val podeAlterar = avaria.podeSerAlteradaPor(pessoa.id)
            botaoEditar.isVisible = podeAlterar
            botaoEliminar.isVisible = podeAlterar
            textoSoQuemReportou.isVisible = !podeAlterar && avaria.estado == Avaria.PENDENTE &&
                pessoa.papel == Pessoa.PAPEL_OPERADOR
            textoLotoLeitura.isVisible = avaria.estado == Avaria.EM_CURSO && pessoa.papel == Pessoa.PAPEL_OPERADOR
            botaoEditar.setOnClickListener { editar(avaria) }
            botaoEliminar.setOnClickListener { confirmarEliminacao(avaria) }
            botaoRegistarPeca.isVisible = pessoa.papel == Pessoa.PAPEL_TECNICO && avaria.estado == Avaria.EM_CURSO && avaria.tecnicoId == pessoa.id
            botaoRegistarPeca.setOnClickListener { escolherPeca(pessoa) }
        }
    }

    /** Decisão de engenharia: por aprovar → aprovar ou devolver; pendente → reclassificar, mudar de oficina ou atribuir. */
    private fun ligarDecisaoDeEngenharia(avaria: Avaria, pessoa: Pessoa) {
        with(binding.cabecalho) {
            val engenharia = pessoa.papel == Pessoa.PAPEL_ENGENHEIRO
            blocoEngenharia.isVisible = engenharia && (avaria.porAprovar() || avaria.estado == Avaria.PENDENTE)
            botaoAprovarFecho.isVisible = avaria.porAprovar()
            botaoDevolver.isVisible = avaria.porAprovar()
            botaoReclassificar.isVisible = avaria.estado == Avaria.PENDENTE
            botaoOficina.isVisible = avaria.estado == Avaria.PENDENTE
            botaoAtribuir.isVisible = avaria.estado == Avaria.PENDENTE && avaria.especialidade != Avaria.NAO_SEI
            botaoOficina.setOnClickListener { reclassificarOficina(pessoa) }
            botaoAtribuir.setOnClickListener { atribuir(pessoa, avaria.especialidade) }
            botaoAprovarFecho.setOnClickListener {
                confirmar(R.string.decisao_aprovar_fecho, getString(R.string.decisao_aprovar_mensagem), R.string.decisao_aprovar_fecho) { viewModel.aprovarFecho(pessoa) }
            }
            botaoDevolver.setOnClickListener {
                pedirTexto(
                    R.string.decisao_devolver, R.string.decisao_motivo_dica, R.string.decisao_devolver,
                    validar = { if (it.length >= 5) null else getString(R.string.erro_nota) }
                ) { motivo -> viewModel.devolver(motivo, pessoa) }
            }
            botaoReclassificar.setOnClickListener { reclassificar(pessoa) }
        }
    }

    /** Nota de quem deixou o trabalho em progresso (fica visível também depois de retomar). */
    private fun mostrarNotaDeProgresso(resumo: AvariaResumo) {
        val avaria = resumo.avaria
        with(binding.cabecalho) {
            cartaoProgresso.isVisible = avaria.progressoNota != null
            if (avaria.progressoNota != null) {
                textoRetoma.setText(
                    if (avaria.progressoRetoma == Avaria.RETOMA_DIA) R.string.em_progresso_retoma_dia
                    else R.string.em_progresso_retoma_turno
                )
                textoOrdemMotivo.text = getString(
                    R.string.detalhe_ordem_motivo, avaria.progressoOrdem ?: "", avaria.progressoMotivo ?: ""
                )
                textoNota.text = getString(
                    R.string.avaria_nota_progresso, avaria.progressoNota, resumo.progressoPorNome ?: ""
                )
            }
        }
    }

    /** A coluna “Detalhes”: descrição, equipamento, secção, especialidade, quem reportou, datas e técnico. */
    private fun mostrarDados(resumo: AvariaResumo) {
        val avaria = resumo.avaria
        with(binding.dados) {
            textoDescricao.text = avaria.descricao.ifBlank { getString(R.string.detalhe_sem_descricao) }
            preencher(campoEquipamento, R.string.campo_equipamento, resumo.equipamentoNome)
            preencher(campoSecao, R.string.campo_secao, resumo.secaoNome)
            preencher(campoEspecialidade, R.string.campo_especialidade, getString(EstiloAvaria.especialidade(avaria.especialidade)))
            preencher(campoReportadaPor, R.string.campo_reportada_por, resumo.reportadaPorNome ?: getString(R.string.valor_vazio))
            preencher(campoAbertura, R.string.campo_abertura, Datas.dataHora(avaria.dataCriacao))
            preencher(campoTecnico, R.string.campo_tecnico, resumo.tecnicoNome?.let { EstiloAvaria.tecnicoComEquipa(requireContext(), it, resumo.ajudantes) } ?: getString(R.string.valor_por_atribuir))
            preencher(campoInicio, R.string.campo_inicio, avaria.dataInicio?.let { Datas.dataHora(it) } ?: getString(R.string.valor_vazio))
            preencher(
                campoDuracao, R.string.campo_duracao,
                avaria.duracaoMs(System.currentTimeMillis())?.let { Datas.duracao(requireContext(), it) } ?: getString(R.string.valor_vazio)
            )
        }
    }

    private fun preencher(campo: ItemCampoBinding, @StringRes rotulo: Int, valor: String) {
        campo.textoRotulo.setText(rotulo)
        campo.textoValor.text = valor
    }

    /** “Máquina parada há 14 min · ≈ 2,4 tab. perdidos”: precisa da paragem e da secção (para a unidade). */
    private fun mostrarParadaHa() {
        val paragem = viewModel.paragemAtiva.value
        binding.cabecalho.textoParadaHa.isVisible = paragem != null
        if (paragem == null) return
        val secao = viewModel.secao.value
        val perdida = ProducaoPerdida.estimar(secao?.id ?: "", paragem.inicio, System.currentTimeMillis())
        binding.cabecalho.textoParadaHa.text = getString(
            R.string.detalhe_parada_ha, Datas.duracao(requireContext(), System.currentTimeMillis() - paragem.inicio),
            Numeros.formatar(perdida, 1), getString(ProducaoFragment.nomeDaUnidade(secao?.unidade ?: ""))
        )
    }

    /** Escolhe a peça (da sua especialidade, com stock) e depois a quantidade no teclado grande. */
    private fun escolherPeca(tecnico: Pessoa) {
        val pecas = viewModel.pecas.value.orEmpty().filter { it.peca.especialidade == tecnico.especialidade && it.peca.stock > 0 }
        val opcoes = pecas.map { Opcao(it.peca.id.toString(), it.peca.nome, getString(R.string.pecas_em_stock, it.peca.stock)) }
        escolherUma(getString(R.string.pecas_registar), opcoes, colunasTablet = 2) { id ->
            val peca = pecas.first { it.peca.id.toString() == id }.peca
            pedirNumero(getString(R.string.pecas_quantidade), getString(R.string.pecas_unidade), 0, info = { getString(R.string.pecas_em_stock, peca.stock) }) {
                viewModel.registarPeca(peca.id, it.toInt(), tecnico)
            }
        }
    }

    private fun reclassificarOficina(eng: Pessoa) {
        val oficinas = listOf(Avaria.MECANICA, Avaria.ELETRICA, Avaria.NAO_SEI)
        val atual = viewModel.avaria.value?.avaria
        val opcoes = oficinas.map { Opcao(it, getString(EstiloAvaria.especialidade(it))) }
        escolherEConfirmar(getString(R.string.decisao_oficina), opcoes, getString(R.string.botao_guardar), atual?.especialidade, atual?.codigo) {
            viewModel.reclassificarOficina(it, eng)
        }
    }

    /** Atribui a um técnico da oficina da avaria (ou tira a atribuição). */
    private fun atribuir(eng: Pessoa, especialidade: String) {
        val tecnicos = viewModel.tecnicos.value.orEmpty().filter { it.especialidade == especialidade }
        val opcoes = tecnicos.map { Opcao(it.id, it.nome, getString(Datas.nomeTurno(it.turno))) } + Opcao(PARA_TODOS, getString(R.string.distribuir_para_todos))
        escolherEConfirmar(getString(R.string.distribuir_escolher), opcoes, getString(R.string.distribuir_atribuir), etiqueta = viewModel.avaria.value?.avaria?.codigo) {
            viewModel.atribuir(it.takeIf { id -> id != PARA_TODOS }, eng)
        }
    }

    private fun reclassificar(eng: Pessoa) {
        val prioridades = listOf(Avaria.BAIXA, Avaria.MEDIA, Avaria.CRITICA)
        val subtitulos = listOf(R.string.prioridade_sub_baixa, R.string.prioridade_sub_media, R.string.prioridade_sub_critica)
        val opcoes = prioridades.mapIndexed { i, p -> Opcao(p, getString(EstiloAvaria.prioridade(p)), getString(subtitulos[i])) }
        val atual = viewModel.avaria.value?.avaria
        escolherEConfirmar(getString(R.string.decisao_reclassificar), opcoes, getString(R.string.botao_guardar), atual?.prioridade, atual?.codigo) {
            viewModel.reclassificar(it, eng)
        }
    }

    private fun editar(avaria: Avaria) {
        FormularioDialogo.novo(avaria.id).show(childFragmentManager, "formulario")
    }

    /** Confirmação antes de eliminar: é preciso escolher o motivo para o botão "Eliminar" ficar ativo. */
    private fun confirmarEliminacao(avaria: Avaria) {
        val motivos = resources.getStringArray(R.array.motivos_eliminar)
        escolherEConfirmar(
            titulo = getString(R.string.eliminar_titulo),
            opcoes = motivos.map { Opcao(it, it) },
            textoConfirmar = getString(R.string.detalhe_eliminar),
            etiqueta = avaria.codigo,
            aviso = getString(R.string.eliminar_aviso)
        ) { eliminacao.eliminar(avaria, it) }
    }

    /** Snackbar com "Desfazer" durante 10 segundos. Fica na Activity, porque este ecrã fecha logo a seguir. */
    private fun mostrarDesfazer(motivo: String) {
        val eliminacao = eliminacao
        Snackbar.make(
            requireView(),
            getString(R.string.avaria_eliminada, motivo),
            DURACAO_DESFAZER_MS
        ).setAction(R.string.desfazer) { eliminacao.desfazer() }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_AVARIA_ID = "avariaId"
        const val ARG_CODIGO = "codigo"

        /** O título da barra: “AV-2026-0418” aparece como “OT · 2026-0418” (ordem de trabalho). */
        fun titulo(context: android.content.Context, codigo: String) = context.getString(R.string.detalhe_titulo_ot, codigo.removePrefix("AV-"))

        /** Os argumentos para abrir o detalhe de uma avaria: o id e o título da barra. */
        fun argumentos(context: android.content.Context, avariaId: Long, codigo: String) = Bundle().apply {
            putLong(ARG_AVARIA_ID, avariaId)
            putString(ARG_CODIGO, titulo(context, codigo))
        }
        private const val DURACAO_DESFAZER_MS = 10_000
    }
}
