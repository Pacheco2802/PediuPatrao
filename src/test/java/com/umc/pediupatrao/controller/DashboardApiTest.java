package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.dto.DashboardDTO.ProdutoVendido;
import com.umc.pediupatrao.dto.DashboardDTO.VendaDia;
import com.umc.pediupatrao.dto.FiltroDashboard;
import com.umc.pediupatrao.exception.TratamentoErros;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Testa a camada HTTP da API do dashboard: quem pode acessar, parametros e formato dos erros
@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, TratamentoErros.class})
public class DashboardApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    // ---------- acesso ----------
    @Test
    public void semLoginRecebe401() throws Exception {
        mockMvc.perform(get("/api/dashboard/indicadores"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    public void atendenteNaoAcessaODashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/indicadores"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/produtos-mais-vendidos"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    public void gerenteAcessaODashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/indicadores"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void adminAcessaODashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/pedidos-por-status"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    public void dashboardESomenteLeitura() throws Exception {
        mockMvc.perform(post("/api/dashboard/indicadores").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ---------- parametros e respostas ----------
    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    public void filtrosDaRequisicaoChegamNoService() throws Exception {
        when(dashboardService.vendasPorDia(any())).thenReturn(List.of(
                new VendaDia(LocalDate.of(2026, 11, 10), 3, 150.50)));

        mockMvc.perform(get("/api/dashboard/vendas-por-dia")
                        .param("dataInicio", "2026-11-01")
                        .param("dataFim", "2026-11-10")
                        .param("status", "finalizado")
                        .param("tipoEntrega", "ENTREGA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].data").value("2026-11-10"))
                .andExpect(jsonPath("$[0].pedidos").value(3))
                .andExpect(jsonPath("$[0].faturamento").value(150.50));

        ArgumentCaptor<FiltroDashboard> filtro = ArgumentCaptor.forClass(FiltroDashboard.class);
        verify(dashboardService).vendasPorDia(filtro.capture());
        assertEquals(LocalDate.of(2026, 11, 1), filtro.getValue().dataInicio());
        assertEquals(LocalDate.of(2026, 11, 10), filtro.getValue().dataFim());
        assertEquals("FINALIZADO", filtro.getValue().status());
        assertEquals("ENTREGA", filtro.getValue().tipoEntrega());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    public void rankingRecebeOLimite() throws Exception {
        when(dashboardService.produtosMaisVendidos(any(), eq(3))).thenReturn(List.of(
                new ProdutoVendido(1, "abc", "Pizza Calabresa", "Pizza", 12, 540.00, 40.0)));

        mockMvc.perform(get("/api/dashboard/produtos-mais-vendidos").param("limite", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].posicao").value(1))
                .andExpect(jsonPath("$[0].nome").value("Pizza Calabresa"))
                .andExpect(jsonPath("$[0].quantidade").value(12));
    }

    // ---------- erros ----------
    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    public void dataEmFormatoErradoDevolve400ComMensagem() throws Exception {
        mockMvc.perform(get("/api/dashboard/indicadores").param("dataInicio", "10/11/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    public void dataInicialMaiorQueFinalDevolve400() throws Exception {
        mockMvc.perform(get("/api/dashboard/vendas-por-hora")
                        .param("dataInicio", "2026-11-10").param("dataFim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A data inicial não pode ser maior que a data final."));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    public void statusDesconhecidoDevolve400() throws Exception {
        mockMvc.perform(get("/api/dashboard/pedidos-por-status").param("status", "ENTREGUE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    public void limiteQueNaoENumeroDevolve400() throws Exception {
        mockMvc.perform(get("/api/dashboard/produtos-mais-vendidos").param("limite", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Parâmetro limite inválido."));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    public void bancoForaDoArDevolve503ComMensagem() throws Exception {
        when(dashboardService.indicadores(any())).thenThrow(new DataAccessResourceFailureException("sem conexao"));

        mockMvc.perform(get("/api/dashboard/indicadores"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro").exists());
    }
}
