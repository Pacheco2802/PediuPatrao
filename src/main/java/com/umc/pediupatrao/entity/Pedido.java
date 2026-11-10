package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pedidos")
public class Pedido {
    @Id
    private String id;
    private String clienteId;
    private String clienteNome;
    private String tipoEntrega; // ENTREGA ou RETIRADA
    private String status;

    private List<ItemPedido> itens = new ArrayList<>();

    // valores (sempre calculados no PedidoService, nunca vem da requisicao)
    private double subtotal;
    private double percentualDesconto;
    private double valorDesconto;
    private double valorTotal;

    // entrada do pedido
    private LocalDateTime dataHoraEntrada;
    private String usuarioEntrada;

    // saida (saiu para entrega ou foi retirado)
    private LocalDateTime dataHoraSaida;
    private String usuarioSaida;

    // finalizacao
    private LocalDateTime dataHoraFinalizacao;
    private String usuarioFinalizacao;

    // desconto
    private LocalDateTime dataHoraDesconto;
    private String usuarioDesconto;

    // cancelamento
    private LocalDateTime dataHoraCancelamento;
    private String usuarioCancelamento;
    private String justificativaCancelamento;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public String getClienteNome() {
        return clienteNome;
    }

    public void setClienteNome(String clienteNome) {
        this.clienteNome = clienteNome;
    }

    public String getTipoEntrega() {
        return tipoEntrega;
    }

    public void setTipoEntrega(String tipoEntrega) {
        this.tipoEntrega = tipoEntrega;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<ItemPedido> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedido> itens) {
        this.itens = itens;
    }

    public double getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(double subtotal) {
        this.subtotal = subtotal;
    }

    public double getPercentualDesconto() {
        return percentualDesconto;
    }

    public void setPercentualDesconto(double percentualDesconto) {
        this.percentualDesconto = percentualDesconto;
    }

    public double getValorDesconto() {
        return valorDesconto;
    }

    public void setValorDesconto(double valorDesconto) {
        this.valorDesconto = valorDesconto;
    }

    public double getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(double valorTotal) {
        this.valorTotal = valorTotal;
    }

    public LocalDateTime getDataHoraEntrada() {
        return dataHoraEntrada;
    }

    public void setDataHoraEntrada(LocalDateTime dataHoraEntrada) {
        this.dataHoraEntrada = dataHoraEntrada;
    }

    public String getUsuarioEntrada() {
        return usuarioEntrada;
    }

    public void setUsuarioEntrada(String usuarioEntrada) {
        this.usuarioEntrada = usuarioEntrada;
    }

    public LocalDateTime getDataHoraSaida() {
        return dataHoraSaida;
    }

    public void setDataHoraSaida(LocalDateTime dataHoraSaida) {
        this.dataHoraSaida = dataHoraSaida;
    }

    public String getUsuarioSaida() {
        return usuarioSaida;
    }

    public void setUsuarioSaida(String usuarioSaida) {
        this.usuarioSaida = usuarioSaida;
    }

    public LocalDateTime getDataHoraFinalizacao() {
        return dataHoraFinalizacao;
    }

    public void setDataHoraFinalizacao(LocalDateTime dataHoraFinalizacao) {
        this.dataHoraFinalizacao = dataHoraFinalizacao;
    }

    public String getUsuarioFinalizacao() {
        return usuarioFinalizacao;
    }

    public void setUsuarioFinalizacao(String usuarioFinalizacao) {
        this.usuarioFinalizacao = usuarioFinalizacao;
    }

    public LocalDateTime getDataHoraDesconto() {
        return dataHoraDesconto;
    }

    public void setDataHoraDesconto(LocalDateTime dataHoraDesconto) {
        this.dataHoraDesconto = dataHoraDesconto;
    }

    public String getUsuarioDesconto() {
        return usuarioDesconto;
    }

    public void setUsuarioDesconto(String usuarioDesconto) {
        this.usuarioDesconto = usuarioDesconto;
    }

    public LocalDateTime getDataHoraCancelamento() {
        return dataHoraCancelamento;
    }

    public void setDataHoraCancelamento(LocalDateTime dataHoraCancelamento) {
        this.dataHoraCancelamento = dataHoraCancelamento;
    }

    public String getUsuarioCancelamento() {
        return usuarioCancelamento;
    }

    public void setUsuarioCancelamento(String usuarioCancelamento) {
        this.usuarioCancelamento = usuarioCancelamento;
    }

    public String getJustificativaCancelamento() {
        return justificativaCancelamento;
    }

    public void setJustificativaCancelamento(String justificativaCancelamento) {
        this.justificativaCancelamento = justificativaCancelamento;
    }
}
