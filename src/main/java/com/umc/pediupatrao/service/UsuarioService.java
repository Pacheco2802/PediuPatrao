package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.util.UsuarioLogado;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Busca um usuário por ID
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    // Busca um usuário por username
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    // perfis que existem no sistema
    public boolean perfilValido(String perfil) {
        return "ADMIN".equals(perfil) || "GERENTE".equals(perfil) || "ATENDENTE".equals(perfil);
    }

    // Salva um novo usuário (criptografando a senha)
    @PreAuthorize("hasRole('ADMIN')")
    public Usuario salvarUsuario(Usuario usuario) {
        if (usuario.getUsername() == null || usuario.getUsername().isBlank()) {
            throw new RegraNegocioException("Informe o username.");
        }
        if (usuario.getPassword() == null || usuario.getPassword().length() < 6) {
            throw new RegraNegocioException("A senha precisa ter pelo menos 6 caracteres.");
        }
        if (!perfilValido(usuario.getRole())) {
            throw new RegraNegocioException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE.");
        }
        usuario.setUsername(usuario.getUsername().trim());
        if (usuarioRepository.findByUsername(usuario.getUsername()).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com esse username.");
        }

        // garante que e um cadastro novo (ninguem consegue mandar um id e sobrescrever outro usuario)
        usuario.setId(null);
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword())); // Criptografa a senha
        Usuario salvo = usuarioRepository.save(usuario);

        // a senha nao vai para a auditoria
        List<CampoAlterado> campos = new ArrayList<>();
        campos.add(new CampoAlterado("username", "", salvo.getUsername()));
        campos.add(new CampoAlterado("perfil", "", salvo.getRole()));
        auditoriaService.registrar("INCLUSAO_USUARIO", "Usuario", salvo.getId(), campos, null);

        return salvo;
    }

    // Deleta um usuário pelo ID
    @PreAuthorize("hasRole('ADMIN')")
    public void deletarUsuario(String id) {
        Usuario usuario = usuarioRepository.findById(id).orElse(null);
        if (usuario == null) {
            throw new RegraNegocioException("Usuário não encontrado.");
        }
        if (usuario.getUsername().equals(UsuarioLogado.getUsername())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        usuarioRepository.deleteById(id);

        List<CampoAlterado> campos = new ArrayList<>();
        campos.add(new CampoAlterado("username", usuario.getUsername(), ""));
        campos.add(new CampoAlterado("perfil", usuario.getRole(), ""));
        auditoriaService.registrar("EXCLUSAO_USUARIO", "Usuario", id, campos, null);
    }

    // Lista todos os usuários
    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    // Atualiza o usuário
    @PreAuthorize("hasRole('ADMIN')")
    public Usuario atualizarUsuario(String id, Usuario usuarioAtualizado) {
        Usuario usuarioExistente = usuarioRepository.findById(id).orElse(null);
        if (usuarioExistente == null) {
            throw new RegraNegocioException("Usuário não encontrado.");
        }
        if (!perfilValido(usuarioAtualizado.getRole())) {
            throw new RegraNegocioException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE.");
        }

        // ninguem pode mudar o proprio perfil (evita autoelevacao e o admin se trancar fora)
        boolean ehEleMesmo = usuarioExistente.getUsername().equals(UsuarioLogado.getUsername());
        if (ehEleMesmo && !usuarioAtualizado.getRole().equals(usuarioExistente.getRole())) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
        }

        String novoUsername = usuarioAtualizado.getUsername();
        if (novoUsername == null || novoUsername.isBlank()) {
            throw new RegraNegocioException("Informe o username.");
        }
        novoUsername = novoUsername.trim();
        if (ehEleMesmo && !novoUsername.equals(usuarioExistente.getUsername())) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio username.");
        }
        Usuario outro = usuarioRepository.findByUsername(novoUsername).orElse(null);
        if (outro != null && !outro.getId().equals(id)) {
            throw new RegraNegocioException("Já existe um usuário com esse username.");
        }

        List<CampoAlterado> campos = new ArrayList<>();
        auditoriaService.adicionarSeMudou(campos, "username", usuarioExistente.getUsername(), novoUsername);
        auditoriaService.adicionarSeMudou(campos, "perfil", usuarioExistente.getRole(), usuarioAtualizado.getRole());

        usuarioExistente.setUsername(novoUsername);
        usuarioExistente.setRole(usuarioAtualizado.getRole());

        // Só atualiza a senha se uma nova foi informada
        if (usuarioAtualizado.getPassword() != null && !usuarioAtualizado.getPassword().isBlank()) {
            if (usuarioAtualizado.getPassword().length() < 6) {
                throw new RegraNegocioException("A senha precisa ter pelo menos 6 caracteres.");
            }
            usuarioExistente.setPassword(passwordEncoder.encode(usuarioAtualizado.getPassword()));
            // registra so que a senha mudou, nunca o valor
            campos.add(new CampoAlterado("senha", "******", "****** (alterada)"));
        }

        Usuario salvo = usuarioRepository.save(usuarioExistente);
        if (!campos.isEmpty()) {
            auditoriaService.registrar("ALTERACAO_USUARIO", "Usuario", id, campos, null);
        }
        return salvo;
    }
}
