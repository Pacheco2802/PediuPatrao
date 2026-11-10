package com.umc.pediupatrao.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

// Classe de apoio para pegar quem esta logado no momento
public class UsuarioLogado {

    public static String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return "sistema";
        }
        return auth.getName();
    }

    // devolve ADMIN, GERENTE ou ATENDENTE (sem o ROLE_ na frente)
    public static String getPerfil() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "";
        }
        for (GrantedAuthority autoridade : auth.getAuthorities()) {
            String nome = autoridade.getAuthority();
            if (nome != null && nome.startsWith("ROLE_")) {
                return nome.substring(5);
            }
        }
        return "";
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(getPerfil());
    }
}
