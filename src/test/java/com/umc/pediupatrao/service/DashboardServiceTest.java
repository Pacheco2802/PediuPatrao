package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDTO.Indicadores;
import com.umc.pediupatrao.dto.DashboardDTO.PedidoResumo;
import com.umc.pediupatrao.dto.DashboardDTO.ProdutoVendido;
import com.umc.pediupatrao.dto.DashboardDTO.StatusPedidos;
import com.umc.pediupatrao.dto.DashboardDTO.VendaCategoria;
import com.umc.pediupatrao.dto.DashboardDTO.VendaDia;
import com.umc.pediupatrao.dto.DashboardDTO.VendaHora;
import com.umc.pediupatrao.dto.FiltroDashboard;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

// Testa as contas do dashboard sem precisar do MongoDB (repositorios simulados)
@ExtendWith(MockitoExtension.class)
public class DashboardServiceTest {

    private static final LocalDate DIA1 = LocalDate.of(2026, 11, 9);
    private static final LocalDate DIA2 = LocalDate.of(2026, 11, 10);

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @InjectMocks
    private DashboardService service;

    private List<Pedido> pedidosAtuais;
    private List<Pedido> pedidosAnteriores;

    private final FiltroDashboard filtro = FiltroDashboard.criar("2026-11-09", "2026-11-10", null, null);

    @BeforeEach
    public void preparar() {
        // Periodo consultado (09 e 10/11):
        //   dia 09 19h - FINALIZADO  - ENTREGA  - 2 calabresa (90,00)            = 90,00
        //   dia 09 20h - CANCELADO   - ENTREGA  - 1 mussarela (42,00)            = 42,00 (nao conta)
        //   dia 10 19h - EM_PREPARACAO - RETIRADA - 1 calabresa + 2 refri (69,00), 10% desc = 62,10
        //   dia 10 12h - FINALIZADO  - ENTREGA  - 3 refri (36,00)                = 36,00
        pedidosAtuais = new ArrayList<>();
        pedidosAtuais.add(pedido("p1", DIA1.atTime(19, 10), "FINALIZADO", "ENTREGA", 0,
                item("calabresa", "Pizza Calabresa", 2, 45.00)));
        pedidosAtuais.add(pedido("p2", DIA1.atTime(20, 0), "CANCELADO", "ENTREGA", 0,
                item("mussarela", "Pizza Mussarela", 1, 42.00)));
        pedidosAtuais.add(pedido("p3", DIA2.atTime(19, 30), "EM_PREPARACAO", "RETIRADA", 10,
                item("calabresa", "Pizza Calabresa", 1, 45.00), item("refri", "Refrigerante 2L", 2, 12.00)));
        pedidosAtuais.add(pedido("p4", DIA2.atTime(12, 5), "FINALIZADO", "ENTREGA", 0,
                item("refri", "Refrigerante 2L", 3, 12.00)));

        // Periodo anterior (07 e 08/11): um pedido de 100,00 e um cancelado
        pedidosAnteriores = new ArrayList<>();
        pedidosAnteriores.add(pedido("a1", LocalDate.of(2026, 11, 8).atTime(21, 0), "FINALIZADO", "ENTREGA", 0,
                item("calabresa", "Pizza Calabresa", 1, 100.00)));
        pedidosAnteriores.add(pedido("a2", LocalDate.of(2026, 11, 7).atTime(21, 0), "CANCELADO", "ENTREGA", 0,
                item("calabresa", "Pizza Calabresa", 1, 100.00)));

        // o repositorio simulado devolve a lista conforme o periodo pedido
        lenient().when(pedidoRepository.buscarPorPeriodo(any(), any())).thenAnswer(chamada -> {
            LocalDateTime inicio = chamada.getArgument(0);
            if (inicio.equals(DIA1.atStartOfDay())) {
                return pedidosAtuais;
            }
            if (inicio.equals(LocalDate.of(2026, 11, 7).atStartOfDay())) {
                return pedidosAnteriores;
            }
            return new ArrayList<Pedido>();
        });

        lenient().when(produtoRepository.findAll()).thenReturn(List.of(
                produto("calabresa", "Pizza"), produto("mussarela", "Pizza"), produto("refri", "Bebida")));
    }

