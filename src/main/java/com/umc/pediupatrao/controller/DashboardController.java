package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.DashboardDTO.Indicadores;
import com.umc.pediupatrao.dto.DashboardDTO.OpcoesFiltro;
import com.umc.pediupatrao.dto.DashboardDTO.PedidoResumo;
import com.umc.pediupatrao.dto.DashboardDTO.ProdutoVendido;
import com.umc.pediupatrao.dto.DashboardDTO.StatusPedidos;
import com.umc.pediupatrao.dto.DashboardDTO.VendaCategoria;
import com.umc.pediupatrao.dto.DashboardDTO.VendaDia;
import com.umc.pediupatrao.dto.DashboardDTO.VendaHora;
import com.umc.pediupatrao.dto.FiltroDashboard;
import com.umc.pediupatrao.service.DashboardService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// API do dashboard administrativo (somente leitura, ADMIN e GERENTE).
//
// Todos os endpoints de dados aceitam os mesmos filtros opcionais:
//   dataInicio e dataFim (AAAA-MM-DD), status e tipoEntrega.
// Sem datas, considera os ultimos 7 dias. Filtro invalido devolve 400 com {"erro": "..."}.
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/filtros")
    public OpcoesFiltro filtros() {
        return dashboardService.opcoesFiltro();
    }

    @GetMapping("/indicadores")
    public Indicadores indicadores(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipoEntrega) {
        return dashboardService.indicadores(FiltroDashboard.criar(dataInicio, dataFim, status, tipoEntrega));
    }

    @GetMapping("/vendas-por-dia")
    public List<VendaDia> vendasPorDia(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipoEntrega) {
        return dashboardService.vendasPorDia(FiltroDashboard.criar(dataInicio, dataFim, status, tipoEntrega));
    }

    @GetMapping("/vendas-por-hora")
    public List<VendaHora> vendasPorHora(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipoEntrega) {
        return dashboardService.vendasPorHora(FiltroDashboard.criar(dataInicio, dataFim, status, tipoEntrega));
    }

    @GetMapping("/vendas-por-categoria")
    public List<VendaCategoria> vendasPorCategoria(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipoEntrega) {
        return dashboardService.vendasPorCategoria(FiltroDashboard.criar(dataInicio, dataFim, status, tipoEntrega));
    }

    @GetMapping("/pedidos-por-status")
    public List<StatusPedidos> pedidosPorStatus(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipoEntrega) {
        return dashboardService.pedidosPorStatus(FiltroDashboard.criar(dataInicio, dataFim, status, tipoEntrega));
    }

    @GetMapping("/pedidos-recentes")
    public List<PedidoResumo> pedidosRecentes(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipoEntrega,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.pedidosRecentes(FiltroDashboard.criar(dataInicio, dataFim, status, tipoEntrega), limite);
    }

    @GetMapping("/produtos-mais-vendidos")
    public List<ProdutoVendido> produtosMaisVendidos(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipoEntrega,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.produtosMaisVendidos(FiltroDashboard.criar(dataInicio, dataFim, status, tipoEntrega), limite);
    }
}
