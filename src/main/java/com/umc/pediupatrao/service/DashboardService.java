package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDTO.Indicadores;
import com.umc.pediupatrao.dto.DashboardDTO.OpcoesFiltro;
import com.umc.pediupatrao.dto.DashboardDTO.PedidoResumo;
import com.umc.pediupatrao.dto.DashboardDTO.Periodo;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

// Regras do dashboard administrativo.
//
// Regra principal: pedido CANCELADO nao e venda. Ele aparece na contagem de pedidos
// e na situacao dos pedidos, mas fica fora do faturamento, do ticket medio,
// dos graficos de venda e do ranking de produtos.
//
// O periodo e sempre aplicado sobre a data/hora de ENTRADA do pedido.
@Service
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
public class DashboardService {

    public static final int LIMITE_PADRAO = 5;
    public static final int LIMITE_MAXIMO = 50;
    public static final String SEM_CATEGORIA = "Sem categoria";

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    // ==========================================
    // RF01 - INDICADORES GERAIS
    // ==========================================
    public Indicadores indicadores(FiltroDashboard filtro) {
        List<Pedido> pedidos = buscarPedidos(filtro);
        List<Pedido> validos = somenteValidos(pedidos);

        long cancelados = pedidos.size() - validos.size();
        long finalizados = 0;
        long itens = 0;
        double faturamento = 0;
        double descontos = 0;

        for (Pedido p : validos) {
            faturamento = faturamento + p.getValorTotal();
            descontos = descontos + p.getValorDesconto();
            if (PedidoService.FINALIZADO.equals(p.getStatus())) {
                finalizados++;
            }
            for (ItemPedido item : itensDe(p)) {
                itens = itens + item.getQuantidade();
            }
        }

        double ticketMedio = validos.isEmpty() ? 0 : faturamento / validos.size();
        double taxaCancelamento = percentual(cancelados, pedidos.size());

        // tendencia: compara com o periodo anterior do mesmo tamanho
        List<Pedido> anteriores = somenteValidos(buscarPedidos(filtro.periodoAnterior()));
        double faturamentoAnterior = 0;
        for (Pedido p : anteriores) {
            faturamentoAnterior = faturamentoAnterior + p.getValorTotal();
        }
        // sem venda no periodo anterior nao da pra calcular variacao (divisao por zero)
        Double variacao = null;
        if (faturamentoAnterior > 0) {
            variacao = arredondar((faturamento - faturamentoAnterior) / faturamentoAnterior * 100);
        }

        return new Indicadores(
                periodo(filtro),
                pedidos.size(),
                validos.size(),
                validos.size() - finalizados,
                finalizados,
                cancelados,
                taxaCancelamento,
                arredondar(faturamento),
                arredondar(ticketMedio),
                arredondar(descontos),
                itens,
                arredondar(faturamentoAnterior),
                anteriores.size(),
                variacao,
                LocalDateTime.now());
    }

    // ==========================================
    // RF02 - ANALISE DE VENDAS (GRAFICOS)
    // ==========================================

    // um ponto para cada dia do periodo, inclusive os dias sem venda (com zero),
    // senao o grafico de linha "pula" os dias vazios e engana a leitura
    public List<VendaDia> vendasPorDia(FiltroDashboard filtro) {
        Map<LocalDate, double[]> porDia = new LinkedHashMap<>();
        for (LocalDate dia = filtro.dataInicio(); !dia.isAfter(filtro.dataFim()); dia = dia.plusDays(1)) {
            porDia.put(dia, new double[2]);
        }

        for (Pedido p : somenteValidos(buscarPedidos(filtro))) {
            double[] acumulado = porDia.get(p.getDataHoraEntrada().toLocalDate());
            if (acumulado != null) {
                acumulado[0] = acumulado[0] + 1;
                acumulado[1] = acumulado[1] + p.getValorTotal();
            }
        }

        List<VendaDia> resultado = new ArrayList<>();
        for (Map.Entry<LocalDate, double[]> e : porDia.entrySet()) {
            resultado.add(new VendaDia(e.getKey(), (long) e.getValue()[0], arredondar(e.getValue()[1])));
        }
        return resultado;
    }

    // sempre devolve as 24 horas (0 a 23) para mostrar os horarios de pico
    public List<VendaHora> vendasPorHora(FiltroDashboard filtro) {
        long[] quantidade = new long[24];
        double[] valor = new double[24];

        for (Pedido p : somenteValidos(buscarPedidos(filtro))) {
            int hora = p.getDataHoraEntrada().getHour();
            quantidade[hora]++;
            valor[hora] = valor[hora] + p.getValorTotal();
        }

        List<VendaHora> resultado = new ArrayList<>();
        for (int hora = 0; hora < 24; hora++) {
            resultado.add(new VendaHora(hora, quantidade[hora], arredondar(valor[hora])));
        }
        return resultado;
    }

