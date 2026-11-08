package pt.ubi.di.pdm.finsafix

import pt.ubi.di.pdm.finsafix.util.Numeros
import androidx.core.view.size
import androidx.core.view.get
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.StrictMode
import android.view.Menu
import android.view.MenuItem
import android.widget.PopupMenu
import android.os.Handler
import android.os.SystemClock
import android.widget.Chronometer
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AlertDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import pt.ubi.di.pdm.finsafix.data.AvisoResumo
import pt.ubi.di.pdm.finsafix.data.ParagemResumo
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.databinding.ActivityMainBinding
import pt.ubi.di.pdm.finsafix.ui.avisos.AvisoViewModel
import pt.ubi.di.pdm.finsafix.ui.avisos.mostrar
import pt.ubi.di.pdm.finsafix.ui.comum.ViewModelBase
import pt.ubi.di.pdm.finsafix.ui.detalhe.DetalheFragment
import pt.ubi.di.pdm.finsafix.ui.detalhe.EliminarAvariaViewModel
import pt.ubi.di.pdm.finsafix.ui.mais.ItemMenu
import pt.ubi.di.pdm.finsafix.ui.mais.MaisFragment
import pt.ubi.di.pdm.finsafix.ui.menu.ContadoresViewModel
import pt.ubi.di.pdm.finsafix.ui.paragens.ParagemAtivaViewModel
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Posto
import pt.ubi.di.pdm.finsafix.util.Sessao
import pt.ubi.di.pdm.finsafix.util.Tema

