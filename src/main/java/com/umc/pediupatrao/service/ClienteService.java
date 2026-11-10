package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // Cadastro de cliente novo
    public Cliente criar(Cliente cliente) {
        validar(cliente);
        // garante que e um cliente novo (nao deixa sobrescrever outro passando o id)
        cliente.setId(null);
        Cliente salvo = clienteRepository.save(cliente);

        List<CampoAlterado> campos = new ArrayList<>();
        compararCampos(campos, new Cliente(), salvo);
        auditoriaService.registrar("INCLUSAO_CLIENTE", "Cliente", salvo.getId(), campos, null);
        return salvo;
    }

    // Atualizacao: compara o que estava no banco com o que chegou e audita so o que mudou
    public Cliente atualizar(String id, Cliente dados) {
        Cliente atual = clienteRepository.findById(id).orElse(null);
        if (atual == null) {
            throw new RegraNegocioException("Cliente não encontrado para atualização.");
        }
        validar(dados);

        List<CampoAlterado> campos = new ArrayList<>();
        compararCampos(campos, atual, dados);

        atual.setNome(dados.getNome());
        atual.setTelefone(dados.getTelefone());
        atual.setCep(dados.getCep());
        atual.setLogradouro(dados.getLogradouro());
        atual.setNumero(dados.getNumero());
        atual.setComplemento(dados.getComplemento());
        atual.setBairro(dados.getBairro());
        atual.setCidade(dados.getCidade());
        atual.setEstado(dados.getEstado());

        Cliente salvo = clienteRepository.save(atual);

        if (!campos.isEmpty()) {
            auditoriaService.registrar("ALTERACAO_CLIENTE", "Cliente", salvo.getId(), campos, null);
        }
        return salvo;
    }

    // Método para excluir cliente
    public void excluir(String id) {
        Cliente atual = clienteRepository.findById(id).orElse(null);
        if (atual == null) {
            throw new RegraNegocioException("Cliente não encontrado.");
        }
        clienteRepository.deleteById(id);

        List<CampoAlterado> campos = new ArrayList<>();
        compararCampos(campos, atual, new Cliente());
        auditoriaService.registrar("EXCLUSAO_CLIENTE", "Cliente", id, campos, null);
    }

    private void validar(Cliente cliente) {
        if (cliente.getNome() == null || cliente.getNome().isBlank()) {
            throw new RegraNegocioException("O nome do cliente é obrigatório.");
        }
        if (cliente.getTelefone() == null || cliente.getTelefone().isBlank()) {
            throw new RegraNegocioException("O telefone do cliente é obrigatório.");
        }
    }

    private void compararCampos(List<CampoAlterado> campos, Cliente antes, Cliente depois) {
        auditoriaService.adicionarSeMudou(campos, "nome", antes.getNome(), depois.getNome());
        auditoriaService.adicionarSeMudou(campos, "telefone", antes.getTelefone(), depois.getTelefone());
        auditoriaService.adicionarSeMudou(campos, "cep", antes.getCep(), depois.getCep());
        auditoriaService.adicionarSeMudou(campos, "logradouro", antes.getLogradouro(), depois.getLogradouro());
        auditoriaService.adicionarSeMudou(campos, "numero", antes.getNumero(), depois.getNumero());
        auditoriaService.adicionarSeMudou(campos, "complemento", antes.getComplemento(), depois.getComplemento());
        auditoriaService.adicionarSeMudou(campos, "bairro", antes.getBairro(), depois.getBairro());
        auditoriaService.adicionarSeMudou(campos, "cidade", antes.getCidade(), depois.getCidade());
        auditoriaService.adicionarSeMudou(campos, "estado", antes.getEstado(), depois.getEstado());
    }
}
