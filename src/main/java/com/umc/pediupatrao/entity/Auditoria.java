package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// Cada documento e um evento de auditoria (quem fez, quando, o que mudou)
@Document(collection = "auditoria")
public class Auditoria {

    @Id
    private String id;

    private String usuario;      // username de quem fez a operacao
    private String perfil;       // perfil dele no momento (ADMIN, GERENTE, ATENDENTE)
    private LocalDateTime dataHora;

    private String operacao;     // ex: INCLUSAO_PEDIDO, ALTERACAO_CLIENTE, CANCELAMENTO_PEDIDO...
    private String entidade;     // Pedido, Cliente ou Usuario
    private String entidadeId;   // id do registro afetado

    private List<CampoAlterado> campos = new ArrayList<>();

    private String justificativa;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public void setPerfil(String perfil) {
        this.perfil = perfil;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getOperacao() {
        return operacao;
    }

    public void setOperacao(String operacao) {
        this.operacao = operacao;
    }

    public String getEntidade() {
        return entidade;
    }

    public void setEntidade(String entidade) {
        this.entidade = entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public void setEntidadeId(String entidadeId) {
        this.entidadeId = entidadeId;
    }

    public List<CampoAlterado> getCampos() {
        return campos;
    }

    public void setCampos(List<CampoAlterado> campos) {
        this.campos = campos;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
    }
}
