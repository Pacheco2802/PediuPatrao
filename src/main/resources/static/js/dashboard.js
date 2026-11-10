// Dashboard administrativo.
// Busca tudo na API (/api/dashboard/**) e desenha os indicadores, graficos e tabelas.
// Cada bloco da tela e independente: se um endpoint falhar, so aquele bloco mostra erro.
(function () {
    'use strict';

    var API = '/api/dashboard';
    var INTERVALO_ATUALIZACAO = 60000; // 60 segundos
    var COR_SERIE = '#2a78d6';
    var COR_GRADE = '#e9ecef';
    var COR_TEXTO = '#6c757d';

    var STATUS = {
        RECEBIDO:          { nome: 'Recebido',          icone: 'bi-inbox',         tom: 'neutro' },
        EM_PREPARACAO:     { nome: 'Em preparação',     icone: 'bi-fire',          tom: 'neutro' },
        PRONTO:            { nome: 'Pronto',            icone: 'bi-bell',          tom: 'neutro' },
        SAIU_PARA_ENTREGA: { nome: 'Saiu para entrega', icone: 'bi-bicycle',       tom: 'neutro' },
        RETIRADO:          { nome: 'Retirado',          icone: 'bi-bag-check',     tom: 'neutro' },
        FINALIZADO:        { nome: 'Finalizado',        icone: 'bi-check-circle-fill', tom: 'bom' },
        CANCELADO:         { nome: 'Cancelado',         icone: 'bi-x-circle-fill', tom: 'critico' }
    };
    var TIPOS = { ENTREGA: 'Entrega', RETIRADA: 'Retirada' };

    var moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    var numero = new Intl.NumberFormat('pt-BR');
    var decimal = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

    var graficos = {};
    var consultaAtual = 0;   // para descartar resposta antiga quando o filtro muda rapido
    var temporizador = null;

    var campoInicio = document.getElementById('dash-inicio');
    var campoFim = document.getElementById('dash-fim');
    var campoStatus = document.getElementById('dash-status');
    var campoTipo = document.getElementById('dash-tipo');
    var campoLimite = document.getElementById('dash-limite');
    var campoAuto = document.getElementById('dash-auto');
    var caixaErro = document.getElementById('dash-erro');

    // ---------------------------------------------------------------
    // utilidades
    // ---------------------------------------------------------------

    // data local no formato AAAA-MM-DD (toISOString usaria UTC e erraria o dia a noite)
    function dataIso(data) {
        var mes = String(data.getMonth() + 1).padStart(2, '0');
        var dia = String(data.getDate()).padStart(2, '0');
        return data.getFullYear() + '-' + mes + '-' + dia;
    }

    // "2026-11-10" -> "10/11"
    function diaMes(iso) {
        var partes = String(iso).split('-');
        return partes[2] + '/' + partes[1];
    }

    // "2026-11-10T19:35:00" -> "10/11 19:35"
    function dataHora(iso) {
        if (!iso) {
            return '-';
        }
        var texto = String(iso);
        return diaMes(texto.substring(0, 10)) + ' ' + texto.substring(11, 16);
    }

    // cria elemento sempre com textContent (nome de cliente/produto nunca vira HTML)
    function el(tag, classe, texto) {
        var no = document.createElement(tag);
        if (classe) {
            no.className = classe;
        }
        if (texto !== undefined && texto !== null) {
            no.textContent = texto;
        }
        return no;
    }

    function limpar(no) {
        while (no.firstChild) {
            no.removeChild(no.firstChild);
        }
    }

    function texto(id, valor) {
        document.getElementById(id).textContent = valor;
    }

    function ErroApi(mensagem, status) {
        this.message = mensagem;
        this.status = status;
    }

    // RF06 - integracao com a API
    function buscar(caminho, parametros) {
        var url = new URL(API + caminho, window.location.origin);
        Object.keys(parametros || {}).forEach(function (chave) {
            if (parametros[chave] !== '' && parametros[chave] !== null && parametros[chave] !== undefined) {
                url.searchParams.set(chave, parametros[chave]);
            }
        });

        return fetch(url, { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
            .catch(function () {
                throw new ErroApi('Sem conexão com o servidor.', 0);
            })
            .then(function (resposta) {
                if (resposta.status === 401) {
                    // sessao expirou: volta para o login
                    window.location.href = '/login';
                    throw new ErroApi('Sessão expirada.', 401);
                }
                if (resposta.status === 403) {
                    throw new ErroApi('Seu perfil não tem acesso ao dashboard.', 403);
                }
                return resposta.json().catch(function () {
                    return null;
                }).then(function (corpo) {
                    if (!resposta.ok) {
                        var mensagem = corpo && corpo.erro ? corpo.erro : 'Erro ao consultar o servidor (' + resposta.status + ').';
                        throw new ErroApi(mensagem, resposta.status);
                    }
                    if (corpo === null) {
                        throw new ErroApi('O servidor devolveu uma resposta inválida.', resposta.status);
                    }
                    return corpo;
                });
            });
    }

    // ---------------------------------------------------------------
    // estados dos blocos (RF07): carregando, vazio, erro, pronto
    // ---------------------------------------------------------------
    function estado(idBloco, tipo, mensagem) {
        var bloco = document.getElementById(idBloco);
        var antigo = bloco.querySelector('.dash-estado');
        if (antigo) {
            bloco.removeChild(antigo);
        }
        bloco.classList.remove('carregando', 'vazio', 'erro');
        if (tipo === 'pronto') {
            return;
        }
        bloco.classList.add(tipo);

        var caixa = el('div', 'dash-estado');
        if (tipo === 'carregando') {
            var giro = el('div', 'spinner-border spinner-border-sm text-secondary');
            giro.setAttribute('role', 'status');
            giro.appendChild(el('span', 'visually-hidden', 'Carregando...'));
            caixa.appendChild(giro);
        } else if (tipo === 'vazio') {
            caixa.appendChild(el('i', 'bi bi-inbox fs-3'));
            caixa.appendChild(el('span', null, mensagem || 'Nenhum dado para o período e filtros escolhidos.'));
        } else {
            caixa.appendChild(el('i', 'bi bi-exclamation-triangle fs-3'));
            caixa.appendChild(el('span', null, mensagem || 'Não foi possível carregar.'));
            var botao = el('button', 'btn btn-outline-secondary btn-sm', 'Tentar de novo');
            botao.type = 'button';
            botao.addEventListener('click', carregar);
            caixa.appendChild(botao);
        }
        bloco.appendChild(caixa);
    }

    function erroGeral(mensagem) {
        if (!mensagem) {
            caixaErro.classList.add('d-none');
            return;
        }
        caixaErro.querySelector('span').textContent = mensagem;
        caixaErro.classList.remove('d-none');
    }

    // ---------------------------------------------------------------
    // filtros (RF05)
    // ---------------------------------------------------------------
    function filtros() {
        return {
            dataInicio: campoInicio.value,
            dataFim: campoFim.value,
            status: campoStatus.value,
            tipoEntrega: campoTipo.value
        };
    }

    function periodoRapido(dias) {
        var hoje = new Date();
        var inicio = new Date();
        inicio.setDate(hoje.getDate() - (dias - 1));
        campoInicio.value = dataIso(inicio);
        campoFim.value = dataIso(hoje);
        marcarPeriodoRapido();
    }

    // destaca o botao de periodo que bate com as datas escolhidas
    function marcarPeriodoRapido() {
        var hoje = dataIso(new Date());
        document.querySelectorAll('#dash-filtros [data-dias]').forEach(function (botao) {
            var inicio = new Date();
            inicio.setDate(inicio.getDate() - (Number(botao.dataset.dias) - 1));
            var ativo = campoFim.value === hoje && campoInicio.value === dataIso(inicio);
            botao.classList.toggle('active', ativo);
        });
    }

    // valida antes de chamar a API (a API valida de novo do lado dela)
    function validarFiltros() {
        if (!campoInicio.value || !campoFim.value) {
            return 'Informe a data inicial e a data final.';
        }
        if (campoInicio.value > campoFim.value) {
            return 'A data inicial não pode ser maior que a data final.';
        }
        return null;
    }

    function montarOpcoes(opcoes) {
        opcoes.status.forEach(function (status) {
            var opcao = el('option', null, STATUS[status] ? STATUS[status].nome : status);
            opcao.value = status;
            campoStatus.appendChild(opcao);
        });
        opcoes.tiposEntrega.forEach(function (tipo) {
            var opcao = el('option', null, TIPOS[tipo] || tipo);
            opcao.value = tipo;
            campoTipo.appendChild(opcao);
        });
        campoInicio.value = opcoes.dataInicioPadrao;
        campoFim.value = opcoes.dataFimPadrao;
        marcarPeriodoRapido();
    }

    // ---------------------------------------------------------------
    // RF01 - indicadores
    // ---------------------------------------------------------------
    function desenharIndicadores(d) {
        texto('kpi-faturamento', moeda.format(d.faturamento));
        texto('kpi-pedidos', numero.format(d.totalPedidos));
        texto('kpi-ticket', moeda.format(d.ticketMedio));
        texto('kpi-cancelados', numero.format(d.pedidosCancelados));

        // tendencia contra o periodo anterior do mesmo tamanho
        var apoio = document.getElementById('kpi-faturamento-apoio');
        limpar(apoio);
        if (d.variacaoFaturamento === null || d.variacaoFaturamento === undefined) {
            apoio.textContent = 'Sem vendas no período anterior para comparar';
        } else {
            var subiu = d.variacaoFaturamento >= 0;
            var seta = el('span', subiu ? 'dash-sobe' : 'dash-desce',
                (subiu ? '▲ ' : '▼ ') + decimal.format(Math.abs(d.variacaoFaturamento)) + '%');
            apoio.appendChild(seta);
            apoio.appendChild(document.createTextNode(' em relação aos ' + d.periodo.dias + ' dia(s) anteriores'));
        }

        texto('kpi-pedidos-apoio', numero.format(d.pedidosEmAndamento) + ' em andamento · '
            + numero.format(d.pedidosFinalizados) + ' finalizados');
        texto('kpi-ticket-apoio', numero.format(d.itensVendidos) + ' itens vendidos · '
            + moeda.format(d.totalDescontos) + ' em descontos');
        texto('kpi-cancelados-apoio', decimal.format(d.taxaCancelamento) + '% dos pedidos do período');

        var hora = String(d.atualizadoEm || '').substring(11, 19);
        texto('dash-atualizado', hora ? 'Atualizado às ' + hora : '');
    }

    function indicadoresIndisponiveis() {
        ['kpi-faturamento', 'kpi-pedidos', 'kpi-ticket', 'kpi-cancelados'].forEach(function (id) {
            texto(id, '–');
        });
        ['kpi-faturamento-apoio', 'kpi-pedidos-apoio', 'kpi-ticket-apoio', 'kpi-cancelados-apoio'].forEach(function (id) {
            texto(id, 'Indisponível');
        });
    }

    // ---------------------------------------------------------------
    // RF02 - graficos (Chart.js 2.9)
    // ---------------------------------------------------------------
    function opcoesBase(formatoValor, tituloTooltip) {
        return {
            responsive: true,
            maintainAspectRatio: false,
            legend: { display: false },
            animation: { duration: 300 },
            tooltips: {
                mode: 'index',
                intersect: false,
                callbacks: {
                    title: tituloTooltip,
                    label: formatoValor
                }
            },
            hover: { mode: 'index', intersect: false }
        };
    }

    function eixo(extra) {
        var base = {
            gridLines: { color: COR_GRADE, zeroLineColor: COR_GRADE, drawBorder: false },
            ticks: { fontColor: COR_TEXTO, beginAtZero: true }
        };
        Object.keys(extra || {}).forEach(function (chave) {
            base[chave] = extra[chave];
        });
        return base;
    }

    function desenhar(id, configuracao) {
        if (graficos[id]) {
            graficos[id].destroy();
        }
        graficos[id] = new Chart(document.getElementById(id).getContext('2d'), configuracao);
    }

    function desenharVendasPorDia(dias) {
        var opcoes = opcoesBase(function (item) {
            var dia = dias[item.index];
            return [' Faturamento: ' + moeda.format(dia.faturamento), ' Pedidos: ' + numero.format(dia.pedidos)];
        }, function (itens) {
            return diaMes(dias[itens[0].index].data);
        });
        opcoes.scales = {
            xAxes: [eixo({ gridLines: { display: false }, ticks: { fontColor: COR_TEXTO, maxTicksLimit: 10, maxRotation: 0 } })],
            yAxes: [eixo({ ticks: { fontColor: COR_TEXTO, beginAtZero: true, maxTicksLimit: 5,
                callback: function (valor) { return 'R$ ' + numero.format(valor); } } })]
        };

        desenhar('grafico-dia', {
            type: 'line',
            data: {
                labels: dias.map(function (d) { return diaMes(d.data); }),
                datasets: [{
                    label: 'Faturamento',
                    data: dias.map(function (d) { return d.faturamento; }),
                    borderColor: COR_SERIE,
                    backgroundColor: 'rgba(42, 120, 214, 0.10)',
                    borderWidth: 2,
                    lineTension: 0,
                    pointRadius: dias.length > 31 ? 0 : 3,
                    pointHoverRadius: 5,
                    pointBackgroundColor: COR_SERIE,
                    fill: true
                }]
            },
            options: opcoes
        });
    }

    function desenharVendasPorHora(horas) {
        // corta as horas vazias das pontas (madrugada), mas mantem os buracos do meio
        var primeira = 0;
        var ultima = horas.length - 1;
        while (primeira < ultima && horas[primeira].pedidos === 0) { primeira++; }
        while (ultima > primeira && horas[ultima].pedidos === 0) { ultima--; }
        var faixa = horas.slice(primeira, ultima + 1);

        var opcoes = opcoesBase(function (item) {
            var hora = faixa[item.index];
            return [' Pedidos: ' + numero.format(hora.pedidos), ' Faturamento: ' + moeda.format(hora.faturamento)];
        }, function (itens) {
            var hora = faixa[itens[0].index].hora;
            return hora + 'h às ' + (hora + 1) + 'h';
        });
        opcoes.scales = {
            xAxes: [eixo({ gridLines: { display: false } })],
            yAxes: [eixo({ ticks: { fontColor: COR_TEXTO, beginAtZero: true, precision: 0, maxTicksLimit: 5 } })]
        };

        desenhar('grafico-hora', {
            type: 'bar',
            data: {
                labels: faixa.map(function (h) { return h.hora + 'h'; }),
                datasets: [{
                    label: 'Pedidos',
                    data: faixa.map(function (h) { return h.pedidos; }),
                    backgroundColor: COR_SERIE,
                    hoverBackgroundColor: '#1c5cab',
                    maxBarThickness: 28
                }]
            },
            options: opcoes
        });
    }

    function desenharCategorias(categorias) {
        var opcoes = opcoesBase(function (item) {
            var c = categorias[item.index];
            return [' Valor: ' + moeda.format(c.faturamento) + ' (' + decimal.format(c.percentual) + '%)',
                ' Itens: ' + numero.format(c.quantidade)];
        }, function (itens) {
            return categorias[itens[0].index].categoria;
        });
        opcoes.tooltips.mode = 'nearest';
        opcoes.tooltips.intersect = true;
        opcoes.hover = { mode: 'nearest', intersect: true };
        opcoes.scales = {
            xAxes: [eixo({ ticks: { fontColor: COR_TEXTO, beginAtZero: true, maxTicksLimit: 5,
                callback: function (valor) { return 'R$ ' + numero.format(valor); } } })],
            yAxes: [eixo({ gridLines: { display: false } })]
        };

        desenhar('grafico-categoria', {
            type: 'horizontalBar',
            data: {
                labels: categorias.map(function (c) { return c.categoria; }),
                datasets: [{
                    label: 'Valor vendido',
                    data: categorias.map(function (c) { return c.faturamento; }),
                    backgroundColor: COR_SERIE,
                    hoverBackgroundColor: '#1c5cab',
                    maxBarThickness: 28
                }]
            },
            options: opcoes
        });
    }

    // ---------------------------------------------------------------
    // RF03 - situacao dos pedidos
    // ---------------------------------------------------------------
    function etiquetaStatus(status) {
        var info = STATUS[status] || { nome: status || '-', icone: 'bi-question-circle', tom: 'neutro' };
        var etiqueta = el('span', 'dash-etiqueta ' + info.tom);
        etiqueta.appendChild(el('i', 'bi ' + info.icone));
        etiqueta.appendChild(el('span', null, info.nome));
        return etiqueta;
    }

    function desenharStatus(lista) {
        var caixa = document.getElementById('lista-status');
        limpar(caixa);
        var maior = 0;
        lista.forEach(function (s) { maior = Math.max(maior, s.quantidade); });

        lista.forEach(function (s) {
            var info = STATUS[s.status] || { tom: 'neutro' };
            var linha = el('div', 'dash-status-linha');
            linha.title = numero.format(s.quantidade) + ' pedido(s) · ' + moeda.format(s.valor)
                + ' · ' + decimal.format(s.percentual) + '%';
            linha.appendChild(etiquetaStatus(s.status));

            var trilho = el('div', 'dash-trilho');
            var barra = el('div', 'dash-barra ' + info.tom);
            barra.style.width = (maior > 0 ? (s.quantidade / maior * 100) : 0) + '%';
            trilho.appendChild(barra);
            linha.appendChild(trilho);

            linha.appendChild(el('span', 'qtd', numero.format(s.quantidade)));
            caixa.appendChild(linha);
        });
    }

    function desenharRecentes(pedidos) {
        var corpo = document.getElementById('tabela-recentes');
        limpar(corpo);
        pedidos.forEach(function (p) {
            var linha = el('tr');
            linha.appendChild(el('td', 'text-nowrap', dataHora(p.dataHoraEntrada)));

            var cliente = el('td');
            var link = el('a', 'text-decoration-none', p.clienteNome || 'Cliente não informado');
            link.href = '/pedidos/' + encodeURIComponent(p.id);
            cliente.appendChild(link);
            cliente.appendChild(el('div', 'text-muted small',
                (TIPOS[p.tipoEntrega] || '-') + ' · ' + numero.format(p.quantidadeItens) + ' item(ns)'));
            linha.appendChild(cliente);

            var status = el('td');
            status.appendChild(etiquetaStatus(p.status));
            linha.appendChild(status);

            linha.appendChild(el('td', 'num', moeda.format(p.valorTotal)));
            corpo.appendChild(linha);
        });
    }

    // ---------------------------------------------------------------
    // RF04 - produtos mais vendidos
    // ---------------------------------------------------------------
    function desenharProdutos(produtos) {
        var corpo = document.getElementById('tabela-produtos');
        limpar(corpo);
        var maior = produtos.length ? produtos[0].quantidade : 0;

        produtos.forEach(function (p) {
            var linha = el('tr');
            linha.appendChild(el('td', 'text-muted', p.posicao + 'º'));

            var nome = el('td');
            nome.appendChild(el('div', null, p.nome || 'Produto sem nome'));
            nome.appendChild(el('div', 'text-muted small', p.categoria));
            linha.appendChild(nome);

            var celulaBarra = el('td', 'dash-col-barra');
            var trilho = el('div', 'dash-trilho');
            var barra = el('div', 'dash-barra');
            barra.style.width = (maior > 0 ? (p.quantidade / maior * 100) : 0) + '%';
            trilho.appendChild(barra);
            celulaBarra.appendChild(trilho);
            celulaBarra.title = decimal.format(p.percentual) + '% dos itens vendidos';
            linha.appendChild(celulaBarra);

            linha.appendChild(el('td', 'num fw-semibold', numero.format(p.quantidade)));
            linha.appendChild(el('td', 'num', moeda.format(p.faturamento)));
            corpo.appendChild(linha);
        });
    }

    // ---------------------------------------------------------------
    // carga dos dados
    // ---------------------------------------------------------------

    // busca um endpoint e cuida dos estados do bloco correspondente
    function carregarBloco(consulta, idBloco, caminho, parametros, desenharBloco, estaVazio) {
        estado(idBloco, 'carregando');
        return buscar(caminho, parametros).then(function (dados) {
            if (consulta !== consultaAtual) {
                return; // chegou resposta de um filtro antigo
            }
            if (estaVazio(dados)) {
                estado(idBloco, 'vazio');
                return;
            }
            estado(idBloco, 'pronto');
            desenharBloco(dados);
        }).catch(function (erro) {
            if (consulta !== consultaAtual) {
                return;
            }
            estado(idBloco, 'erro', erro.message);
            throw erro;
        });
    }

    function semVendas(lista) {
        return !lista.some(function (item) { return item.pedidos > 0; });
    }

    function listaVazia(lista) {
        return !lista || lista.length === 0;
    }

    function carregar() {
        var problema = validarFiltros();
        if (problema) {
            erroGeral(problema);
            return;
        }
        erroGeral(null);
        marcarPeriodoRapido();

        var consulta = ++consultaAtual;
        var f = filtros();
        var comLimite = function (limite) {
            return { dataInicio: f.dataInicio, dataFim: f.dataFim, status: f.status, tipoEntrega: f.tipoEntrega, limite: limite };
        };

        var indicadores = buscar('/indicadores', f).then(function (dados) {
            if (consulta === consultaAtual) {
                desenharIndicadores(dados);
            }
        }).catch(function (erro) {
            if (consulta === consultaAtual) {
                indicadoresIndisponiveis();
            }
            throw erro;
        });

        var chamadas = [
            indicadores,
            carregarBloco(consulta, 'bloco-dia', '/vendas-por-dia', f, desenharVendasPorDia, semVendas),
            carregarBloco(consulta, 'bloco-hora', '/vendas-por-hora', f, desenharVendasPorHora, semVendas),
            carregarBloco(consulta, 'bloco-categoria', '/vendas-por-categoria', f, desenharCategorias, listaVazia),
            carregarBloco(consulta, 'bloco-status', '/pedidos-por-status', f, desenharStatus, function (lista) {
                return !lista.some(function (s) { return s.quantidade > 0; });
            }),
            carregarBloco(consulta, 'bloco-produtos', '/produtos-mais-vendidos', comLimite(campoLimite.value), desenharProdutos, listaVazia),
            carregarBloco(consulta, 'bloco-recentes', '/pedidos-recentes', comLimite(8), desenharRecentes, listaVazia)
        ];

        // um aviso so no topo, com a primeira mensagem de erro
        Promise.allSettled(chamadas).then(function (resultados) {
            if (consulta !== consultaAtual) {
                return;
            }
            var falha = resultados.filter(function (r) { return r.status === 'rejected'; })[0];
            if (falha) {
                erroGeral(falha.reason && falha.reason.message ? falha.reason.message : 'Erro ao carregar o dashboard.');
            }
        });
    }

    // so o ranking, quando muda o "Top N"
    function carregarProdutos() {
        var f = filtros();
        f.limite = campoLimite.value;
        carregarBloco(consultaAtual, 'bloco-produtos', '/produtos-mais-vendidos', f, desenharProdutos, listaVazia)
            .catch(function (erro) { erroGeral(erro.message); });
    }

    // RF07 - atualizacao automatica (pausa quando a aba nao esta visivel)
    function agendarAtualizacao() {
        if (temporizador) {
            clearInterval(temporizador);
            temporizador = null;
        }
        if (campoAuto.checked) {
            temporizador = setInterval(function () {
                if (!document.hidden) {
                    carregar();
                }
            }, INTERVALO_ATUALIZACAO);
        }
    }

    // ---------------------------------------------------------------
    // eventos
    // ---------------------------------------------------------------
    document.getElementById('dash-filtros').addEventListener('submit', function (evento) {
        evento.preventDefault();
        carregar();
    });

    document.querySelectorAll('#dash-filtros [data-dias]').forEach(function (botao) {
        botao.addEventListener('click', function () {
            periodoRapido(Number(botao.dataset.dias));
            carregar();
        });
    });

    document.getElementById('dash-limpar').addEventListener('click', function () {
        campoStatus.value = '';
        campoTipo.value = '';
        periodoRapido(7);
        carregar();
    });

    document.getElementById('dash-atualizar').addEventListener('click', carregar);
    campoLimite.addEventListener('change', carregarProdutos);
    campoAuto.addEventListener('change', agendarAtualizacao);
    document.addEventListener('visibilitychange', function () {
        if (!document.hidden && campoAuto.checked) {
            carregar();
        }
    });

    // ---------------------------------------------------------------
    // inicio: primeiro as opcoes de filtro, depois os dados
    // ---------------------------------------------------------------
    buscar('/filtros').then(montarOpcoes).catch(function () {
        // sem as opcoes da API ainda da para usar o dashboard com o periodo padrao
        periodoRapido(7);
    }).then(function () {
        carregar();
        agendarAtualizacao();
    });
})();
