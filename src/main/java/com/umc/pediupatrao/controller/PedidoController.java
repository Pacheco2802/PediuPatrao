package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.PedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private PedidoService pedidoService;

    // Corpo esperado:
    // { "clienteId": "...", "tipoEntrega": "ENTREGA", "itens": [ { "produtoId": "...", "quantidade": 2 } ] }
    // Qualquer outro campo (valorTotal, status, desconto...) e ignorado pelo service.
    @PostMapping
    public ResponseEntity<Pedido> criarPedido(@RequestBody Pedido pedido) {
        Pedido salvo = pedidoService.criarPedido(pedido.getClienteId(), pedido.getTipoEntrega(), pedido.getItens());
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public Pedido buscarPedido(@PathVariable String id) {
        return pedidoService.buscarPorId(id);
    }

    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam String status) {
        return pedidoService.atualizarStatus(id, status);
    }

    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @RequestParam Double percentual,
            @RequestParam(required = false) String justificativa) {
        return pedidoService.aplicarDesconto(id, percentual, justificativa);
    }

    @PostMapping("/{id}/cancelar")
    public Pedido cancelar(@PathVariable String id, @RequestParam(required = false) String justificativa) {
        return pedidoService.cancelar(id, justificativa);
    }
}