    // vendas por tipo de produto (Pizza, Bebida...). O valor aqui e a soma dos itens,
    // antes do desconto do pedido, porque o desconto e do pedido e nao do item.
    public List<VendaCategoria> vendasPorCategoria(FiltroDashboard filtro) {
        Map<String, String> categoriaPorProduto = categoriasDosProdutos();
        Map<String, double[]> porCategoria = new HashMap<>();
        double total = 0;

        for (Pedido p : somenteValidos(buscarPedidos(filtro))) {
            for (ItemPedido item : itensDe(p)) {
                String categoria = categoriaPorProduto.getOrDefault(item.getProdutoId(), SEM_CATEGORIA);
                double[] acumulado = porCategoria.computeIfAbsent(categoria, c -> new double[2]);
                acumulado[0] = acumulado[0] + item.getQuantidade();
                acumulado[1] = acumulado[1] + item.getSubtotal();
                total = total + item.getSubtotal();
            }
        }

        List<VendaCategoria> resultado = new ArrayList<>();
        for (Map.Entry<String, double[]> e : porCategoria.entrySet()) {
            resultado.add(new VendaCategoria(e.getKey(), (long) e.getValue()[0],
                    arredondar(e.getValue()[1]), percentual(e.getValue()[1], total)));
        }
        resultado.sort(Comparator.comparingDouble(VendaCategoria::faturamento).reversed()
                .thenComparing(VendaCategoria::categoria));
        return resultado;
    }

    // ==========================================
    // RF03 - SITUACAO DOS PEDIDOS
    // ==========================================

    // devolve todos os status na ordem do fluxo, mesmo os que estao zerados
    public List<StatusPedidos> pedidosPorStatus(FiltroDashboard filtro) {
        List<Pedido> pedidos = buscarPedidos(filtro);

        Map<String, double[]> porStatus = new LinkedHashMap<>();
        for (String status : FiltroDashboard.STATUS_VALIDOS) {
            porStatus.put(status, new double[2]);
        }
        long contados = 0;
        for (Pedido p : pedidos) {
            double[] acumulado = porStatus.get(p.getStatus());
            // status antigo/desconhecido no banco: ignora em vez de quebrar a tela
            if (acumulado != null) {
                acumulado[0] = acumulado[0] + 1;
                acumulado[1] = acumulado[1] + p.getValorTotal();
                contados++;
            }
        }

        List<StatusPedidos> resultado = new ArrayList<>();
        for (Map.Entry<String, double[]> e : porStatus.entrySet()) {
            resultado.add(new StatusPedidos(e.getKey(), (long) e.getValue()[0],
                    arredondar(e.getValue()[1]), percentual(e.getValue()[0], contados)));
        }
        return resultado;
    }

    // ultimos pedidos do periodo (mais novo primeiro)
    public List<PedidoResumo> pedidosRecentes(FiltroDashboard filtro, Integer limite) {
        int maximo = validarLimite(limite, 10);
        List<Pedido> pedidos = buscarPedidos(filtro);
        pedidos.sort(Comparator.comparing(Pedido::getDataHoraEntrada).reversed());

        List<PedidoResumo> resultado = new ArrayList<>();
        for (Pedido p : pedidos) {
            if (resultado.size() == maximo) {
                break;
            }
            int itens = 0;
            for (ItemPedido item : itensDe(p)) {
                itens = itens + item.getQuantidade();
            }
            resultado.add(new PedidoResumo(p.getId(), p.getClienteNome(), p.getTipoEntrega(), p.getStatus(),
                    itens, p.getValorTotal(), p.getDataHoraEntrada()));
        }
        return resultado;
    }

    // ==========================================
    // RF04 - PRODUTOS MAIS VENDIDOS
    // ==========================================

