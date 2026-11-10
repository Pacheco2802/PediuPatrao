package com.umc.pediupatrao.entity;

// Item dentro do pedido. O nome e o preco sao copiados do produto
// na hora do pedido, assim se o preco do produto mudar depois o pedido antigo nao muda.
public class ItemPedido {

    private String produtoId;
    private String nomeProduto;
    private int quantidade;
    private double precoUnitario;
    private double subtotal;

    public String getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(String produtoId) {
        this.produtoId = produtoId;
    }

    public String getNomeProduto() {
        return nomeProduto;
    }

    public void setNomeProduto(String nomeProduto) {
        this.nomeProduto = nomeProduto;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }

    public double getPrecoUnitario() {
        return precoUnitario;
    }

    public void setPrecoUnitario(double precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    public double getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(double subtotal) {
        this.subtotal = subtotal;
    }
}
