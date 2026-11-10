package com.umc.pediupatrao.entity;

// Guarda um campo que mudou dentro de um evento de auditoria
public class CampoAlterado {

    private String campo;
    private String valorAnterior;
    private String valorNovo;

    public CampoAlterado() {
    }

    public CampoAlterado(String campo, String valorAnterior, String valorNovo) {
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
    }

    public String getCampo() {
        return campo;
    }

    public void setCampo(String campo) {
        this.campo = campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public void setValorAnterior(String valorAnterior) {
        this.valorAnterior = valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }

    public void setValorNovo(String valorNovo) {
        this.valorNovo = valorNovo;
    }
}
