# Roteiro do vídeo de apresentação (até 3 minutos)

Antes de gravar: suba a aplicação com os dados de demonstração (ver item 9 do
[DASHBOARD.md](DASHBOARD.md)), entre como `gerente01` e deixe abertas duas abas:
o dashboard e o GitHub na branch `feature/dashboard-administrativo`.

| Tempo | O que mostrar | O que falar |
|---|---|---|
| 0:00 – 0:20 | Tela de login → menu → **Dashboard** | "Este é o dashboard administrativo do Pediu Patrão. Ele mostra ao responsável pela pizzaria como estão as vendas e os pedidos. A tela não traz dados prontos: tudo vem de uma API que eu projetei." |
| 0:20 – 0:50 | Os 4 indicadores e o gráfico de faturamento por dia | "RF01: faturamento, pedidos, ticket médio e cancelamentos. O faturamento é comparado com o período anterior para mostrar a tendência. RF02: faturamento por dia, pedidos por hora — que mostra o horário de pico — e vendas por categoria." |
| 0:50 – 1:15 | Situação dos pedidos, pedidos recentes e ranking (trocar para Top 10) | "RF03: quantos pedidos estão em cada etapa e os últimos pedidos. RF04: ranking dos produtos mais vendidos, por quantidade." |
| 1:15 – 1:45 | Clicar em **14 dias**, depois filtrar **Status = Cancelado**, depois **Limpar** | "RF05: os filtros de período, status e tipo de entrega valem para a página toda. Repare que, filtrando cancelados, o faturamento fica zero: decidi que pedido cancelado não é venda. Os gráficos mostram o estado 'sem dados' em vez de quebrar." |
| 1:45 – 2:10 | Colocar data inicial maior que a final; depois abrir `/api/dashboard/indicadores` e `/api/dashboard/indicadores?dataInicio=abc` no navegador | "RF06 e RF07: cada bloco chama um endpoint próprio. A API valida os parâmetros e sempre responde erro no mesmo formato JSON. A tela atualiza sozinha a cada 60 segundos e trata carregamento, falta de dados e erro em cada bloco." |
| 2:10 – 2:40 | GitHub: branch `feature/dashboard-administrativo`, `DashboardController`, `DashboardService` e `docs/DASHBOARD.md` | "Principais decisões: um endpoint por bloco, para carregar em paralelo e um erro não derrubar a tela; filtros validados em um lugar só; a API devolve DTOs e não a entidade; é somente leitura e restrita a ADMIN e GERENTE, com a proteção no backend. Os cálculos ficam no serviço, o que permitiu testar tudo sem o banco." |
| 2:40 – 3:00 | Terminal com `mvnw.cmd test` (51 testes, BUILD SUCCESS) ou a seção 7 do documento | "São 51 testes automatizados, 29 novos, mais 22 casos testados direto na API com as respostas salvas em docs/evidencias. O documento traz os requisitos, a documentação dos endpoints e as evidências." |

Dicas:

- Grave em 1280×720 ou maior, com o zoom do navegador em 100%.
- Se passar de 3 minutos, corte o trecho do GitHub para 15 segundos (mostre só a branch e o documento).
- Para mostrar o 403, abra uma janela anônima, entre como `atendente01` e acesse `/dashboard`.
