# PediuPatrão - Atividade Prática M1 (Parte 2)

Aluno: Pedro Henrique Prado Pacheco  RGM: 11231101417

Sistema de pedidos de pizzaria feito com Java 21, Spring Boot 3, Spring Security, Thymeleaf e MongoDB.
Nesta parte da atividade evoluí o projeto que foi passado em aula, mantendo as camadas que já existiam
(entity, repository, service, controller) e acrescentando controle por perfil, fluxo de pedidos,
descontos, cancelamento e auditoria.

## Dashboard Administrativo

Painel com indicadores de vendas, graficos, situacao dos pedidos, ranking de produtos e filtros,
alimentado pela API `/api/dashboard/**` (perfis ADMIN e GERENTE). Tela em `/dashboard`.

- Documento da entrega (requisitos, endpoints, evidencias de testes e decisoes): [docs/DASHBOARD.md](docs/DASHBOARD.md)
- Evidencias: [docs/evidencias](docs/evidencias)
- Video de apresentacao: https://youtu.be/hXq7OphN-IU
- Roteiro do video: [docs/ROTEIRO-VIDEO.md](docs/ROTEIRO-VIDEO.md)

Para criar pedidos de demonstracao (uma unica vez) e ver o dashboard preenchido:

```
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--pediupatrao.demo.popular-pedidos=true"
```

## Como executar

Requisitos: JDK 21 instalado (o Maven pode ser o wrapper que já vem no projeto).

1. Abrir um terminal na pasta do projeto
2. Rodar:
   - Windows: `mvnw.cmd spring-boot:run`
   - Linux/Mac: `./mvnw spring-boot:run`
3. Acessar http://localhost:8080

Também dá para abrir no NetBeans/IntelliJ como projeto Maven e rodar a classe `PizzariaPedidosApplication`.

O banco usado é o MongoDB configurado em `src/main/resources/application.properties`.
Coloquei um nome de banco separado (`pizzaria_atividade_m1`) para não misturar com os dados dos colegas.
Para usar um Mongo local basta trocar a URI para `mongodb://localhost:27017`.

Na primeira execução a classe `DataInitializer` cria os usuários de teste e alguns produtos.

### Usuários de teste

| Usuário     | Senha        | Perfil    |
|-------------|--------------|-----------|
| admin       | teste123     | ADMIN     |
| gerente01   | gerente123   | GERENTE   |
| atendente01 | atendente123 | ATENDENTE |

### Testes

```
mvnw.cmd test
```

São 51 testes (JUnit + Mockito + MockMvc). Eles não precisam do MongoDB rodando.

- `PedidoServiceTest`: cálculo do total, transições válidas e inválidas, desconto (limite de 20% e só antes da saída) e cancelamento.
- `ClienteServiceTest`: auditoria registra só o campo que mudou (ex: logradouro) e máscara de dados.
- `PedidoSegurancaTest`: bloqueio no backend (401 sem login, 403 para ATENDENTE e ADMIN tentando cancelar ou dar desconto, 403 sem token CSRF).

## Perfis e permissões

A proteção está no backend em dois lugares: nas regras de URL do `SecurityConfig` e com `@PreAuthorize`
nos métodos dos services. O menu esconde o que o perfil não usa, mas isso é só visual.

| Funcionalidade                         | ADMIN | GERENTE | ATENDENTE |
|----------------------------------------|:-----:|:-------:|:---------:|
| Usuários e perfis                      |  sim  |   não   |    não    |
| Consultar pedidos                      |  sim  |   sim   |    sim    |
| Registrar pedido                       |  não  |   sim   |    sim    |
| Mudar status do pedido                 |  não  |   sim   |    não    |
| Aplicar desconto                       |  não  |   sim   |    não    |
| Cancelar pedido                        |  não  |   sim   |    não    |
| Consultar clientes                     |  não  |   sim   |    sim    |
| Cadastrar e alterar clientes           |  não  |   não   |    sim    |
| Excluir cliente                        |  não  |   sim   |    não    |
| Ver auditoria                          |  sim  |   sim   |    não    |
| Cadastrar/alterar produtos             |  sim  |   sim   |    não    |