    // ------------------------------------------------------------
    // RF01 - indicadores
    // ------------------------------------------------------------
    @Test
    public void indicadoresNaoContamPedidoCanceladoNoFaturamento() {
        Indicadores i = service.indicadores(filtro);

        assertEquals(4, i.totalPedidos());
        assertEquals(3, i.pedidosValidos());
        assertEquals(1, i.pedidosCancelados());
        assertEquals(25.0, i.taxaCancelamento());
        assertEquals(188.10, i.faturamento(), 0.001);   // 90,00 + 62,10 + 36,00
        assertEquals(62.70, i.ticketMedio(), 0.001);    // 188,10 / 3
        assertEquals(6.90, i.totalDescontos(), 0.001);
        assertEquals(8, i.itensVendidos());             // 2 + 3 + 3
        assertEquals(2, i.pedidosFinalizados());
        assertEquals(1, i.pedidosEmAndamento());
    }

    @Test
    public void indicadoresComparamComPeriodoAnteriorDoMesmoTamanho() {
        Indicadores i = service.indicadores(filtro);

        assertEquals(2, i.periodo().dias());
        assertEquals(100.00, i.faturamentoPeriodoAnterior(), 0.001);
        assertEquals(1, i.pedidosPeriodoAnterior());
        assertEquals(88.10, i.variacaoFaturamento(), 0.001); // (188,10 - 100) / 100
    }

    @Test
    public void semVendasNoPeriodoAnteriorVariacaoFicaNula() {
        pedidosAnteriores.clear();

        Indicadores i = service.indicadores(filtro);

        assertEquals(0, i.faturamentoPeriodoAnterior());
        assertNull(i.variacaoFaturamento());
    }

    @Test
    public void periodoSemPedidosDevolveTudoZeradoSemErro() {
        pedidosAtuais.clear();
        pedidosAnteriores.clear();

        Indicadores i = service.indicadores(filtro);

        assertEquals(0, i.totalPedidos());
        assertEquals(0, i.faturamento());
        assertEquals(0, i.ticketMedio());
        assertEquals(0, i.taxaCancelamento());
        assertTrue(service.produtosMaisVendidos(filtro, null).isEmpty());
        assertTrue(service.vendasPorCategoria(filtro).isEmpty());
        assertTrue(service.pedidosRecentes(filtro, null).isEmpty());
    }

    // ------------------------------------------------------------
    // RF05 - filtros
    // ------------------------------------------------------------
    @Test
    public void filtroPorTipoDeEntrega() {
        Indicadores i = service.indicadores(FiltroDashboard.criar("2026-11-09", "2026-11-10", null, "retirada"));

        assertEquals(1, i.totalPedidos());
        assertEquals(62.10, i.faturamento(), 0.001);
    }

    @Test
    public void filtroPorStatusCanceladoNaoTemFaturamento() {
        Indicadores i = service.indicadores(FiltroDashboard.criar("2026-11-09", "2026-11-10", "CANCELADO", null));

        assertEquals(1, i.totalPedidos());
        assertEquals(1, i.pedidosCancelados());
        assertEquals(0, i.faturamento());
        assertEquals(100.0, i.taxaCancelamento());
    }

    @Test
    public void semDatasUsaUltimosSeteDias() {
        FiltroDashboard padrao = FiltroDashboard.criar(null, " ", null, null);

        assertEquals(LocalDate.now(), padrao.dataFim());
        assertEquals(LocalDate.now().minusDays(6), padrao.dataInicio());
        assertEquals(7, padrao.quantidadeDias());
    }

