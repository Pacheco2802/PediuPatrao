package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);

        if (usuario == null) {
            throw new UsernameNotFoundException("Usuário não encontrado");
        }

        // agora a role vem do cadastro do usuario (antes era sempre USER)
        String perfil = usuario.getRole();

        // usuario antigo sem perfil ou com perfil desconhecido fica com o menor acesso
        if (!"ADMIN".equals(perfil) && !"GERENTE".equals(perfil) && !"ATENDENTE".equals(perfil)) {
            perfil = "ATENDENTE";
        }

        return User.builder()
                .username(usuario.getUsername())
                .password(usuario.getPassword())
                .roles(perfil)
                .build();
    }
}