Tentativas de acesso indevido retornam 403 (página "Acesso negado" no navegador ou status 403 na API).
Chamadas na API sem estar logado retornam 401.

## Requisitos implementados

### RF01 - Usuários e permissões
- Perfis ADMIN, GERENTE e ATENDENTE. O `UsuarioDetailsService` agora usa o perfil salvo no banco
  (antes colocava USER para todo mundo). Usuário antigo sem perfil fica como ATENDENTE (menor acesso).
- `/usuarios/**` e `/api/usuarios/**` só para ADMIN (antes `/api/usuarios` estava liberado sem login).
- O cadastro valida o perfil (só aceita os três), não deixa username repetido e ignora id enviado na criação.
- Proteção contra autoelevação: só o ADMIN altera perfis e ninguém consegue mudar o próprio perfil nem excluir a si mesmo.
- A senha não volta mais no JSON da API (`@JsonProperty(access = WRITE_ONLY)`) e o hash não vai para o formulário de edição.
- CSRF foi religado. Os formulários com `th:action` mandam o token automaticamente e o `layout.html`
  tem as meta tags `_csrf` para quem precisar usar fetch. O logout agora é um POST.

### RF02 - Cadastro de pedidos
- `Pedido` agora tem lista de itens (`ItemPedido`: produto, quantidade, preço unitário, subtotal),
  subtotal, percentual e valor de desconto, valor total, tipo (ENTREGA ou RETIRADA),
  usuário e data/hora de entrada.
- O preço de cada item vem do cadastro do produto e o total é calculado no `PedidoService`.
  Se a requisição mandar `valorTotal`, `status`, desconto ou preço, isso é ignorado.
- Validações: cliente existente, pelo menos um item, quantidade entre 1 e 50, produto ativo e com preço.

### RF03 - Entrada, saída e transições
Fluxo obrigatório:

```
RECEBIDO -> EM_PREPARACAO -> PRONTO -> SAIU_PARA_ENTREGA (entrega) ou RETIRADO (retirada) -> FINALIZADO
```

- Na entrada grava data/hora e usuário que registrou.
- Na saída (SAIU_PARA_ENTREGA ou RETIRADO) grava data/hora e usuário responsável.
- Qualquer pulo de etapa é bloqueado (ex: EM_PREPARACAO direto para FINALIZADO).
- Pedido FINALIZADO ou CANCELADO não aceita mais mudança de status.
- CANCELADO não pode ser colocado pela rota de status, só pela operação de cancelamento.

### RF04 - Cancelamento e desconto (só GERENTE)
- Cancelamento exige justificativa (mínimo 5 caracteres), só pode antes da saída e guarda quem cancelou e quando.
  Os itens e valores do pedido continuam salvos.
- Desconto é percentual, maior que 0 e no máximo 20%, sempre calculado sobre o subtotal
  (aplicar de novo substitui o anterior). Só pode antes da saída e não pode em pedido cancelado.
- As duas operações vão para a auditoria com valores anteriores e novos.

### RF05 - Auditoria
- Coleção `auditoria` no MongoDB com: usuário, perfil, data/hora, operação, entidade, id da entidade,
  lista de campos alterados (campo, valor anterior, valor novo) e justificativa.
- Operações auditadas: INCLUSAO_PEDIDO, ALTERACAO_STATUS_PEDIDO, DESCONTO_PEDIDO, CANCELAMENTO_PEDIDO,
  INCLUSAO_CLIENTE, ALTERACAO_CLIENTE, EXCLUSAO_CLIENTE, INCLUSAO_USUARIO, ALTERACAO_USUARIO e EXCLUSAO_USUARIO.
