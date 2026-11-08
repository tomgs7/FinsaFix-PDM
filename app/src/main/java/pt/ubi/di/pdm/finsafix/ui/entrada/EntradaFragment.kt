package pt.ubi.di.pdm.finsafix.ui.entrada

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.divider.MaterialDividerItemDecoration
import pt.ubi.di.pdm.finsafix.MainActivity
import pt.ubi.di.pdm.finsafix.R
import pt.ubi.di.pdm.finsafix.data.Equipamento
import pt.ubi.di.pdm.finsafix.data.Pessoa
import pt.ubi.di.pdm.finsafix.data.Secao
import pt.ubi.di.pdm.finsafix.databinding.FragmentEntradaBinding
import pt.ubi.di.pdm.finsafix.ui.comum.EstiloAvaria
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarErrosDe
import pt.ubi.di.pdm.finsafix.ui.comum.mostrarMensagem
import pt.ubi.di.pdm.finsafix.ui.comum.pedirPin
import pt.ubi.di.pdm.finsafix.util.Datas
import pt.ubi.di.pdm.finsafix.util.Sessao

/** Ecrã de entrada do tablet: quem está ao serviço toca no seu nome. */
class EntradaFragment : Fragment() {

    private var _binding: FragmentEntradaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EntradaViewModel by viewModels()
    private val adapter = PessoaAdapter { pessoa -> entrar(pessoa) }

    private var postoAtual: Secao? = null
    private var pessoasDoPosto: List<Pessoa> = emptyList()
    private var equipamentos: List<Equipamento> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEntradaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Sessao.terminar()

        binding.quemEsta.listaPessoas.adapter = adapter
        binding.quemEsta.listaPessoas.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL)
        )
        // O ecrã de entrada fica aberto durante horas: sempre que o relógio muda (uma vez por minuto),
        // atualizam-se a data, o turno atual e a etiqueta "Turno atual" das pessoas.
        mostrarDataETurno()
        binding.hora.relogio.doAfterTextChanged { mostrarDataETurno() }
        binding.cabecalho.botaoChefia.setOnClickListener { pedirPinChefe() }
        binding.quemEsta.botaoCracha.setOnClickListener { simularCracha() }
        // O tablet tem o “Mais” ao lado do menu; o telemóvel não tem menu na entrada
        binding.quemEsta.botaoMaisEntrada.isVisible = resources.getBoolean(R.bool.entrada_botao_mais)
        binding.quemEsta.botaoMaisEntrada.setOnClickListener { (requireActivity() as MainActivity).abrirMais() }

        viewModel.posto.observe(viewLifecycleOwner) { posto ->
            postoAtual = posto
            binding.infoPosto.textoPosto.text = posto?.nome ?: ""
            mostrarDescricaoDoPosto()
        }
        viewModel.equipamentos.observe(viewLifecycleOwner) {
            equipamentos = it
            mostrarDescricaoDoPosto()
        }
        viewModel.pessoas.observe(viewLifecycleOwner) { pessoas ->
            pessoasDoPosto = pessoas.sortedByDescending { it.turno == Datas.turnoAtual() } // quem está no turno primeiro
            adapter.submitList(pessoasDoPosto)
            binding.quemEsta.textoSemPessoas.isVisible = pessoas.isEmpty()
            // Na 1.ª execução a lista está vazia até os dados de demonstração carregarem
            binding.quemEsta.textoSemPessoas.setText(if (viewModel.dadosDemoFalharam()) R.string.erro_ler_ficheiro else R.string.entrada_a_carregar)
        }
        mostrarErrosDe(viewModel)
    }

    /** Os equipamentos da secção; numa oficina (sem equipamentos) o que ela trata. */
    private fun mostrarDescricaoDoPosto() {
        val binding = _binding ?: return
        val oficina = when (postoAtual?.id) {
            "OFI-MEC" -> getString(R.string.entrada_oficina_mecanica)
            "OFI-ELE" -> getString(R.string.entrada_oficina_eletrica)
            else -> null
        }
        binding.infoPosto.textoEquipamentos.text = oficina ?: equipamentos.joinToString(EstiloAvaria.SEPARADOR) { it.nome }
        binding.infoPosto.textoEquipamentos.isVisible = oficina != null || equipamentos.isNotEmpty()
    }

    @SuppressLint("NotifyDataSetChanged") // a etiqueta "Turno atual" de cada pessoa também muda
    private fun mostrarDataETurno() {
        val binding = _binding ?: return
        binding.hora.textoData.text = Datas.hojePorExtenso()
        val turno = Datas.turnoAtual()
        val texto = getString(
            R.string.entrada_turno_atual, getString(Datas.nomeTurno(turno)), getString(Datas.horarioTurno(turno))
        )
        if (binding.infoPosto.textoTurno.text.toString() != texto) {
            binding.infoPosto.textoTurno.text = texto
            adapter.notifyDataSetChanged()
        }
    }

    private fun entrar(pessoa: Pessoa) {
        Sessao.iniciar(pessoa, postoAtual?.nome ?: "")
        val primeiroNome = pessoa.nomeCurto()
        val mensagem = when (pessoa.papel) {
            Pessoa.PAPEL_CHEFE -> getString(R.string.entrada_modo_chefia, pessoa.nome, postoAtual?.nome ?: "")
            Pessoa.PAPEL_ENGENHEIRO -> getString(R.string.entrada_modo_engenharia, pessoa.nome)
            else -> getString(
                R.string.entrada_boas_vindas,
                primeiroNome,
                Datas.hora(System.currentTimeMillis()),
                getString(Datas.nomeTurno(pessoa.turno))
            )
        }
        mostrarMensagem(mensagem)

        when (pessoa.papel) {
            Pessoa.PAPEL_OPERADOR -> findNavController().navigate(R.id.acao_entrada_para_inicio)
            Pessoa.PAPEL_TECNICO -> findNavController().navigate(R.id.acao_entrada_para_tecnico)
            Pessoa.PAPEL_CHEFE -> findNavController().navigate(
                if (pessoa.eChefeDeOficina()) R.id.acao_entrada_para_oficina else R.id.acao_entrada_para_seccao
            )
            Pessoa.PAPEL_ENGENHEIRO -> findNavController().navigate(R.id.acao_entrada_para_engenharia)
        }
    }

    /** Simula o crachá NFC: entra como uma pessoa do turno atual (a primeira da lista). */
    private fun simularCracha() {
        val pessoa = pessoasDoPosto.firstOrNull { it.turno == Datas.turnoAtual() }
        if (pessoa == null) mostrarMensagem(R.string.entrada_sem_pessoas) else entrar(pessoa)
    }

    /** Cadeado: o chefe entra com o PIN pessoal (só no tablet do seu posto); a engenharia entra em qualquer tablet. */
    private fun pedirPinChefe() = pedirPin(R.string.pin_chefe_titulo) { pin, _, _ ->
        when (val resultado = viewModel.entrarComoChefia(pin)) {
            is EntradaViewModel.Resultado.Erro -> resultado.mensagem
            is EntradaViewModel.Resultado.Certo -> {
                entrar(resultado.valor)
                null
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
