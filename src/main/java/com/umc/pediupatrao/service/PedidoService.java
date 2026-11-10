package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.util.UsuarioLogado;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {

    // status do pedido
    public static final String RECEBIDO = "RECEBIDO";
    public static final String EM_PREPARACAO = "EM_PREPARACAO";
    public static final String PRONTO = "PRONTO";
    public static final String SAIU_PARA_ENTREGA = "SAIU_PARA_ENTREGA";
    public static final String RETIRADO = "RETIRADO";
    public static final String FINALIZADO = "FINALIZADO";
    public static final String CANCELADO = "CANCELADO";

    public static final double DESCONTO_MAXIMO = 20.0;
    public static final int QUANTIDADE_MAXIMA_ITEM = 50;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    private DateTimeFormatter formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private Locale brasil = Locale.forLanguageTag("pt-BR");

    // ==========================================
    // REGISTRAR PEDIDO (entrada)
    // ==========================================
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE')")
    public Pedido criarPedido(String clienteId, String tipoEntrega, List<ItemPedido> itensInformados) {
        if (clienteId == null || clienteId.isBlank()) {
            throw new RegraNegocioException("Informe o cliente do pedido.");
        }
        Cliente cliente = clienteRepository.findById(clienteId).orElse(null);
        if (cliente == null) {
            throw new RegraNegocioException("Cliente não encontrado.");
        }
        if (!"ENTREGA".equals(tipoEntrega) && !"RETIRADA".equals(tipoEntrega)) {
            throw new RegraNegocioException("Tipo de entrega inválido. Use ENTREGA ou RETIRADA.");
        }
        if (itensInformados == null || itensInformados.isEmpty()) {
            throw new RegraNegocioException("O pedido precisa ter pelo menos um item.");
        }

        // monto um pedido novo do zero, assim nada que venha na requisicao
        // (status, total, desconto, datas...) e aproveitado
        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoEntrega(tipoEntrega);

        for (ItemPedido informado : itensInformados) {
            if (informado.getProdutoId() == null || informado.getProdutoId().isBlank()) {
                throw new RegraNegocioException("Item sem produto informado.");
            }
            if (informado.getQuantidade() <= 0) {
                throw new RegraNegocioException("A quantidade de cada item deve ser maior que zero.");
            }
            if (informado.getQuantidade() > QUANTIDADE_MAXIMA_ITEM) {
                throw new RegraNegocioException("Quantidade máxima por item é " + QUANTIDADE_MAXIMA_ITEM + ".");
            }

            Produto produto = produtoRepository.findById(informado.getProdutoId()).orElse(null);
            if (produto == null) {
                throw new RegraNegocioException("Produto não encontrado.");
            }
            if (produto.getAtivo() == null || !produto.getAtivo()) {
                throw new RegraNegocioException("O produto " + produto.getNome() + " não está ativo.");
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("O produto " + produto.getNome() + " está sem preço válido.");
            }

            // o preco vem do cadastro do produto, nao do que foi enviado
            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setQuantidade(informado.getQuantidade());
            item.setPrecoUnitario(produto.getPreco());
            item.setSubtotal(arredondar(produto.getPreco() * informado.getQuantidade()));
            pedido.getItens().add(item);
        }

        pedido.setPercentualDesconto(0);
        calcularValores(pedido);

        pedido.setStatus(RECEBIDO);
        pedido.setDataHoraEntrada(LocalDateTime.now());
        pedido.setUsuarioEntrada(UsuarioLogado.getUsername());

        Pedido salvo = pedidoRepository.save(pedido);

        List<CampoAlterado> campos = new ArrayList<>();
        campos.add(new CampoAlterado("cliente", "", salvo.getClienteNome()));
        campos.add(new CampoAlterado("tipoEntrega", "", salvo.getTipoEntrega()));
        campos.add(new CampoAlterado("itens", "", descreverItens(salvo)));
        campos.add(new CampoAlterado("valorTotal", "", formatarValor(salvo.getValorTotal())));
        campos.add(new CampoAlterado("status", "", RECEBIDO));
        campos.add(new CampoAlterado("dataHoraEntrada", "", formatarData(salvo.getDataHoraEntrada())));
        auditoriaService.registrar("INCLUSAO_PEDIDO", "Pedido", salvo.getId(), campos, null);

        return salvo;
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll(Sort.by(Sort.Direction.DESC, "dataHoraEntrada"));
    }

    public Pedido buscarPorId(String id) {
        Pedido pedido = pedidoRepository.findById(id).orElse(null);
        if (pedido == null) {
            throw new RegraNegocioException("Pedido não encontrado.");
        }
        return pedido;
    }

    // ==========================================
    // STATUS / TRANSICOES
    // ==========================================

    // Fluxo: RECEBIDO -> EM_PREPARACAO -> PRONTO -> SAIU_PARA_ENTREGA ou RETIRADO -> FINALIZADO
    // Retorna o proximo status permitido ou null se nao tiver mais nada pra fazer
    public String proximoStatus(Pedido pedido) {
        String status = pedido.getStatus();
        if (RECEBIDO.equals(status)) {
            return EM_PREPARACAO;
        }
        if (EM_PREPARACAO.equals(status)) {
            return PRONTO;
        }
        if (PRONTO.equals(status)) {
            if ("RETIRADA".equals(pedido.getTipoEntrega())) {
                return RETIRADO;
            }
            return SAIU_PARA_ENTREGA;
        }
        if (SAIU_PARA_ENTREGA.equals(status) || RETIRADO.equals(status)) {
            return FINALIZADO;
        }
        // FINALIZADO, CANCELADO ou status antigo: nao tem proximo
        return null;
    }

    // pedido ainda nao saiu da pizzaria (pode ter desconto e ser cancelado)
    public boolean antesDaSaida(Pedido pedido) {
        String status = pedido.getStatus();
        return RECEBIDO.equals(status) || EM_PREPARACAO.equals(status) || PRONTO.equals(status);
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido atualizarStatus(String id, String novoStatus) {
        Pedido pedido = buscarPorId(id);

        if (novoStatus == null || novoStatus.isBlank()) {
            throw new RegraNegocioException("Informe o novo status.");
        }
        novoStatus = novoStatus.trim().toUpperCase();
        String statusAtual = pedido.getStatus();

        if (CANCELADO.equals(statusAtual)) {
            throw new RegraNegocioException("Pedido cancelado não pode ter o status alterado.");
        }
        if (FINALIZADO.equals(statusAtual)) {
            throw new RegraNegocioException("Pedido finalizado não pode ter o status alterado.");
        }
        if (CANCELADO.equals(novoStatus)) {
            throw new RegraNegocioException("Para cancelar use a opção de cancelamento (exige justificativa).");
        }

        String permitido = proximoStatus(pedido);
        if (permitido == null || !permitido.equals(novoStatus)) {
            throw new RegraNegocioException("Transição inválida: " + statusAtual + " -> " + novoStatus
                    + ". O próximo status permitido é " + permitido + ".");
        }

        LocalDateTime agora = LocalDateTime.now();
        String usuario = UsuarioLogado.getUsername();

        List<CampoAlterado> campos = new ArrayList<>();
        campos.add(new CampoAlterado("status", statusAtual, novoStatus));

        pedido.setStatus(novoStatus);

        // saida e obrigatorio registrar data/hora e responsavel
        if (SAIU_PARA_ENTREGA.equals(novoStatus) || RETIRADO.equals(novoStatus)) {
            pedido.setDataHoraSaida(agora);
            pedido.setUsuarioSaida(usuario);
            campos.add(new CampoAlterado("dataHoraSaida", "", formatarData(agora)));
            campos.add(new CampoAlterado("usuarioSaida", "", usuario));
        }
        if (FINALIZADO.equals(novoStatus)) {
            pedido.setDataHoraFinalizacao(agora);
            pedido.setUsuarioFinalizacao(usuario);
            campos.add(new CampoAlterado("dataHoraFinalizacao", "", formatarData(agora)));
        }

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("ALTERACAO_STATUS_PEDIDO", "Pedido", salvo.getId(), campos, null);
        return salvo;
    }

    // ==========================================
    // DESCONTO (somente GERENTE)
    // ==========================================
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, Double percentual, String justificativa) {
        Pedido pedido = buscarPorId(id);

        if (percentual == null || percentual.isNaN()) {
            throw new RegraNegocioException("Informe o percentual de desconto.");
        }
        if (percentual <= 0) {
            throw new RegraNegocioException("O desconto deve ser maior que zero.");
        }
        if (percentual > DESCONTO_MAXIMO) {
            throw new RegraNegocioException("O desconto não pode passar de 20% do valor do pedido.");
        }
        if (CANCELADO.equals(pedido.getStatus())) {
            throw new RegraNegocioException("Pedido cancelado não pode receber desconto.");
        }
        if (!antesDaSaida(pedido)) {
            throw new RegraNegocioException("O desconto só pode ser aplicado antes da saída para entrega ou retirada.");
        }

        double percentualAnterior = pedido.getPercentualDesconto();
        double descontoAnterior = pedido.getValorDesconto();
        double totalAnterior = pedido.getValorTotal();

        // o percentual e sempre sobre o subtotal (se aplicar de novo, substitui o anterior)
        pedido.setPercentualDesconto(arredondar(percentual));
        calcularValores(pedido);
        pedido.setDataHoraDesconto(LocalDateTime.now());
        pedido.setUsuarioDesconto(UsuarioLogado.getUsername());

        Pedido salvo = pedidoRepository.save(pedido);

        List<CampoAlterado> campos = new ArrayList<>();
        campos.add(new CampoAlterado("percentualDesconto", formatarPercentual(percentualAnterior), formatarPercentual(salvo.getPercentualDesconto())));
        campos.add(new CampoAlterado("valorDesconto", formatarValor(descontoAnterior), formatarValor(salvo.getValorDesconto())));
        campos.add(new CampoAlterado("valorTotal", formatarValor(totalAnterior), formatarValor(salvo.getValorTotal())));
        auditoriaService.registrar("DESCONTO_PEDIDO", "Pedido", salvo.getId(), campos, textoOuNulo(justificativa));

        return salvo;
    }

    // ==========================================
    // CANCELAMENTO (somente GERENTE)
    // ==========================================
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        Pedido pedido = buscarPorId(id);

        if (justificativa == null || justificativa.trim().length() < 5) {
            throw new RegraNegocioException("Informe a justificativa do cancelamento (mínimo 5 caracteres).");
        }
        if (justificativa.length() > 500) {
            throw new RegraNegocioException("A justificativa pode ter no máximo 500 caracteres.");
        }
        if (CANCELADO.equals(pedido.getStatus())) {
            throw new RegraNegocioException("Este pedido já está cancelado.");
        }
        if (!antesDaSaida(pedido)) {
            throw new RegraNegocioException("Não é possível cancelar um pedido que já saiu para entrega, foi retirado ou finalizado.");
        }

        String statusAnterior = pedido.getStatus();
        LocalDateTime agora = LocalDateTime.now();

        // nao apaga nada do pedido, so muda o status e guarda quem cancelou
        pedido.setStatus(CANCELADO);
        pedido.setDataHoraCancelamento(agora);
        pedido.setUsuarioCancelamento(UsuarioLogado.getUsername());
        pedido.setJustificativaCancelamento(justificativa.trim());

        Pedido salvo = pedidoRepository.save(pedido);

        List<CampoAlterado> campos = new ArrayList<>();
        campos.add(new CampoAlterado("status", statusAnterior, CANCELADO));
        campos.add(new CampoAlterado("dataHoraCancelamento", "", formatarData(agora)));
        auditoriaService.registrar("CANCELAMENTO_PEDIDO", "Pedido", salvo.getId(), campos, justificativa.trim());

        return salvo;
    }

    // ==========================================
    // METODOS DE APOIO
    // ==========================================

    // soma os itens e aplica o desconto
    private void calcularValores(Pedido pedido) {
        double subtotal = 0;
        for (ItemPedido item : pedido.getItens()) {
            subtotal = subtotal + item.getSubtotal();
        }
        subtotal = arredondar(subtotal);
        double desconto = arredondar(subtotal * pedido.getPercentualDesconto() / 100);

        pedido.setSubtotal(subtotal);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(arredondar(subtotal - desconto));
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private String formatarValor(double valor) {
        return "R$ " + String.format(brasil, "%.2f", valor);
    }

    private String formatarPercentual(double valor) {
        return String.format(brasil, "%.2f", valor) + "%";
    }

    private String formatarData(LocalDateTime data) {
        if (data == null) {
            return "";
        }
        return data.format(formatoData);
    }

    private String descreverItens(Pedido pedido) {
        String texto = "";
        for (ItemPedido item : pedido.getItens()) {
            if (!texto.isEmpty()) {
                texto = texto + "; ";
            }
            texto = texto + item.getQuantidade() + "x " + item.getNomeProduto()
                    + " (" + formatarValor(item.getPrecoUnitario()) + ")";
        }
        return texto;
    }

    private String textoOuNulo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }
}
