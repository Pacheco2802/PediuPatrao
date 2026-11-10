package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private PedidoService pedidoService;

    @BeforeEach
    public void logarComoGerente() {
        logar("gerente01", "GERENTE");
    }

    @AfterEach
    public void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void logar(String usuario, String perfil) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    // monta um pedido de 100 reais no status informado
    private Pedido pedidoNoStatus(String status) {
        Pedido p = new Pedido();
        p.setId("p1");
        p.setStatus(status);
        p.setTipoEntrega("ENTREGA");
        ItemPedido item = new ItemPedido();
        item.setNomeProduto("Pizza");
        item.setQuantidade(2);
        item.setPrecoUnitario(50.0);
        item.setSubtotal(100.0);
        p.getItens().add(item);
        p.setSubtotal(100.0);
        p.setValorTotal(100.0);
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(p));
        return p;
    }

    private void salvarDevolveOMesmo() {
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    public void criarPedidoCalculaTotalPeloPrecoDoProduto() {
        logar("atendente01", "ATENDENTE");

        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Maria");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente));

        Produto pizza = new Produto();
        pizza.setId("pz");
        pizza.setNome("Pizza Calabresa");
        pizza.setPreco(45.0);
        pizza.setAtivo(true);
        when(produtoRepository.findById("pz")).thenReturn(Optional.of(pizza));
        salvarDevolveOMesmo();

        // tenta mandar um preco falso no item
        ItemPedido item = new ItemPedido();
        item.setProdutoId("pz");
        item.setQuantidade(2);
        item.setPrecoUnitario(0.01);
        List<ItemPedido> itens = new ArrayList<>();
        itens.add(item);

        Pedido pedido = pedidoService.criarPedido("c1", "ENTREGA", itens);

        assertEquals(90.0, pedido.getValorTotal());
        assertEquals(45.0, pedido.getItens().get(0).getPrecoUnitario());
        assertEquals("RECEBIDO", pedido.getStatus());
        assertEquals("atendente01", pedido.getUsuarioEntrada());
        assertNotNull(pedido.getDataHoraEntrada());
        verify(auditoriaService).registrar(eq("INCLUSAO_PEDIDO"), eq("Pedido"), any(), any(), any());
    }

    @Test
    public void naoCriaPedidoComQuantidadeZero() {
        Cliente cliente = new Cliente();
        cliente.setId("c1");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente));

        ItemPedido item = new ItemPedido();
        item.setProdutoId("pz");
        item.setQuantidade(0);
        List<ItemPedido> itens = new ArrayList<>();
        itens.add(item);

        assertThrows(RegraNegocioException.class, () -> pedidoService.criarPedido("c1", "ENTREGA", itens));
    }

    @Test
    public void naoPermiteFinalizarPedidoEmPreparacao() {
        pedidoNoStatus("EM_PREPARACAO");
        assertThrows(RegraNegocioException.class, () -> pedidoService.atualizarStatus("p1", "FINALIZADO"));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    public void fluxoNormalAteASaida() {
        Pedido p = pedidoNoStatus("PRONTO");
        salvarDevolveOMesmo();

        pedidoService.atualizarStatus("p1", "SAIU_PARA_ENTREGA");

        assertEquals("SAIU_PARA_ENTREGA", p.getStatus());
        assertEquals("gerente01", p.getUsuarioSaida());
        assertNotNull(p.getDataHoraSaida());
    }

    @Test
    public void pedidoDeRetiradaNaoSaiParaEntrega() {
        Pedido p = pedidoNoStatus("PRONTO");
        p.setTipoEntrega("RETIRADA");
        assertThrows(RegraNegocioException.class, () -> pedidoService.atualizarStatus("p1", "SAIU_PARA_ENTREGA"));
    }

    @Test
    public void pedidoCanceladoNaoMudaStatus() {
        pedidoNoStatus("CANCELADO");
        assertThrows(RegraNegocioException.class, () -> pedidoService.atualizarStatus("p1", "EM_PREPARACAO"));
    }

    @Test
    public void descontoDeDezPorCento() {
        Pedido p = pedidoNoStatus("RECEBIDO");
        salvarDevolveOMesmo();

        pedidoService.aplicarDesconto("p1", 10.0, "cliente antigo");

        assertEquals(10.0, p.getValorDesconto());
        assertEquals(90.0, p.getValorTotal());
        verify(auditoriaService).registrar(eq("DESCONTO_PEDIDO"), eq("Pedido"), eq("p1"), any(), eq("cliente antigo"));
    }

    @Test
    public void descontoMaiorQueVintePorCentoNaoPode() {
        pedidoNoStatus("RECEBIDO");
        assertThrows(RegraNegocioException.class, () -> pedidoService.aplicarDesconto("p1", 20.5, null));
    }

    @Test
    public void descontoDepoisDaSaidaNaoPode() {
        pedidoNoStatus("SAIU_PARA_ENTREGA");
        assertThrows(RegraNegocioException.class, () -> pedidoService.aplicarDesconto("p1", 5.0, null));
    }

    @Test
    public void cancelamentoSemJustificativaNaoPode() {
        pedidoNoStatus("RECEBIDO");
        assertThrows(RegraNegocioException.class, () -> pedidoService.cancelar("p1", ""));
    }

    @Test
    public void cancelamentoComJustificativa() {
        Pedido p = pedidoNoStatus("EM_PREPARACAO");
        salvarDevolveOMesmo();

        pedidoService.cancelar("p1", "Cliente desistiu do pedido");

        assertEquals("CANCELADO", p.getStatus());
        assertEquals("gerente01", p.getUsuarioCancelamento());
        assertEquals("Cliente desistiu do pedido", p.getJustificativaCancelamento());
        // itens e valores continuam guardados
        assertEquals(1, p.getItens().size());
        assertEquals(100.0, p.getValorTotal());
    }

    @Test
    public void naoCancelaPedidoQueJaSaiu() {
        pedidoNoStatus("RETIRADO");
        assertThrows(RegraNegocioException.class, () -> pedidoService.cancelar("p1", "Cliente desistiu"));
    }
}