    @Test
    public void filtrosInvalidosSaoRecusados() {
        assertThrows(RegraNegocioException.class, () -> FiltroDashboard.criar("10/11/2026", null, null, null));
        assertThrows(RegraNegocioException.class, () -> FiltroDashboard.criar("2026-11-10", "2026-11-09", null, null));
        assertThrows(RegraNegocioException.class, () -> FiltroDashboard.criar("2024-01-01", "2026-11-09", null, null));
        assertThrows(RegraNegocioException.class, () -> FiltroDashboard.criar(null, null, "ENTREGUE", null));
        assertThrows(RegraNegocioException.class, () -> FiltroDashboard.criar(null, null, null, "MOTOBOY"));
    }

    // ------------------------------------------------------------
    // RF02 - graficos
    // ------------------------------------------------------------
    @Test
    public void vendasPorDiaTrazTodosOsDiasInclusiveSemVenda() {
        List<VendaDia> dias = service.vendasPorDia(FiltroDashboard.criar("2026-11-09", "2026-11-11", null, null));

        assertEquals(3, dias.size());
        assertEquals(DIA1, dias.get(0).data());
        assertEquals(1, dias.get(0).pedidos());                // o cancelado nao conta
        assertEquals(90.00, dias.get(0).faturamento(), 0.001);
        assertEquals(2, dias.get(1).pedidos());
        assertEquals(98.10, dias.get(1).faturamento(), 0.001);
        assertEquals(0, dias.get(2).pedidos());                // dia 11 sem venda aparece zerado
        assertEquals(0, dias.get(2).faturamento());
    }

    @Test
    public void vendasPorHoraTrazAs24Horas() {
        List<VendaHora> horas = service.vendasPorHora(filtro);

        assertEquals(24, horas.size());
        assertEquals(2, horas.get(19).pedidos());
        assertEquals(152.10, horas.get(19).faturamento(), 0.001);
        assertEquals(1, horas.get(12).pedidos());
        assertEquals(0, horas.get(20).pedidos());              // as 20h so teve o cancelado
    }

    @Test
    public void vendasPorCategoriaOrdenadaPorValor() {
        List<VendaCategoria> categorias = service.vendasPorCategoria(filtro);

        assertEquals(2, categorias.size());
        assertEquals("Pizza", categorias.get(0).categoria());
        assertEquals(135.00, categorias.get(0).faturamento(), 0.001);
        assertEquals(3, categorias.get(0).quantidade());
        assertEquals("Bebida", categorias.get(1).categoria());
        assertEquals(60.00, categorias.get(1).faturamento(), 0.001);
        assertEquals(100.0, categorias.get(0).percentual() + categorias.get(1).percentual(), 0.011);
    }

    @Test
    public void produtoQueNaoExisteMaisFicaSemCategoria() {
        pedidosAtuais.add(pedido("p5", DIA2.atTime(21, 0), "FINALIZADO", "ENTREGA", 0,
                item("excluido", "Esfiha Antiga", 1, 6.00)));

        List<VendaCategoria> categorias = service.vendasPorCategoria(filtro);

        assertTrue(categorias.stream().anyMatch(c -> c.categoria().equals(DashboardService.SEM_CATEGORIA)));
    }

    // ------------------------------------------------------------
    // RF03 - situacao dos pedidos
    // ------------------------------------------------------------
    @Test
    public void pedidosPorStatusTrazTodosOsStatusNaOrdemDoFluxo() {
        List<StatusPedidos> status = service.pedidosPorStatus(filtro);

        assertEquals(7, status.size());
        assertEquals("RECEBIDO", status.get(0).status());
        assertEquals(0, status.get(0).quantidade());
        assertEquals("EM_PREPARACAO", status.get(1).status());
        assertEquals(1, status.get(1).quantidade());
        assertEquals("FINALIZADO", status.get(5).status());
        assertEquals(2, status.get(5).quantidade());
        assertEquals(50.0, status.get(5).percentual());
        assertEquals("CANCELADO", status.get(6).status());
        assertEquals(1, status.get(6).quantidade());           // aqui o cancelado aparece
    }

