package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.PedidoService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Cria pedidos de demonstracao dos ultimos 14 dias para o dashboard ter o que mostrar.
//
// So roda se a propriedade pediupatrao.demo.popular-pedidos=true estiver ligada
// e so uma vez: os pedidos sao gravados com usuarioEntrada = "demo", se ja existir
// algum assim nao cria de novo. Os pedidos reais nao sao alterados.
@Component
@ConditionalOnProperty(name = "pediupatrao.demo.popular-pedidos", havingValue = "true")
public class DemoDataSeeder {

    public static final String USUARIO_DEMO = "demo";
    private static final int DIAS = 14;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    // semente fixa: os dados de demonstracao saem sempre parecidos
    private final Random random = new Random(42);

    // ApplicationReadyEvent = depois do DataInitializer, entao os produtos ja existem
    @EventListener(ApplicationReadyEvent.class)
    public void popular() {
        if (pedidoRepository.countByUsuarioEntrada(USUARIO_DEMO) > 0) {
            return;
        }

        List<Produto> produtos = new ArrayList<>();
        for (Produto p : produtoRepository.findAll()) {
            if (Boolean.TRUE.equals(p.getAtivo()) && p.getPreco() != null && p.getPreco() > 0) {
                produtos.add(p);
            }
        }
        if (produtos.isEmpty()) {
            System.out.println("Dados de demonstração: nenhum produto ativo, nada foi criado.");
            return;
        }

        List<Cliente> clientes = clienteRepository.findAll();
        if (clientes.isEmpty()) {
            clientes = new ArrayList<>();
            clientes.add(criarCliente("Ana Souza (demo)", "11900000001"));
            clientes.add(criarCliente("Bruno Lima (demo)", "11900000002"));
            clientes.add(criarCliente("Carla Mendes (demo)", "11900000003"));
            clientes.add(criarCliente("Diego Rocha (demo)", "11900000004"));
        }

        LocalDateTime agora = LocalDateTime.now();
        LocalDate hoje = agora.toLocalDate();
        List<Pedido> novos = new ArrayList<>();

        for (int diasAtras = DIAS - 1; diasAtras >= 0; diasAtras--) {
            LocalDate dia = hoje.minusDays(diasAtras);
            DayOfWeek semana = dia.getDayOfWeek();
            boolean fimDeSemana = semana == DayOfWeek.FRIDAY || semana == DayOfWeek.SATURDAY || semana == DayOfWeek.SUNDAY;
            int quantidade = fimDeSemana ? 8 + random.nextInt(5) : 3 + random.nextInt(4);

            for (int i = 0; i < quantidade; i++) {
                LocalDateTime entrada = dia.atTime(sortearHora(), random.nextInt(60));
                if (entrada.isAfter(agora)) {
                    // hoje: nao existe pedido no futuro
                    entrada = agora.minusMinutes(5 + random.nextInt(180));
                }
                novos.add(montarPedido(entrada, agora, clientes, produtos));
            }
        }

        pedidoRepository.saveAll(novos);
        System.out.println("Dados de demonstração: " + novos.size() + " pedidos criados.");
    }

    private Pedido montarPedido(LocalDateTime entrada, LocalDateTime agora, List<Cliente> clientes, List<Produto> produtos) {
        Cliente cliente = clientes.get(random.nextInt(clientes.size()));

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoEntrega(random.nextInt(100) < 70 ? "ENTREGA" : "RETIRADA");
        pedido.setDataHoraEntrada(entrada);
        pedido.setUsuarioEntrada(USUARIO_DEMO);

        // 1 a 3 produtos diferentes. Os primeiros da lista saem mais (para o ranking ter diferenca)
        int tiposDeItem = 1 + random.nextInt(Math.min(3, produtos.size()));
        List<Produto> escolhidos = new ArrayList<>();
        double subtotal = 0;
        while (escolhidos.size() < tiposDeItem) {
            int a = random.nextInt(produtos.size());
            int b = random.nextInt(produtos.size());
            Produto produto = produtos.get(Math.min(a, b));
            if (escolhidos.contains(produto)) {
                continue;
            }
            escolhidos.add(produto);

            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setQuantidade(1 + random.nextInt(2));
            item.setPrecoUnitario(produto.getPreco());
            item.setSubtotal(arredondar(produto.getPreco() * item.getQuantidade()));
            pedido.getItens().add(item);
            subtotal = subtotal + item.getSubtotal();
        }

        // alguns pedidos com desconto (dentro do limite de 20%)
        double percentual = random.nextInt(100) < 15 ? 5 * (1 + random.nextInt(3)) : 0;
        double desconto = arredondar(subtotal * percentual / 100);
        pedido.setSubtotal(arredondar(subtotal));
        pedido.setPercentualDesconto(percentual);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(arredondar(subtotal - desconto));
        if (percentual > 0) {
            pedido.setDataHoraDesconto(entrada.plusMinutes(2));
            pedido.setUsuarioDesconto(USUARIO_DEMO);
        }

        definirStatus(pedido, entrada, agora);
        return pedido;
    }

    // pedido antigo ja terminou (finalizado ou cancelado). Pedido das ultimas 2 horas
    // fica no meio do fluxo, para a situacao dos pedidos ter todos os status.
    private void definirStatus(Pedido pedido, LocalDateTime entrada, LocalDateTime agora) {
        boolean retirada = "RETIRADA".equals(pedido.getTipoEntrega());

        if (random.nextInt(100) < 10) {
            pedido.setStatus(PedidoService.CANCELADO);
            pedido.setDataHoraCancelamento(entrada.plusMinutes(10));
            pedido.setUsuarioCancelamento(USUARIO_DEMO);
            pedido.setJustificativaCancelamento("Cliente desistiu do pedido (demonstração)");
            return;
        }

        if (entrada.isAfter(agora.minusHours(2))) {
            int etapa = random.nextInt(4);
            if (etapa == 0) {
                pedido.setStatus(PedidoService.RECEBIDO);
            } else if (etapa == 1) {
                pedido.setStatus(PedidoService.EM_PREPARACAO);
            } else if (etapa == 2) {
                pedido.setStatus(PedidoService.PRONTO);
            } else {
                pedido.setStatus(retirada ? PedidoService.RETIRADO : PedidoService.SAIU_PARA_ENTREGA);
                pedido.setDataHoraSaida(entrada.plusMinutes(30));
                pedido.setUsuarioSaida(USUARIO_DEMO);
            }
            return;
        }

        pedido.setStatus(PedidoService.FINALIZADO);
        pedido.setDataHoraSaida(entrada.plusMinutes(30));
        pedido.setUsuarioSaida(USUARIO_DEMO);
        pedido.setDataHoraFinalizacao(entrada.plusMinutes(retirada ? 35 : 60));
        pedido.setUsuarioFinalizacao(USUARIO_DEMO);
    }

    // maior movimento no jantar (18h as 23h), um pouco no almoco (11h as 14h)
    private int sortearHora() {
        if (random.nextInt(100) < 75) {
            int[] jantar = {18, 19, 19, 20, 20, 20, 21, 21, 22, 23};
            return jantar[random.nextInt(jantar.length)];
        }
        return 11 + random.nextInt(4);
    }

    private Cliente criarCliente(String nome, String telefone) {
        Cliente cliente = new Cliente();
        cliente.setNome(nome);
        cliente.setTelefone(telefone);
        cliente.setLogradouro("Rua de Demonstração");
        cliente.setNumero("100");
        cliente.setBairro("Centro");
        cliente.setCidade("Mogi das Cruzes");
        cliente.setEstado("SP");
        return clienteRepository.save(cliente);
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
