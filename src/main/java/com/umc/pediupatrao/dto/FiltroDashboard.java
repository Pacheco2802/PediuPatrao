package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.exception.RegraNegocioException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

// Filtros de consulta do dashboard (RF05).
// Toda a validacao dos parametros fica aqui, assim todos os endpoints
// respondem do mesmo jeito quando o filtro vem errado (400 com {"erro": "..."}).
public record FiltroDashboard(LocalDate dataInicio, LocalDate dataFim, String status, String tipoEntrega) {

    public static final int PERIODO_PADRAO_DIAS = 7;
    public static final int PERIODO_MAXIMO_DIAS = 366;

    public static final List<String> STATUS_VALIDOS = List.of(
            "RECEBIDO", "EM_PREPARACAO", "PRONTO", "SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO");

    public static final List<String> TIPOS_ENTREGA_VALIDOS = List.of("ENTREGA", "RETIRADA");

    // Monta o filtro a partir dos parametros da requisicao (todos opcionais).
    // Sem datas = ultimos 7 dias (contando hoje).
    public static FiltroDashboard criar(String dataInicio, String dataFim, String status, String tipoEntrega) {
        LocalDate fim = vazio(dataFim) ? LocalDate.now() : converterData("dataFim", dataFim);
        LocalDate inicio = vazio(dataInicio)
                ? fim.minusDays(PERIODO_PADRAO_DIAS - 1)
                : converterData("dataInicio", dataInicio);

        if (inicio.isAfter(fim)) {
            throw new RegraNegocioException("A data inicial não pode ser maior que a data final.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > PERIODO_MAXIMO_DIAS) {
            throw new RegraNegocioException("O período máximo de consulta é de " + PERIODO_MAXIMO_DIAS + " dias.");
        }

        String statusFiltro = null;
        if (!vazio(status)) {
            statusFiltro = status.trim().toUpperCase();
            if (!STATUS_VALIDOS.contains(statusFiltro)) {
                throw new RegraNegocioException("Status inválido. Use um destes: " + String.join(", ", STATUS_VALIDOS) + ".");
            }
        }

        String tipoFiltro = null;
        if (!vazio(tipoEntrega)) {
            tipoFiltro = tipoEntrega.trim().toUpperCase();
            if (!TIPOS_ENTREGA_VALIDOS.contains(tipoFiltro)) {
                throw new RegraNegocioException("Tipo de entrega inválido. Use ENTREGA ou RETIRADA.");
            }
        }

        return new FiltroDashboard(inicio, fim, statusFiltro, tipoFiltro);
    }

    // inicio do primeiro dia (inclusive)
    public LocalDateTime inicioPeriodo() {
        return dataInicio.atStartOfDay();
    }

    // inicio do dia seguinte ao ultimo (exclusive)
    public LocalDateTime fimPeriodo() {
        return dataFim.plusDays(1).atStartOfDay();
    }

    public long quantidadeDias() {
        return ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
    }

    // mesmo filtro, so que no periodo imediatamente anterior e com o mesmo tamanho
    // (usado para comparar o faturamento e mostrar a tendencia)
    public FiltroDashboard periodoAnterior() {
        long dias = quantidadeDias();
        return new FiltroDashboard(dataInicio.minusDays(dias), dataInicio.minusDays(1), status, tipoEntrega);
    }

    private static LocalDate converterData(String nome, String valor) {
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new RegraNegocioException("Parâmetro " + nome + " inválido. Use o formato AAAA-MM-DD (ex: 2026-11-10).");
        }
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }
}
