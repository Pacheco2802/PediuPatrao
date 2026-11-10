package com.umc.pediupatrao.exception;

// Erro usado quando alguma regra de negocio nao e atendida
// (ex: desconto maior que 20%, transicao de status invalida...)
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