/**
 * A única Activity. Cada ecrã é um Fragment do nav_graph.
 * O menu é uma BottomNavigationView no telemóvel e uma NavigationRailView no tablet (layout-sw600dp).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var menuAtual = 0
    private var itensDoPerfil: List<MenuItem> = emptyList()
    private var ultimosContadores: Map<Int, ContadoresViewModel.Contador> = emptyMap()
    private var naEntrada = true
    private var avisoAtual: AvisoResumo? = null
    private var paragemAtual: ParagemResumo? = null

    // Sessão por inatividade: depois do tempo sem toques pergunta "Ainda é X?"; sem resposta, termina.
    private val relogioSessao = Handler(Looper.getMainLooper())
    private val perguntar = Runnable { perguntarSeAindaEle() }
    private val terminarPorInatividade = Runnable { terminarSessao(porInatividade = true) }
    // Ao fim do turno pergunta-se “Ainda é X?” com o motivo “Começou um novo turno.”
    private val mudancaDeTurno = Runnable {
        if (Sessao.pessoa != null && perguntaSessao?.isShowing != true) perguntarSeAindaEle()
        agendarMudancaDeTurno()
    }
    private var perguntaSessao: AlertDialog? = null
    private var ultimaInteracao = 0L

    // O mesmo objeto que o DetalheFragment obtém com activityViewModels().
    private val eliminacao: EliminarAvariaViewModel by viewModels()
    private val avisos: AvisoViewModel by viewModels()
    private val contadores: ContadoresViewModel by viewModels()
    private val paragemAtiva: ParagemAtivaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Só na build de depuração: o Logcat (filtro StrictMode) avisa de leituras e escritas em disco na thread principal
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyLog().build())
        }
        super.onCreate(savedInstanceState)
        Tema.aplicar(this)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        afastarDasBarrasDoSistema()
        ligarNavegacao()
        observarEstadoGlobal()
        navController.addOnDestinationChangedListener { _, destino, _ -> aoMudarDeEcra(destino.id) }
    }

    /**
     * Afasta o conteúdo das barras do sistema. As insets continuam a ser passadas,
     * para a Snackbar também ficar acima da barra de navegação.
     */
    private fun afastarDasBarrasDoSistema() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
        // O menu não acrescenta margens próprias para as barras do sistema (a raiz já as aplicou acima).
        ViewCompat.setOnApplyWindowInsetsListener(binding.navegacao) { _, insets -> insets }
    }

    /** Liga o NavController à barra de topo e ao menu (BottomNavigationView ou NavigationRailView). */
    private fun ligarNavegacao() {
        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navController = navHost.navController

        // Ecrãs principais (sem seta de voltar na barra de topo).
        val ecrasPrincipais = AppBarConfiguration(
            setOf(
                R.id.entradaFragment, R.id.inicioFragment, R.id.historicoFragment,
                R.id.avariasTecnicoFragment, R.id.minhasFragment, R.id.parametrosFragment, R.id.producaoFragment, R.id.seccaoFragment, R.id.pedidosFragment, R.id.validarFragment,
                R.id.engenhariaInicioFragment, R.id.avariasFabricaFragment, R.id.indicadoresFragment,
                R.id.oficinaFragment, R.id.oficinaResumoFragment,
                R.id.pedidosOperadorFragment, R.id.instrucoesFragment, R.id.turnoFragment,
                R.id.preventivaFragment, R.id.rondasFragment, R.id.stockFragment, R.id.resumoChefeFragment, R.id.planoFragment, R.id.planosFragment, R.id.instrucoesEngFragment, R.id.relatoriosFragment, R.id.distribuirFragment
            )
        )
        binding.topo.barraTopo.setupWithNavController(navController, ecrasPrincipais)
        binding.topo.barraTopo.inflateMenu(R.menu.menu_barra_topo)
        binding.topo.barraTopo.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.acaoTerminarSessao) {
                terminarSessao(porInatividade = false)
                true
            } else {
                false
            }
        }
        binding.navegacao.setupWithNavController(navController)
        // O "Mais" da barra do telemóvel abre uma janela; todos os outros itens navegam como habitualmente.
        binding.navegacao.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.mais) {
                abrirMais()
                false
            } else {
                NavigationUI.onNavDestinationSelected(item, navController)
            }
        }
        binding.botaoMais?.setOnClickListener { abrirMais() }
    }

    /** O que se vê em todos os ecrãs: contadores do menu, paragem ativa e banner de aviso. */
    private fun observarEstadoGlobal() {
        contadores.contadores.observe(this) { aplicarContadores(it) }
        paragemAtiva.paragem.observe(this) {
            paragemAtual = it
            mostrarParagemAtiva()
        }

        // Erros que acontecem fora de um ecrã: o "Desfazer" (o detalhe já fechou) e a resposta ao banner.
        mostrarErros(eliminacao)
        mostrarErros(avisos)
        avisos.urgente.observe(this) {
            avisoAtual = it
            atualizarBanner()
        }
    }

    /** Cada vez que se muda de ecrã: mostra ou esconde a barra e o menu e põe os dados da pessoa em sessão. */
    private fun aoMudarDeEcra(destinoId: Int) {
        naEntrada = destinoId == R.id.entradaFragment
        binding.topo.barraTopo.isVisible = !naEntrada
        // O título passa para a vista própria, por baixo do contexto (“Secção · Schelling”)
        binding.topo.textoTitulo.text = binding.topo.barraTopo.title
        binding.topo.barraTopo.title = null
        binding.navegacao.isVisible = !naEntrada
        val pessoa = Sessao.pessoa
        if (!naEntrada && pessoa != null) {
            preencherBarra(pessoa)
            paragemAtiva.definirPessoa(pessoa)
            mostrarMenuDoPerfil(pessoa)
            contadores.definirPessoa(pessoa)
            avisos.definirPosto(Posto.obterId(this))
        }
        atualizarBanner()
        mostrarParagemAtiva()
        // Navegar conta como atividade; na entrada não há sessão para vigiar.
        if (naEntrada) pararRelogioDaSessao() else reiniciarInatividade()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        reiniciarInatividade()
    }

    override fun onResume() {
        super.onResume()
        val pessoa = Sessao.pessoa ?: return
        // Se a app esteve em segundo plano mais do que o tempo da sessão, a sessão já acabou.
        val total = Sessao.inatividadeMs(pessoa) + Sessao.respostaMs(pessoa)
        if (System.currentTimeMillis() - ultimaInteracao >= total) terminarSessao(porInatividade = true) else reiniciarInatividade()
    }

    override fun onDestroy() {
        pararRelogioDaSessao()
        super.onDestroy()
    }

    fun terminarSessao(porInatividade: Boolean) {
        pararRelogioDaSessao()
        navController.navigate(R.id.acao_terminar_sessao)
        if (porInatividade) Snackbar.make(binding.root, R.string.sessao_terminada, Snackbar.LENGTH_LONG).show()
    }

    fun mostrarAviso(mensagem: String) {
        Snackbar.make(binding.root, mensagem, Snackbar.LENGTH_LONG).show()
    }

    private fun pararRelogioDaSessao() {
        relogioSessao.removeCallbacks(mudancaDeTurno)
        relogioSessao.removeCallbacks(perguntar)
        relogioSessao.removeCallbacks(terminarPorInatividade)
        perguntaSessao?.dismiss()
        perguntaSessao = null
    }

    private fun agendarMudancaDeTurno() {
        relogioSessao.removeCallbacks(mudancaDeTurno)
        relogioSessao.postDelayed(mudancaDeTurno, Datas.fimDoTurnoAtual() - System.currentTimeMillis() + 1000)
    }

    /** Recomeça a contagem. Com a pergunta no ecrã, só os botões da pergunta contam. */
    private fun reiniciarInatividade() {
        val pessoa = Sessao.pessoa ?: return
        if (perguntaSessao?.isShowing == true) return
        ultimaInteracao = System.currentTimeMillis()
        if (!relogioSessao.hasCallbacks(mudancaDeTurno)) agendarMudancaDeTurno()
        relogioSessao.removeCallbacks(perguntar)
        relogioSessao.postDelayed(perguntar, Sessao.inatividadeMs(pessoa))
    }

    private fun perguntarSeAindaEle() {
        val pessoa = Sessao.pessoa ?: return
        relogioSessao.postDelayed(terminarPorInatividade, Sessao.respostaMs(pessoa))
        // Passou para outro turno desde a entrada? Senão foi por falta de uso.
        val motivo = if (Datas.inicioDoTurnoAtual() > Sessao.entradaEm) getString(R.string.sessao_motivo_turno)
        else getString(R.string.sessao_motivo_inatividade, Sessao.inatividadeMs(pessoa) / 60_000)
        perguntaSessao = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.sessao_titulo, pessoa.nomeCurto()))
            .setMessage(getString(R.string.sessao_mensagem, motivo, Sessao.respostaMs(pessoa) / 1000))
            .setCancelable(false)
            .setPositiveButton(R.string.sessao_continuar) { _, _ ->
                relogioSessao.removeCallbacks(terminarPorInatividade)
                perguntaSessao = null
                reiniciarInatividade()
            }
            .setNegativeButton(R.string.sessao_terminar) { _, _ -> terminarSessao(porInatividade = false) }
            .show()
    }

    /** Contexto, modo, avatar e texto do botão de sair, conforme o perfil. */
    private fun preencherBarra(pessoa: Pessoa) {
        val topo = binding.topo
        topo.textoIniciais.text = pessoa.iniciais()
        topo.textoNome?.text = pessoa.nome
        // A chefia mostra a função (“Chefe de turno”); os outros, o turno
        val comModo = pessoa.papel == Pessoa.PAPEL_ENGENHEIRO || pessoa.papel == Pessoa.PAPEL_CHEFE
        topo.textoTurnoPessoa?.text = if (comModo) pessoa.funcao.substringBefore(" · ") else getString(R.string.mais_turno_nome, getString(Datas.nomeTurno(pessoa.turno)))
        val modo = when (pessoa.papel) {
            Pessoa.PAPEL_ENGENHEIRO -> getString(R.string.barra_modo_engenharia)
            Pessoa.PAPEL_CHEFE -> getString(R.string.barra_modo_chefia, Sessao.postoNome)
            else -> null
        }
        // Com o modo por cima do título, “Guardado neste tablet” fica só o ícone para o título ter espaço
        topo.textoGuardado?.text = if (modo != null) "" else getString(R.string.barra_guardado)
        // “Modo chefia · Oficina Mecânica” fica por cima do título (no telemóvel e no tablet)
        val contexto = when {
            modo != null -> modo
            pessoa.papel == Pessoa.PAPEL_OPERADOR -> getString(R.string.barra_contexto_secao, Sessao.postoNome)
            else -> Sessao.postoNome
        }
        topo.textoContexto.text = contexto
        topo.barraTopo.menu.findItem(R.id.acaoTerminarSessao)?.setTitle(
            when (pessoa.papel) {
                Pessoa.PAPEL_ENGENHEIRO -> R.string.sair_engenharia
                Pessoa.PAPEL_CHEFE -> R.string.sair_chefia
                else -> R.string.terminar_sessao
            }
        )
    }

    /** Chip (tablet) ou faixa (telemóvel) com o equipamento parado e o tempo parado; toca e abre a avaria. */
    private fun mostrarParagemAtiva() {
        val vista = binding.topo.chipParagem ?: binding.topo.faixaParagem ?: return
        val resumo = paragemAtual.takeIf { !naEntrada }
        vista.root.isVisible = resumo != null
        if (resumo == null) {
            vista.cronometro.stop()
            return
        }
        vista.texto.text = getString(R.string.barra_paragem, resumo.equipamentoNome)
        // O Chronometer mede a partir do arranque do aparelho (elapsedRealtime), por isso converte-se o início.
        vista.cronometro.base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - resumo.paragem.inicio)
        vista.cronometro.onChronometerTickListener = Chronometer.OnChronometerTickListener { formatarTempoParado(it) }
        formatarTempoParado(vista.cronometro)
        vista.cronometro.start()
        val avariaId = resumo.paragem.avariaId
        val codigo = resumo.avariaCodigo
        vista.root.setOnClickListener {
            if (avariaId != null && codigo != null) {
                navController.navigate(R.id.acao_para_detalhe, DetalheFragment.argumentos(this, avariaId, codigo))
            } else {
                navController.navigate(R.id.paragensFragment)
            }
        }
    }

    /** HH:MM:SS (o formato do Chronometer, MM:SS, parece uma hora do dia). */
    private fun formatarTempoParado(cronometro: Chronometer) {
        val segundos = (SystemClock.elapsedRealtime() - cronometro.base) / 1000
        cronometro.text = getString(R.string.tempo_hms, segundos / 3600, segundos / 60 % 60, segundos % 60)
    }

    /** O banner só aparece com alguém na sessão (na entrada não há ninguém para responder). */
    private fun atualizarBanner() {
        // Quem só consulta (engenharia, chefe de oficina) não responde a avisos: não vê o banner.
        val vePedidos = Sessao.pessoa?.soConsulta() != true
        binding.bannerAviso.mostrar(avisoAtual.takeIf { !naEntrada && vePedidos }) { aviso ->
            Sessao.pessoa?.let { avisos.responder(aviso, it) }
        }
    }

    private fun mostrarErros(viewModel: ViewModelBase) {
        viewModel.erro.observe(this) { mensagem ->
            if (mensagem != null) {
                Snackbar.make(binding.root, mensagem, Snackbar.LENGTH_LONG).show()
                viewModel.erroMostrado()
            }
        }
    }

    fun abrirMais() {
        if (supportFragmentManager.findFragmentByTag("mais") == null) MaisFragment().show(supportFragmentManager, "mais")
    }

    /** Os itens do perfil que não cabem na barra e ficam em "Mais". */
    fun itensDoMais(): List<ItemMenu> =
        itensDoPerfil.drop(resources.getInteger(R.integer.itens_no_menu)).map { ItemMenu(it, ultimosContadores[it.itemId]) }

    /** Números nos ícones do menu (vermelhos se urgentes, cinzentos se não) e a soma do que está em "Mais". */
    private fun aplicarContadores(mapa: Map<Int, ContadoresViewModel.Contador>) {
        ultimosContadores = mapa
        val menu = binding.navegacao.menu
        for (i in 0 until menu.size) {
            val id = menu[i].itemId
            val contador = mapa[id]
            if (id == R.id.mais) continue
            if (contador == null || contador.numero == 0) binding.navegacao.removeBadge(id) else pintar(id, contador)
        }
        val noMais = itensDoMais().sumOf { it.contador?.numero ?: 0 }
        if (binding.botaoMais == null) {
            if (noMais > 0) pintar(R.id.mais, ContadoresViewModel.Contador(noMais)) else binding.navegacao.removeBadge(R.id.mais)
        } else {
            binding.contadorMais?.isVisible = noMais > 0
            binding.contadorMais?.text = Numeros.formatar(noMais)
        }
    }

    private fun pintar(id: Int, contador: ContadoresViewModel.Contador) {
        val vista = binding.navegacao
        val etiqueta = vista.getOrCreateBadge(id)
        etiqueta.number = contador.numero
        etiqueta.isVisible = true
        etiqueta.backgroundColor = if (contador.urgente) getColor(R.color.if_error) else MaterialColors.getColor(vista, com.google.android.material.R.attr.colorSecondaryContainer)
        etiqueta.badgeTextColor = if (contador.urgente) getColor(R.color.if_on_error) else MaterialColors.getColor(vista, com.google.android.material.R.attr.colorOnSecondaryContainer)
    }

    /**
     * Monta o menu do perfil: no telemóvel os primeiros 4 itens e o "Mais"; no tablet os primeiros 7
     * (o "Mais" é o botão por baixo do menu). O que não cabe vai para a janela "Mais".
     */
    private fun mostrarMenuDoPerfil(pessoa: Pessoa) {
        val menu = when (pessoa.papel) {
            Pessoa.PAPEL_TECNICO -> R.menu.menu_tecnico
            Pessoa.PAPEL_ENGENHEIRO -> R.menu.menu_engenharia
            Pessoa.PAPEL_CHEFE -> if (pessoa.eChefeDeOficina()) R.menu.menu_oficina else R.menu.menu_chefe
            else -> R.menu.menu_operador
        }
        if (menu == menuAtual) return
        menuAtual = menu
        val todos = PopupMenu(this, binding.root).menu
        menuInflater.inflate(menu, todos)
        itensDoPerfil = (0 until todos.size).map { todos[it] }
        binding.navegacao.menu.clear()
        itensDoPerfil.take(resources.getInteger(R.integer.itens_no_menu)).forEach {
            binding.navegacao.menu.add(Menu.NONE, it.itemId, Menu.NONE, it.title).icon = it.icon
        }
        if (binding.botaoMais == null) binding.navegacao.menu.add(Menu.NONE, R.id.mais, Menu.NONE, R.string.menu_mais).setIcon(R.drawable.ic_menu)
        aplicarContadores(ultimosContadores)
    }
}
