package pt.ubi.di.pdm.finsafix.ui.turno

import pt.ubi.di.pdm.finsafix.data.Turno
import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import kotlinx.coroutines.flow.combine
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.ui.comum.Opcao
import pt.ubi.di.pdm.finsafix.ui.comum.escolherUma
import pt.ubi.di.pdm.finsafix.data.Avaria
import pt.ubi.di.pdm.finsafix.data.AutorizacaoResumo
import pt.ubi.di.pdm.finsafix.data.AvariaResumo
import androidx.lifecycle.map
import pt.ubi.di.pdm.finsafix.data.MembroEquipa
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Registo
import pt.ubi.di.pdm.finsafix.data.RegistoResumo
import pt.ubi.di.pdm.finsafix.databinding.FragmentTurnoBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.abrirDetalhe
import pt.ubi.di.pdm.finsafix.ui.autorizacoes.EstiloAutorizacao
import pt.ubi.di.pdm.finsafix.ui.lista.Acao
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.pedirTexto
import pt.ubi.di.pdm.finsafix.ui.lista.Linha
import pt.ubi.di.pdm.finsafix.ui.lista.LinhaAdapter
import pt.ubi.di.pdm.finsafix.ui.pedidos.EstiloRegisto
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Validacao
import pt.ubi.di.pdm.finsafix.util.pessoaOuVoltarAEntrada

/** O que passa para o próximo turno (só o chefe de oficina o vê): intervenções em curso, em progresso, pendentes há mais de 1 h e autorizações. */
class Passagem(
    val emCurso: List<Pair<AvariaResumo, List<MembroEquipa>>>,
    val emProgresso: List<AvariaResumo>,
    val pendentes: List<AvariaResumo>,
    val autorizacoes: List<AutorizacaoResumo>
) {
    val vazia get() = emCurso.isEmpty() && emProgresso.isEmpty() && pendentes.isEmpty() && autorizacoes.isEmpty()
}

class TurnoViewModel(application: Application) : ViewModelBase(application) {

    private val pessoa = MutableLiveData<Pessoa>()
    private val posto = pessoa.map { it.postoId.orEmpty() }

    // O chefe de oficina vê as notas que o chefe anterior lhe deixou (e só até confirmar a receção); os outros veem as do posto
    val notas: LiveData<List<RegistoResumo>> = pessoa.switchMap { quem ->
        val id = quem.postoId.orEmpty()
        (if (quem.eChefeDeOficina()) repositorio.notasParaChefe(id, quem.id) else repositorio.notasDoPosto(id)).paraLiveData(emptyList())
    }
    val rececoes: LiveData<List<RegistoResumo>> = posto.switchMap { repositorio.rececoesDoPosto(it).paraLiveData(emptyList()) }
    val pessoas: LiveData<List<Pessoa>> = posto.switchMap { repositorio.pessoasDoPosto(it).paraLiveData(emptyList()) }

    val passagem: LiveData<Passagem> = pessoa.switchMap { quem ->
        val especialidades = listOfNotNull(quem.especialidade, Avaria.NAO_SEI)
        if (!quem.eChefeDeOficina()) return@switchMap MutableLiveData(Passagem(emptyList(), emptyList(), emptyList(), emptyList()))
        combine(
            repositorio.avariasDeConsulta(quem, null, listOf(Avaria.PENDENTE, Avaria.EM_CURSO)),
            repositorio.equipasDasOficinas(especialidades),
            repositorio.autorizacoesAtivasDasOficinas(especialidades)
        ) { avarias, equipas, autorizacoes ->
            val agora = System.currentTimeMillis()
            val pendentes = avarias.filter { it.avaria.estado == Avaria.PENDENTE }
            Passagem(
                emCurso = avarias.filter { it.avaria.estado == Avaria.EM_CURSO }
                    .map { resumo -> resumo to equipas.filter { it.participacao.avariaId == resumo.avaria.id } },
                emProgresso = pendentes.filter { it.avaria.progressoNota != null },
                pendentes = pendentes.filter { it.avaria.progressoNota == null && it.avaria.dataCriacao <= agora - HORA_MS },
                // Aprovadas ou em curso com a validade a acabar antes do fim deste turno (as já expiradas também precisam de atenção)
                autorizacoes = autorizacoes.filter { (it.autorizacao.validaAte ?: Long.MAX_VALUE) <= Datas.fimDoTurnoAtual() }
            )
        }.paraLiveData(Passagem(emptyList(), emptyList(), emptyList(), emptyList()))
    }

    fun definirPessoa(quem: Pessoa) {
        if (pessoa.value != quem) pessoa.value = quem
    }

    fun confirmarPassagem(registoId: Long, chefe: Pessoa) {
        executar { avisar(if (repositorio.confirmarPassagem(registoId, chefe)) R.string.passagem_confirmada else R.string.erro_decisao) }
    }

