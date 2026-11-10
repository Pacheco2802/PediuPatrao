package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.util.UsuarioLogado;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    // Grava um evento novo de auditoria. Nunca altera um que ja existe.
    public void registrar(String operacao, String entidade, String entidadeId,
            List<CampoAlterado> campos, String justificativa) {

        Auditoria auditoria = new Auditoria();
        auditoria.setUsuario(UsuarioLogado.getUsername());
        auditoria.setPerfil(UsuarioLogado.getPerfil());
        auditoria.setDataHora(LocalDateTime.now());
        auditoria.setOperacao(operacao);
        auditoria.setEntidade(entidade);
        auditoria.setEntidadeId(entidadeId);
        if (campos != null) {
            auditoria.setCampos(campos);
        }
        auditoria.setJustificativa(justificativa);

        auditoriaRepository.insert(auditoria);
    }

    // So adiciona na lista se o valor realmente mudou
    public void adicionarSeMudou(List<CampoAlterado> campos, String campo, String antes, String depois) {
        String a = antes == null ? "" : antes.trim();
        String d = depois == null ? "" : depois.trim();
        if (!a.equals(d)) {
            campos.add(new CampoAlterado(campo, a, d));
        }
    }

    // Consulta (somente ADMIN e GERENTE). Se entidadeId vier preenchido filtra por ele.
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<Auditoria> listar(String entidadeId) {
        List<Auditoria> lista;
        if (entidadeId != null && !entidadeId.isBlank()) {
            lista = auditoriaRepository.findByEntidadeIdOrderByDataHoraDesc(entidadeId.trim());
        } else {
            lista = auditoriaRepository.findAllByOrderByDataHoraDesc();
        }

        // O ADMIN cuida dos usuarios do sistema e nao atende cliente,
        // entao para ele os dados pessoais dos clientes aparecem mascarados.
        if (UsuarioLogado.isAdmin()) {
            List<Auditoria> mascarada = new ArrayList<>();
            for (Auditoria a : lista) {
                mascarada.add(copiarComMascara(a));
            }
            return mascarada;
        }
        return lista;
    }

    // cria uma copia (nao mexe no registro original do banco)
    private Auditoria copiarComMascara(Auditoria original) {
        if (!"Cliente".equals(original.getEntidade())) {
            return original;
        }
        Auditoria copia = new Auditoria();
        copia.setId(original.getId());
        copia.setUsuario(original.getUsuario());
        copia.setPerfil(original.getPerfil());
        copia.setDataHora(original.getDataHora());
        copia.setOperacao(original.getOperacao());
        copia.setEntidade(original.getEntidade());
        copia.setEntidadeId(original.getEntidadeId());
        copia.setJustificativa(original.getJustificativa());

        List<CampoAlterado> campos = new ArrayList<>();
        for (CampoAlterado c : original.getCampos()) {
            campos.add(new CampoAlterado(c.getCampo(),
                    mascarar(c.getCampo(), c.getValorAnterior()),
                    mascarar(c.getCampo(), c.getValorNovo())));
        }
        copia.setCampos(campos);
        return copia;
    }

    public String mascarar(String campo, String valor) {
        if (valor == null || valor.isEmpty()) {
            return valor;
        }
        if ("telefone".equals(campo)) {
            // mostra so os 4 ultimos numeros
            String numeros = valor.replaceAll("[^0-9]", "");
            if (numeros.length() <= 4) {
                return "****";
            }
            return "*****" + numeros.substring(numeros.length() - 4);
        }
        // outros campos: mostra so o comeco
        if (valor.length() <= 3) {
            return "***";
        }
        return valor.substring(0, 3) + "***";
    }
}