- Na alteração de cliente só os campos que realmente mudaram são registrados (ex: logradouro Rua das Flores -> Av. Brasil).
- Consulta em `/auditoria` (tela) e `/api/auditoria` (JSON), só para ADMIN e GERENTE, com filtro por id do pedido/cliente.
- Os registros não podem ser alterados nem excluídos: o `AuditoriaRepository` estende `Repository` (e não `MongoRepository`),
  então só existem os métodos `insert` e de consulta. A API só tem GET e qualquer outro método em `/api/auditoria` é negado.
- Senhas nunca são gravadas na auditoria (na troca de senha aparece só "alterada").
- Para o ADMIN os dados pessoais dos clientes aparecem mascarados na auditoria (telefone só com os 4 últimos dígitos),
  já que o papel dele é administrar usuários e não atender clientes. O GERENTE vê completo.

## Principais alterações

Arquivos novos:
- `entity/Auditoria.java`, `entity/CampoAlterado.java`, `entity/ItemPedido.java`
- `repository/AuditoriaRepository.java`
- `service/AuditoriaService.java`
- `controller/AuditoriaController.java`
- `exception/RegraNegocioException.java`, `exception/TratamentoErros.java` (erro da API vira JSON com status 400)
- `util/UsuarioLogado.java` (pega usuário e perfil logados)
- `templates/pedidos/form.html`, `templates/pedidos/detalhes.html`, `templates/auditoria/lista.html`
- testes em `src/test/java`

Arquivos alterados:
- `SecurityConfig`: regras por perfil, CSRF ligado, `@EnableMethodSecurity`, 401 nas APIs sem login
- `UsuarioDetailsService`, `UsuarioService`, `UsuarioController`, `Usuario`
- `Pedido`, `PedidoService`, `PedidoController`
- `ClienteService`, `ClienteController` (criar/atualizar separados, com auditoria)
- `ProdutoService` e telas de produto (a tela antiga não abria dentro do layout e o formulário não salvava)
- `HomeController`: telas de pedido, auditoria, produto e tratamento de erro nas telas
- `DataInitializer`: cria os três usuários e produtos iniciais
- `layout.html`, `sidebar.html`, `login.html`, telas de clientes e usuários
- `pom.xml`: `thymeleaf-extras-springsecurity6` e dependências de teste

## Testando o bloqueio pela API

Sem login:

```
curl -i http://localhost:8080/api/pedidos
```

Retorna 401.

Logado como ATENDENTE (ou ADMIN), abrir o console do navegador (F12) e rodar, trocando o id do pedido:

```js
fetch('/api/pedidos/ID_DO_PEDIDO/cancelar?justificativa=teste', {
    method: 'POST',
    headers: { 'X-CSRF-TOKEN': document.querySelector('meta[name=_csrf]').content }
}).then(r => console.log(r.status));
```

Retorna 403. Logado como GERENTE a mesma chamada cancela o pedido (200).
Também dá para testar digitando direto na barra de endereço uma tela sem permissão, por exemplo `/usuarios` ou `/auditoria` logado como atendente.

## Endpoints da API

| Método | URL                                   | Perfil              |
|--------|---------------------------------------|---------------------|
| GET    | /api/pedidos, /api/pedidos/{id}       | todos               |
| POST   | /api/pedidos                          | ATENDENTE, GERENTE  |
| PUT    | /api/pedidos/{id}/status?status=X     | GERENTE             |
| POST   | /api/pedidos/{id}/desconto?percentual=10&justificativa=... | GERENTE |
| POST   | /api/pedidos/{id}/cancelar?justificativa=...               | GERENTE |
| GET    | /api/clientes                         | GERENTE, ATENDENTE  |
| POST/PUT | /api/clientes                       | ATENDENTE           |
| DELETE | /api/clientes/{id}                    | GERENTE             |
| GET    | /api/auditoria?entidadeId=...         | ADMIN, GERENTE      |
| *      | /api/usuarios                         | ADMIN               |

Exemplo de corpo para criar pedido:

```json
{
  "clienteId": "id do cliente",
  "tipoEntrega": "ENTREGA",
  "itens": [ { "produtoId": "id do produto", "quantidade": 2 } ]
}
```
