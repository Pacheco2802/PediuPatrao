package com.umc.pediupatrao.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private AuditoriaService auditoriaService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("content", "home :: content");
        return "layout";
    }

    // ========================
    // DASHBOARD (ADMIN e GERENTE)
    // ========================
    // a tela vem vazia e busca os dados em /api/dashboard/** (ver static/js/dashboard.js)
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("content", "dashboard :: content");
        return "layout";
    }

    // ========================
    // USUÁRIOS (somente ADMIN)
    // ========================
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        List<Usuario> usuarios = (List<Usuario>) usuarioService.listarTodos();
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("content", "usuarios/lista :: content");
        log.info("Carregando fragmento: usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/usuarios/novo")
    public String novoUsuarioForm(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @GetMapping("/usuarios/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        Usuario u = usuarioService.buscarPorId(id).orElse(null);

        if (u == null) {
            redirectAttributes.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/usuarios";
        }

        // manda pra tela sem a senha (nem o hash vai pro html)
        Usuario form = new Usuario();
        form.setId(u.getId());
        form.setUsername(u.getUsername());
        form.setRole(u.getRole());

        model.addAttribute("usuario", form);
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @PostMapping("/usuarios/salvar")
    public String salvarUsuario(@ModelAttribute Usuario usuario,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.salvarUsuario(usuario);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id,
            @ModelAttribute Usuario usuario,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.atualizarUsuario(id, usuario);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/deletar/{id}")
    public String deletarUsuario(@PathVariable String id,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.deletarUsuario(id);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    // ========================
    // PEDIDOS
    // ========================
    @GetMapping("/pedidos")
    public String pedidos(Model model) {
        List<Pedido> pedidos = pedidoService.listarPedidos();
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("content", "pedidos :: content");
        log.info("Carregando fragmento: pedidos :: content");
        return "layout";
    }

    @GetMapping("/pedidos/novo")
    public String novoPedidoForm(Model model) {
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarAtivos());
        model.addAttribute("content", "pedidos/form :: content");
        return "layout";
    }

    // a tela manda uma lista de produtoIds e outra de quantidades (mesma ordem)
    @PostMapping("/pedidos/salvar")
    public String salvarPedido(@RequestParam(required = false) String clienteId,
            @RequestParam(required = false) String tipoEntrega,
            @RequestParam(required = false) String[] produtoIds,
            @RequestParam(required = false) String[] quantidades,
            RedirectAttributes redirectAttributes) {

        List<ItemPedido> itens = new ArrayList<>();
        if (produtoIds != null && quantidades != null) {
            for (int i = 0; i < produtoIds.length && i < quantidades.length; i++) {
                int qtd = 0;
                try {
                    if (quantidades[i] != null && !quantidades[i].isBlank()) {
                        qtd = Integer.parseInt(quantidades[i].trim());
                    }
                } catch (NumberFormatException e) {
                    redirectAttributes.addFlashAttribute("erro", "Quantidade inválida.");
                    return "redirect:/pedidos/novo";
                }
                // quantidade 0 = produto nao escolhido
                if (qtd != 0) {
                    ItemPedido item = new ItemPedido();
                    item.setProdutoId(produtoIds[i]);
                    item.setQuantidade(qtd);
                    itens.add(item);
                }
            }
        }

        try {
            Pedido salvo = pedidoService.criarPedido(clienteId, tipoEntrega, itens);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido registrado com sucesso!");
            return "redirect:/pedidos/" + salvo.getId();
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos/novo";
        }
    }

    @GetMapping("/pedidos/{id}")
    public String detalhesPedido(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        Pedido pedido;
        try {
            pedido = pedidoService.buscarPorId(id);
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos";
        }
        model.addAttribute("pedido", pedido);
        model.addAttribute("proximoStatus", pedidoService.proximoStatus(pedido));
        model.addAttribute("antesDaSaida", pedidoService.antesDaSaida(pedido));
        model.addAttribute("content", "pedidos/detalhes :: content");
        return "layout";
    }

    @PostMapping("/pedidos/{id}/status")
    public String mudarStatus(@PathVariable String id, @RequestParam String status,
            RedirectAttributes redirectAttributes) {
        try {
            pedidoService.atualizarStatus(id, status);
            redirectAttributes.addFlashAttribute("sucesso", "Status atualizado.");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/desconto")
    public String aplicarDesconto(@PathVariable String id,
            @RequestParam(required = false) String percentual,
            @RequestParam(required = false) String justificativa,
            RedirectAttributes redirectAttributes) {
        try {
            Double valor = null;
            if (percentual != null && !percentual.isBlank()) {
                valor = Double.parseDouble(percentual.trim().replace(",", "."));
            }
            pedidoService.aplicarDesconto(id, valor, justificativa);
            redirectAttributes.addFlashAttribute("sucesso", "Desconto aplicado.");
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("erro", "Percentual de desconto inválido.");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public String cancelarPedido(@PathVariable String id,
            @RequestParam(required = false) String justificativa,
            RedirectAttributes redirectAttributes) {
        try {
            pedidoService.cancelar(id, justificativa);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido cancelado.");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    // ========================
    // AUDITORIA (ADMIN e GERENTE)
    // ========================
    @GetMapping("/auditoria")
    public String auditoria(@RequestParam(required = false) String entidadeId, Model model) {
        List<Auditoria> registros = auditoriaService.listar(entidadeId);
        model.addAttribute("registros", registros);
        model.addAttribute("entidadeId", entidadeId);
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }

    // ========================
    // PRODUTOS
    // ========================
    @GetMapping("/produtos")
    public String produtos(Model model) {
        List<Produto> produtos = produtoService.listarProdutos();
        model.addAttribute("produtos", produtos);
        model.addAttribute("content", "produtos/lista :: content");
        log.info("Carregando fragmento: produtos/lista :: content");
        return "layout";
    }

    @GetMapping("/produtos/novo")
    public String novoProdutoForm(Model model) {
        Produto produto = new Produto();
        produto.setAtivo(true);
        model.addAttribute("produto", produto);
        model.addAttribute("content", "produtos/produto-form :: content");
        return "layout";
    }

    @GetMapping("/produtos/editar/{id}")
    public String editarProdutoForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        Produto produto = produtoService.buscarPorId(id).orElse(null);
        if (produto == null) {
            redirectAttributes.addFlashAttribute("erro", "Produto não encontrado.");
            return "redirect:/produtos";
        }
        model.addAttribute("produto", produto);
        model.addAttribute("content", "produtos/produto-form :: content");
        return "layout";
    }

    @PostMapping("/produtos/salvar")
    public String salvarProduto(@ModelAttribute Produto produto,
            RedirectAttributes redirectAttributes) {
        try {
            produtoService.salvar(produto);
            redirectAttributes.addFlashAttribute("sucesso", "Produto salvo com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/produtos";
    }

    @PostMapping("/produtos/excluir/{id}")
    public String excluirProduto(@PathVariable String id,
            RedirectAttributes redirectAttributes) {
        produtoService.excluir(id);
        redirectAttributes.addFlashAttribute("sucesso", "Produto excluído.");
        return "redirect:/produtos";
    }

    // ========================
    // CLIENTES
    // ========================
    @GetMapping("/clientes")
    public String clientes(Model model) {
        List<Cliente> clientes = clienteService.listarClientes();
        model.addAttribute("clientes", clientes);
        model.addAttribute("content", "clientes/lista :: content");
        log.info("Carregando fragmento: clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes/novo")
    public String novoClienteForm(Model model) {
        model.addAttribute("cliente", new Cliente());
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente,
            RedirectAttributes redirectAttributes) {
        try {
            clienteService.criar(cliente);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/clientes";
    }

    @GetMapping("/clientes/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        Cliente c = clienteService.buscarPorId(id).orElse(null);

        if (c == null) {
            redirectAttributes.addFlashAttribute("erro", "Cliente não encontrado.");
            return "redirect:/clientes";
        }

        model.addAttribute("cliente", c);
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/editar/{id}")
    public String atualizarCliente(@PathVariable String id,
            @ModelAttribute Cliente cliente,
            RedirectAttributes redirectAttributes) {
        try {
            clienteService.atualizar(id, cliente);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/clientes";
    }

    @PostMapping("/clientes/excluir/{id}")
    public String excluirCliente(@PathVariable String id,
            RedirectAttributes redirectAttributes) {
        try {
            clienteService.excluir(id);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente excluído.");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/clientes";
    }

    // ========================
    // OUTRAS ROTAS
    // ========================
    @GetMapping("/configuracoes")
    public String configuracoes(Model model) {
        model.addAttribute("content", "configuracoes :: content");
        return "layout";
    }

    @GetMapping("/teste")
    public String teste() {
        return "teste";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
