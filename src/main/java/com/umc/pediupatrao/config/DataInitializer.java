package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, ProdutoRepository produtoRepo) {
        return args -> {

            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

            // um usuario de cada perfil para testar
            criarUsuarioSeNaoExistir(repo, encoder, "admin", "teste123", "ADMIN");
            criarUsuarioSeNaoExistir(repo, encoder, "gerente01", "gerente123", "GERENTE");
            criarUsuarioSeNaoExistir(repo, encoder, "atendente01", "atendente123", "ATENDENTE");

            // alguns produtos para conseguir montar pedido
            if (produtoRepo.count() == 0) {
                criarProduto(produtoRepo, "Pizza Calabresa", "Pizza", 45.00);
                criarProduto(produtoRepo, "Pizza Mussarela", "Pizza", 42.00);
                criarProduto(produtoRepo, "Pizza Portuguesa", "Pizza", 52.00);
                criarProduto(produtoRepo, "Refrigerante 2L", "Bebida", 12.00);
                criarProduto(produtoRepo, "Borda Recheada", "Adicional", 8.00);
                System.out.println("Produtos iniciais criados!");
            }
        };
    }

    private void criarUsuarioSeNaoExistir(UsuarioRepository repo, BCryptPasswordEncoder encoder,
            String username, String senha, String perfil) {

        Usuario existente = repo.findByUsername(username).orElse(null);

        if (existente == null) {
            Usuario user = new Usuario();
            user.setUsername(username);
            user.setPassword(encoder.encode(senha));
            user.setRole(perfil);
            repo.save(user);
            System.out.println("Usuário " + username + " criado!");
        } else if (existente.getRole() == null || existente.getRole().equals("USER")) {
            // usuario antigo que ainda nao tinha perfil
            existente.setRole(perfil);
            repo.save(existente);
            System.out.println("Perfil do usuário " + username + " ajustado para " + perfil);
        }
    }

    private void criarProduto(ProdutoRepository repo, String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        repo.save(p);
    }
}