    // ranking por quantidade vendida (empate: maior faturamento, depois nome)
    public List<ProdutoVendido> produtosMaisVendidos(FiltroDashboard filtro, Integer limite) {
        int maximo = validarLimite(limite, LIMITE_PADRAO);
        Map<String, String> categoriaPorProduto = categoriasDosProdutos();

        // chave = id do produto. O nome fica o do ultimo pedido (o produto pode ter sido renomeado)
        Map<String, String> nomes = new HashMap<>();
        Map<String, double[]> porProduto = new HashMap<>();
        long totalItens = 0;

        for (Pedido p : somenteValidos(buscarPedidos(filtro))) {
            for (ItemPedido item : itensDe(p)) {
                String chave = item.getProdutoId() != null ? item.getProdutoId() : "nome:" + item.getNomeProduto();
                nomes.put(chave, item.getNomeProduto());
                double[] acumulado = porProduto.computeIfAbsent(chave, c -> new double[2]);
                acumulado[0] = acumulado[0] + item.getQuantidade();
                acumulado[1] = acumulado[1] + item.getSubtotal();
                totalItens = totalItens + item.getQuantidade();
            }
        }

        List<Map.Entry<String, double[]>> ordenado = new ArrayList<>(porProduto.entrySet());
        ordenado.sort(Comparator
                .comparingDouble((Map.Entry<String, double[]> e) -> e.getValue()[0]).reversed()
                .thenComparing(Comparator.comparingDouble((Map.Entry<String, double[]> e) -> e.getValue()[1]).reversed())
                .thenComparing(e -> String.valueOf(nomes.get(e.getKey()))));

        List<ProdutoVendido> resultado = new ArrayList<>();
        for (Map.Entry<String, double[]> e : ordenado) {
            if (resultado.size() == maximo) {
                break;
            }
            String chave = e.getKey();
            String produtoId = chave.startsWith("nome:") ? null : chave;
            resultado.add(new ProdutoVendido(resultado.size() + 1, produtoId, nomes.get(chave),
                    categoriaPorProduto.getOrDefault(chave, SEM_CATEGORIA),
                    (long) e.getValue()[0], arredondar(e.getValue()[1]), percentual(e.getValue()[0], totalItens)));
        }
        return resultado;
    }

    // ==========================================
    // RF05 - OPCOES DE FILTRO
    // ==========================================
    public OpcoesFiltro opcoesFiltro() {
        LocalDate hoje = LocalDate.now();
        return new OpcoesFiltro(FiltroDashboard.STATUS_VALIDOS, FiltroDashboard.TIPOS_ENTREGA_VALIDOS,
                hoje.minusDays(FiltroDashboard.PERIODO_PADRAO_DIAS - 1), hoje, FiltroDashboard.PERIODO_MAXIMO_DIAS);
    }

    // ==========================================
    // METODOS DE APOIO
    // ==========================================

    // busca no banco pelo periodo e aplica os filtros de status e tipo de entrega
    private List<Pedido> buscarPedidos(FiltroDashboard filtro) {
        List<Pedido> doPeriodo = pedidoRepository.buscarPorPeriodo(filtro.inicioPeriodo(), filtro.fimPeriodo());
        List<Pedido> resultado = new ArrayList<>();
        if (doPeriodo == null) {
            return resultado;
        }
        for (Pedido p : doPeriodo) {
            // dado incompleto no banco nao pode derrubar o dashboard
            if (p == null || p.getDataHoraEntrada() == null) {
                continue;
            }
            if (filtro.status() != null && !filtro.status().equals(p.getStatus())) {
                continue;
            }
            if (filtro.tipoEntrega() != null && !filtro.tipoEntrega().equals(p.getTipoEntrega())) {
                continue;
            }
            resultado.add(p);
        }
        return resultado;
    }

    private List<Pedido> somenteValidos(List<Pedido> pedidos) {
        List<Pedido> validos = new ArrayList<>();
        for (Pedido p : pedidos) {
            if (!PedidoService.CANCELADO.equals(p.getStatus())) {
                validos.add(p);
            }
        }
        return validos;
    }

    private List<ItemPedido> itensDe(Pedido pedido) {
        return pedido.getItens() == null ? List.of() : pedido.getItens();
    }

    // id do produto -> tipo (Pizza, Bebida...). O item do pedido nao guarda o tipo.
    private Map<String, String> categoriasDosProdutos() {
        Map<String, String> categorias = new HashMap<>();
        List<Produto> produtos = produtoRepository.findAll();
        if (produtos != null) {
            for (Produto produto : produtos) {
                if (produto.getTipo() != null && !produto.getTipo().isBlank()) {
                    categorias.put(produto.getId(), produto.getTipo().trim());
                }
            }
        }
        return categorias;
    }

    private int validarLimite(Integer limite, int padrao) {
        if (limite == null) {
            return padrao;
        }
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new RegraNegocioException("O parâmetro limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return limite;
    }

    private Periodo periodo(FiltroDashboard filtro) {
        return new Periodo(filtro.dataInicio(), filtro.dataFim(), filtro.quantidadeDias());
    }

    private double percentual(double parte, double total) {
        if (total <= 0) {
            return 0;
        }
        return arredondar(parte / total * 100);
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
