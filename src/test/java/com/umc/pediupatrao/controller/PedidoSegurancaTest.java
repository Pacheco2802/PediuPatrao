package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Testa se o backend bloqueia mesmo (nao so esconder botao)
@WebMvcTest(PedidoController.class)
@Import(SecurityConfig.class)
public class PedidoSegurancaTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoService pedidoService;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    public void semLoginRecebe401() throws Exception {
        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    public void atendenteNaoPodeCancelar() throws Exception {
        mockMvc.perform(post("/api/pedidos/1/cancelar").param("justificativa", "teste").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void adminNaoPodeCancelar() throws Exception {
        mockMvc.perform(post("/api/pedidos/1/cancelar").param("justificativa", "teste").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void adminNaoPodeDarDesconto() throws Exception {
        mockMvc.perform(post("/api/pedidos/1/desconto").param("percentual", "10").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    public void gerentePodeCancelar() throws Exception {
        when(pedidoService.cancelar(any(), any())).thenReturn(new Pedido());
        mockMvc.perform(post("/api/pedidos/1/cancelar").param("justificativa", "cliente desistiu").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    public void semTokenCsrfEhBloqueado() throws Exception {
        mockMvc.perform(post("/api/pedidos/1/cancelar").param("justificativa", "cliente desistiu"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    public void atendentePodeConsultarPedidos() throws Exception {
        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isOk());
    }
}
