package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    // spy para usar o adicionarSeMudou de verdade mas sem gravar no banco
    @Spy
    private AuditoriaService auditoriaService;

    @InjectMocks
    private ClienteService clienteService;

    @Test
    @SuppressWarnings("unchecked")
    public void alteracaoDeEnderecoGeraAuditoriaSoDoCampoAlterado() {
        Cliente atual = new Cliente();
        atual.setId("c1");
        atual.setNome("Maria");
        atual.setTelefone("(11) 99999-0000");
        atual.setLogradouro("Rua das Flores");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(atual));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(auditoriaService).registrar(any(), any(), any(), any(), any());

        Cliente novo = new Cliente();
        novo.setNome("Maria");
        novo.setTelefone("(11) 99999-0000");
        novo.setLogradouro("Av. Brasil");

        clienteService.atualizar("c1", novo);

        ArgumentCaptor<List<CampoAlterado>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq("ALTERACAO_CLIENTE"), eq("Cliente"), eq("c1"), captor.capture(), isNull());

        List<CampoAlterado> campos = captor.getValue();
        assertEquals(1, campos.size());
        assertEquals("logradouro", campos.get(0).getCampo());
        assertEquals("Rua das Flores", campos.get(0).getValorAnterior());
        assertEquals("Av. Brasil", campos.get(0).getValorNovo());
    }

    @Test
    public void semMudancaNaoGeraAuditoria() {
        Cliente atual = new Cliente();
        atual.setId("c1");
        atual.setNome("Maria");
        atual.setTelefone("11999990000");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(atual));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        Cliente igual = new Cliente();
        igual.setNome("Maria");
        igual.setTelefone("11999990000");

        clienteService.atualizar("c1", igual);

        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    public void mascaraTelefone() {
        AuditoriaService service = new AuditoriaService();
        assertEquals("*****4321", service.mascarar("telefone", "(11) 97777-4321"));
        assertEquals("Av.***", service.mascarar("logradouro", "Av. Brasil"));
    }
}