    private companion object {
        const val HORA_MS = 60 * 60 * 1000L
    }

    fun criarNota(categoria: String, texto: String, quem: Pessoa) {
        executar {
            repositorio.criarNota(categoria, texto, quem)
            avisar(R.string.nota_registada)
        }
    }

    fun confirmarRececao(quem: Pessoa) {
        executar { avisar(if (repositorio.confirmarRececao(quem)) R.string.rececao_confirmada else R.string.rececao_ja_confirmada) }
    }
}

/**
 * “Turno” do operador, do técnico e do chefe de oficina: notas para o turno, confirmar a receção, turno em curso e horário do posto.
 * O chefe de oficina tem ainda “Passa para o próximo turno” e as notas que o chefe anterior lhe deixou.
 */
class TurnoFragment : Fragment() {

    private var _binding: FragmentTurnoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TurnoViewModel by viewModels()
    private val notas = LinhaAdapter()
    private val rececoes = LinhaAdapter()
    private val passa = LinhaAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTurnoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = pessoaOuVoltarAEntrada() ?: return
        if (pessoa.postoId == null) return
        viewModel.definirPessoa(pessoa)

        for (lista in listOf(binding.listaNotas, binding.listaRececoes, binding.listaPassa)) {
            lista.addItemDecoration(MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL))
        }
        binding.listaNotas.adapter = notas
        binding.listaRececoes.adapter = rececoes
        binding.listaPassa.adapter = passa
        binding.blocoPassa.isVisible = pessoa.eChefeDeOficina()
        if (pessoa.eChefeDeOficina()) viewModel.passagem.observe(viewLifecycleOwner) { mostrarPassagem(it) }
        binding.botaoLi.setOnClickListener { viewModel.confirmarRececao(pessoa) }
        binding.botaoNota.setOnClickListener { novaNota(pessoa) }
        mostrarTurnoEmCurso()

        viewModel.notas.observe(viewLifecycleOwner) { lista ->
            notas.submitList(lista.map { linhaDeNota(it) })
            binding.textoSemNotas.isVisible = lista.isEmpty()
            binding.textoNotas.text = resources.getQuantityString(R.plurals.turno_notas, lista.size, lista.size)
        }
        viewModel.rececoes.observe(viewLifecycleOwner) { lista ->
            rececoes.submitList(lista.map { linhaDeRececao(it, pessoa) })
            binding.textoSemRececoes.isVisible = lista.isEmpty()
        }
        viewModel.pessoas.observe(viewLifecycleOwner) { mostrarHorario(it) }
        mostrarErrosDe(viewModel)
    }

    private fun linhaDeNota(resumo: RegistoResumo) = Linha(
        id = resumo.registo.id.toString(),
        estado = EstiloRegisto.estadoNota(resumo.registo.extra),
        titulo = resumo.registo.texto,
        sub = listOf(resumo.pessoaNome, Datas.dataHora(resumo.registo.dataCriacao)).joinToString(EstiloAvaria.SEPARADOR)
    )

    private fun linhaDeRececao(resumo: RegistoResumo, pessoa: Pessoa): Linha {
        val registo = resumo.registo
        val confirmada = registo.estado == Registo.CONFIRMADA
        return Linha(
            // O chefe de oficina confirma aqui as receções dos técnicos (como em Validar)
            acoes = if (!confirmada && pessoa.eChefeDeOficina()) listOf(Acao(getString(R.string.turno_confirmar_passagem)) { viewModel.confirmarPassagem(registo.id, pessoa) }) else emptyList(),
            id = registo.id.toString(),
            estado = if (confirmada) EstiloAvaria.Estilo(R.string.rececao_estado_confirmada, R.drawable.ic_check_circle, R.color.if_ok_text)
            else EstiloAvaria.Estilo(R.string.rececao_estado_por_confirmar, R.drawable.ic_clock, R.color.if_warn_text),
            textoEstado = if (confirmada) getString(R.string.pedido_fechado_por, resumo.tratadoPorNome ?: "", Datas.dataHora(registo.dataTratamento ?: registo.dataCriacao)) else null,
            titulo = getString(R.string.rececao_titulo, getString(Datas.nomeTurno(registo.extra ?: Turno.MANHA)).lowercase(), resumo.pessoaNome),
            sub = Datas.dataHora(registo.dataCriacao)
        )
    }

    /** “Passa para o próximo turno”: uma linha por intervenção, trabalho em progresso, pendente há mais de 1 h e autorização; cada uma abre a avaria. */
    private fun mostrarPassagem(passagem: Passagem) {
        val agora = System.currentTimeMillis()
        val linhas = passagem.emCurso.map { (resumo, equipa) ->
            val nomes = equipa.joinToString(", ") {
                if (it.participacao.tecnicoId == resumo.avaria.tecnicoId) getString(R.string.passa_equipa_responsavel, it.nome) else it.nome
            }
            linhaDaAvaria(resumo, EstiloAvaria.Estilo(R.string.passa_estado_em_curso, R.drawable.ic_wrench, R.color.if_info_text), getString(R.string.passa_equipa, nomes))
        } + passagem.emProgresso.map {
            linhaDaAvaria(
                it, EstiloAvaria.Estilo(R.string.passa_estado_em_progresso, R.drawable.ic_pause, R.color.if_warn_text),
                getString(R.string.passa_nota_de, it.avaria.progressoNota.orEmpty(), it.progressoPorNome.orEmpty())
            )
        } + passagem.pendentes.map {
            linhaDaAvaria(
                it, EstiloAvaria.Estilo(R.string.passa_estado_pendente, R.drawable.ic_clock, R.color.if_warn_text),
                getString(R.string.passa_aberta_ha, Datas.duracao(requireContext(), agora - it.avaria.dataCriacao), it.tecnicoNome ?: getString(R.string.avaria_sem_tecnico))
            )
        } + passagem.autorizacoes.map { resumo ->
            val aut = resumo.autorizacao
            Linha(
                id = "aut" + aut.id, estado = EstiloAvaria.Estilo(R.string.passa_estado_autorizacao, R.drawable.ic_shield, R.color.if_error_text),
                titulo = getString(R.string.passa_titulo_avaria, resumo.avariaCodigo, EstiloAutorizacao.nomes(requireContext(), aut.tipo)),
                sub = getString(R.string.passa_autorizacao, resumo.equipamentoNome, Datas.hora(aut.validaAte ?: agora), resumo.pedidaPorNome),
                aoTocar = { abrirDetalhe(aut.avariaId, resumo.avariaCodigo) }
            )
        }
        passa.submitList(linhas)
        binding.textoPassaVazio.isVisible = passagem.vazia
    }

    private fun linhaDaAvaria(resumo: AvariaResumo, estado: EstiloAvaria.Estilo, sub: String) = Linha(
        id = estado.texto.toString() + resumo.avaria.id, estado = estado,
        titulo = getString(R.string.passa_titulo_avaria, resumo.avaria.codigo, resumo.avaria.titulo), sub = sub,
        extra = resumo.equipamentoNome, aoTocar = { abrirDetalhe(resumo) }
    )

    /** Escolhe a categoria e depois escreve a nota (pelo menos 5 caracteres). */
    private fun novaNota(pessoa: Pessoa) {
        val opcoes = Registo.CATEGORIAS_NOTA.map { Opcao(it, getString(R.string.nota_categoria_nome, getString(nomeCategoria(it)))) }
        escolherUma(getString(R.string.nota_tipo_titulo), opcoes) { categoria ->
            pedirTexto(
                R.string.nota_titulo, R.string.nota_dica, R.string.nota_registar,
                validar = { if (Validacao.notaValida(it)) null else getString(R.string.erro_nota) }
            ) { texto -> viewModel.criarNota(categoria, texto, pessoa) }
        }
    }

    private fun nomeCategoria(categoria: String): Int = EstiloRegisto.estadoNota(categoria).texto

    /** “Tarde · 16:00–00:00” e quanto já passou e quanto falta. */
    private fun mostrarTurnoEmCurso() {
        val turno = Datas.turnoAtual()
        val decorrido = System.currentTimeMillis() - Datas.inicioDoTurnoAtual()
        binding.textoTurnoAtual.text = getString(R.string.turno_em_curso, getString(Datas.nomeTurno(turno)), getString(Datas.horarioTurno(turno)))
        binding.textoDecorrido.text = getString(
            R.string.turno_decorrido, Datas.duracao(requireContext(), decorrido), Datas.duracao(requireContext(), DURACAO_TURNO_MS - decorrido)
        )
    }

    /** Quem está escalado em cada turno deste posto. */
    private fun mostrarHorario(pessoas: List<Pessoa>) {
        val atual = Datas.turnoAtual()
        binding.textoHorario.text = Turno.TODOS.joinToString("\n\n") { turno ->
            val nomes = pessoas.filter { it.turno == turno }.joinToString(", ") { it.nome }
                .ifEmpty { getString(R.string.turno_sem_escala) }
            val agora = if (turno == atual) " · " + getString(R.string.turno_agora) else ""
            getString(R.string.turno_horario_linha, getString(Datas.nomeTurno(turno)), getString(Datas.horarioTurno(turno)), nomes) + agora
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val DURACAO_TURNO_MS = 8 * 60 * 60 * 1000L
    }
}
