package com.umc.pediupatrao.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Respostas da API do dashboard. Sao records simples, so com os dados
// que a tela precisa (nao expoe a entidade Pedido inteira).
public class DashboardDTO {

    private DashboardDTO() {
    }

    // periodo realmente usado na consulta (ajuda a tela a mostrar o que foi aplicado)
    public record Periodo(LocalDate dataInicio, LocalDate dataFim, long dias) {
    }

    // RF01 - indicadores gerais
    public record Indicadores(
            Periodo periodo,
            long totalPedidos,
            long pedidosValidos,
            long pedidosEmAndamento,
            long pedidosFinalizados,
            long pedidosCancelados,
            double taxaCancelamento,
            double faturamento,
            double ticketMedio,
            double totalDescontos,
            long itensVendidos,
            double faturamentoPeriodoAnterior,
            long pedidosPeriodoAnterior,
            Double variacaoFaturamento,
            LocalDateTime atualizadoEm) {
    }

    // RF02 - graficos
    public record VendaDia(LocalDate data, long pedidos, double faturamento) {
    }

    public record VendaHora(int hora, long pedidos, double faturamento) {
    }

    public record VendaCategoria(String categoria, long quantidade, double faturamento, double percentual) {
    }

    // RF03 - situacao dos pedidos
    public record StatusPedidos(String status, long quantidade, double valor, double percentual) {
    }

    public record PedidoResumo(String id, String clienteNome, String tipoEntrega, String status,
            int quantidadeItens, double valorTotal, LocalDateTime dataHoraEntrada) {
    }

    // RF04 - ranking de produtos
    public record ProdutoVendido(int posicao, String produtoId, String nome, String categoria,
            long quantidade, double faturamento, double percentual) {
    }

    // RF05 - opcoes para montar os filtros da tela
    public record OpcoesFiltro(List<String> status, List<String> tiposEntrega,
            LocalDate dataInicioPadrao, LocalDate dataFimPadrao, int periodoMaximoDias) {
    }
}
