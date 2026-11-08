package pt.ubi.di.pdm.finsafix.ui.mais

import pt.ubi.di.pdm.finsafix.util.Numeros
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import pt.ubi.di.pdm.finsafix.MainActivity
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.databinding.FragmentMaisBinding
import pt.ubi.di.pdm.finsafix.databinding.ItemMaisBinding
import pt.ubi.di.pdm.finsafix.ui.comum.escolherPosto
import pt.ubi.di.pdm.finsafix.ui.comum.pedirPin
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Posto
import pt.ubi.di.pdm.finsafix.util.Sessao
import pt.ubi.di.pdm.finsafix.util.Tema

/**
 * "Mais": os itens do menu que não cabem, o modo escuro, a ajuda, o posto do tablet e terminar o turno.
 * É uma janela inferior (bottom sheet) para o telemóvel e o tablet.
 */
class MaisFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentMaisBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MaisViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMaisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // No tablet a janela abre inteira (não fica só com a primeira linha visível).
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pessoa = Sessao.pessoa
        // A LiveData só tem valor enquanto é observada: a lista de “Configurar posto” precisa destas duas
        viewModel.postos.observe(viewLifecycleOwner) { }
        viewModel.equipamentos.observe(viewLifecycleOwner) { }
        val principal = requireActivity() as MainActivity
        val chefia = pessoa?.papel == Pessoa.PAPEL_CHEFE || pessoa?.papel == Pessoa.PAPEL_ENGENHEIRO

        // Na entrada (sem sessão) só há modo escuro, emergência e configurar o posto
        binding.cabecalhoPessoa.isVisible = pessoa != null
        if (pessoa != null) preencherPessoa(pessoa, chefia)
        mostrarItens(principal, pessoa, chefia)
    }

    private fun preencherPessoa(pessoa: Pessoa, chefia: Boolean) {
        binding.avatar.text = pessoa.nome.split(" ").filter { it.first().isLetter() }.take(2).map { it.first() }.joinToString("")
        binding.nome.text = pessoa.nome
        binding.funcao.text = when {
            pessoa.papel == Pessoa.PAPEL_ENGENHEIRO -> getString(R.string.mais_funcao_ambito, pessoa.funcao, getString(R.string.mais_toda_fabrica))
            chefia -> getString(R.string.mais_funcao_ambito, pessoa.funcao, Sessao.postoNome)
            else -> getString(R.string.mais_funcao_ambito, pessoa.funcao, getString(R.string.mais_turno_nome, getString(Datas.nomeTurno(pessoa.turno))))
        }
    }

    private fun mostrarItens(principal: MainActivity, pessoa: Pessoa?, chefia: Boolean) {
        // Os itens do menu que não cabem na barra (ver MainActivity.itensDoMais).
        val itens = principal.itensDoMais()
        binding.listaExtra.isVisible = itens.isNotEmpty()
        itens.forEach { extra ->
            val linha = linha(R.drawable.ic_chev, extra.item.title.toString(), null, extra.contador?.numero) {
                dismiss()
                val navegador = (requireActivity().supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
                NavigationUI.onNavDestinationSelected(extra.item, navegador)
            }
            linha.icone.setImageDrawable(extra.item.icon)
            binding.listaExtra.addView(linha.root)
        }

        binding.interruptorEscuro.isChecked = Tema.estaEscuro(requireContext())
        binding.interruptorEscuro.setOnCheckedChangeListener { _, escuro -> Tema.definir(requireContext(), escuro) }

        binding.listaAjuda.isVisible = true
        linha(R.drawable.ic_help, getString(R.string.mais_como_usar), getString(R.string.mais_como_usar_sub), null) {
            pessoa?.let { mostrarComoUsar(it) }
        }.also { if (pessoa != null && !chefia) binding.listaAjuda.addView(it.root) }
        linha(R.drawable.ic_emergency, getString(R.string.mais_emergencia), getString(R.string.mais_emergencia_sub), null) {
            mostrarEmergencia()
        }.also {
            it.icone.setColorFilter(requireContext().getColor(R.color.if_error_text))
            binding.listaAjuda.addView(it.root)
        }

        binding.textoEntrada.isVisible = pessoa != null && !chefia
        binding.textoEntrada.text = getString(
            R.string.mais_entrada, Datas.hora(Sessao.entradaEm), getString(Datas.nomeTurno(Datas.turnoAtual())).lowercase()
        )
        linha(R.drawable.ic_lock, getString(R.string.mais_configurar_posto), getString(R.string.mais_configurar_posto_sub), null) {
            configurarPosto()
        }.also { binding.listaPosto.addView(it.root) }

        binding.botaoSair.isVisible = pessoa != null
        binding.botaoSair.setText(
            when (pessoa?.papel) {
                Pessoa.PAPEL_ENGENHEIRO -> R.string.mais_sair_engenharia
                Pessoa.PAPEL_CHEFE -> R.string.mais_sair_chefia
                else -> R.string.mais_terminar_turno
            }
        )
        binding.botaoSair.setOnClickListener {
            dismiss()
            principal.terminarSessao(porInatividade = false)
        }
        binding.textoSessao.isVisible = chefia
        binding.textoSessao.text = getString(R.string.mais_sessao_chefia, Sessao.CHEFIA_MIN)
    }

    private fun linha(@DrawableRes icone: Int, titulo: String, sub: String?, numero: Int?, aoTocar: () -> Unit): ItemMaisBinding {
        val item = ItemMaisBinding.inflate(layoutInflater)
        item.icone.setImageResource(icone)
        item.titulo.text = titulo
        item.sub.text = sub
        item.sub.isVisible = sub != null
        item.numero.isVisible = numero != null && numero > 0
        item.numero.text = numero?.let { Numeros.formatar(it) }
        item.root.setOnClickListener { aoTocar() }
        return item
    }

    private fun configurarPosto() = pedirPin(R.string.pin_administrador_titulo) { pin, _, _ ->
        val erro = viewModel.verificarPinAdministrador(pin)
        if (erro == null) mudarDePosto()
        erro
    }

    private fun mudarDePosto() {
        val activity = requireActivity() as MainActivity
        val postos = viewModel.postos.value.orEmpty()
        val atualId = Posto.obterId(requireContext())
        escolherPosto(postos, viewModel.equipamentos.value.orEmpty(), atualId) { novo ->
            Posto.registarAlteracao(activity, postos.firstOrNull { it.id == atualId }?.nome ?: atualId, novo.nome)
            Posto.guardar(activity, novo.id)
            dismiss()
            // O posto mudou: volta-se à entrada, que já mostra o posto novo.
            activity.terminarSessao(porInatividade = false)
            activity.mostrarAviso(getString(R.string.posto_reconfigurado, novo.nome))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
