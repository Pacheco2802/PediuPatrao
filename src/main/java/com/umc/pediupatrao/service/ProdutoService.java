package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    public List<Produto> listarProdutos() {
        return produtoRepository.findAll();
    }

    // so os ativos, usado na tela de novo pedido
    public List<Produto> listarAtivos() {
        List<Produto> ativos = new ArrayList<>();
        for (Produto p : produtoRepository.findAll()) {
            if (p.getAtivo() != null && p.getAtivo()) {
                ativos.add(p);
            }
        }
        return ativos;
    }

    public Optional<Produto> buscarPorId(String id) {
        return produtoRepository.findById(id);
    }

    // Método para excluir produto
    public void excluir(String id) {
        produtoRepository.deleteById(id);
    }

    // Método para salvar um novo produto ou atualizar um existente
    public Produto salvar(Produto produto) {
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new RegraNegocioException("Informe o nome do produto.");
        }
        if (produto.getPreco() == null || produto.getPreco() <= 0) {
            throw new RegraNegocioException("O preço do produto deve ser maior que zero.");
        }
        if (produto.getAtivo() == null) {
            produto.setAtivo(false);
        }

        // Se o produto não tem ID, salva como novo
        if (produto.getId() == null || produto.getId().isBlank()) {
            produto.setId(null);
            return produtoRepository.save(produto);
        } else {
            // Verifica se o produto existe antes de atualizar
            if (produtoRepository.existsById(produto.getId())) {
                return produtoRepository.save(produto);
            } else {
                throw new RegraNegocioException("Produto não encontrado para atualização.");
            }
        }
    }
}
