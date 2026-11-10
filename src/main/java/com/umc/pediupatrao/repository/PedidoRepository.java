package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findByClienteId(String clienteId);

    // pedidos que entraram no periodo: inicio inclusive, fim exclusive (usado no dashboard)
    @org.springframework.data.mongodb.repository.Query("{ 'dataHoraEntrada': { $gte: ?0, $lt: ?1 } }")
    List<Pedido> buscarPorPeriodo(java.time.LocalDateTime inicio, java.time.LocalDateTime fim);

    long countByUsuarioEntrada(String usuarioEntrada);
}