    @Test
    public void pedidosRecentesDoMaisNovoParaOMaisAntigo() {
        List<PedidoResumo> recentes = service.pedidosRecentes(filtro, 2);

        assertEquals(2, recentes.size());
        assertEquals("p3", recentes.get(0).id());
        assertEquals("p4", recentes.get(1).id());
        assertEquals(3, recentes.get(0).quantidadeItens());
    }

    @Test
    public void pedidoSemDataOuSemItensNaoDerrubaODashboard() {
        Pedido semData = pedido("x1", DIA2.atTime(10, 0), "FINALIZADO", "ENTREGA", 0);
        semData.setDataHoraEntrada(null);
        Pedido semItens = pedido("x2", DIA2.atTime(10, 0), "STATUS_ANTIGO", "ENTREGA", 0);
        semItens.setItens(null);
        pedidosAtuais.add(semData);
        pedidosAtuais.add(semItens);

        assertDoesNotThrow(() -> service.indicadores(filtro));
        assertDoesNotThrow(() -> service.produtosMaisVendidos(filtro, null));
        assertEquals(7, service.pedidosPorStatus(filtro).size());
        assertEquals(5, service.indicadores(filtro).totalPedidos()); // o sem data fica de fora
    }

    // ------------------------------------------------------------
    // RF04 - produtos mais vendidos
    // ------------------------------------------------------------
    @Test
    public void rankingDeProdutosPorQuantidade() {
        List<ProdutoVendido> ranking = service.produtosMaisVendidos(filtro, null);

        // refri: 2 + 3 = 5 | calabresa: 2 + 1 = 3 | mussarela so no cancelado, nao entra
        assertEquals(2, ranking.size());
        assertEquals(1, ranking.get(0).posicao());
        assertEquals("Refrigerante 2L", ranking.get(0).nome());
        assertEquals("Bebida", ranking.get(0).categoria());
        assertEquals(5, ranking.get(0).quantidade());
        assertEquals(60.00, ranking.get(0).faturamento(), 0.001);
        assertEquals(62.5, ranking.get(0).percentual());
        assertEquals("Pizza Calabresa", ranking.get(1).nome());
        assertEquals(3, ranking.get(1).quantidade());
    }

    @Test
    public void rankingRespeitaOLimite() {
        assertEquals(1, service.produtosMaisVendidos(filtro, 1).size());
        assertThrows(RegraNegocioException.class, () -> service.produtosMaisVendidos(filtro, 0));
        assertThrows(RegraNegocioException.class, () -> service.produtosMaisVendidos(filtro, 51));
    }

    // ------------------------------------------------------------
    // apoio
    // ------------------------------------------------------------
    private Pedido pedido(String id, LocalDateTime entrada, String status, String tipo,
            double percentualDesconto, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setClienteNome("Cliente " + id);
        p.setDataHoraEntrada(entrada);
        p.setStatus(status);
        p.setTipoEntrega(tipo);
        double subtotal = 0;
        for (ItemPedido item : itens) {
            p.getItens().add(item);
            subtotal = subtotal + item.getSubtotal();
        }
        double desconto = Math.round(subtotal * percentualDesconto) / 100.0;
        p.setSubtotal(subtotal);
        p.setPercentualDesconto(percentualDesconto);
        p.setValorDesconto(desconto);
        p.setValorTotal(subtotal - desconto);
        return p;
    }

    private ItemPedido item(String produtoId, String nome, int quantidade, double preco) {
        ItemPedido item = new ItemPedido();
        item.setProdutoId(produtoId);
        item.setNomeProduto(nome);
        item.setQuantidade(quantidade);
        item.setPrecoUnitario(preco);
        item.setSubtotal(quantidade * preco);
        return item;
    }

    private Produto produto(String id, String tipo) {
        Produto p = new Produto();
        p.setId(id);
        p.setTipo(tipo);
        return p;
    }
}
