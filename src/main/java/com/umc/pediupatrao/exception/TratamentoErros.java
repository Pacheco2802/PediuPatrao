package com.umc.pediupatrao.exception;

import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// Trata os erros das APIs REST e devolve um JSON com a mensagem
// (as telas Thymeleaf tratam o erro no proprio HomeController)
@RestControllerAdvice(annotations = RestController.class)
public class TratamentoErros {

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, String>> regraNegocio(RegraNegocioException e) {
        return resposta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // parametro com tipo errado (ex: limite=abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro " + e.getName() + " inválido.");
    }

    // banco fora do ar ou sem conexao
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> bancoIndisponivel(DataAccessException e) {
        return resposta(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível consultar o banco de dados. Tente novamente em instantes.");
    }

    private ResponseEntity<Map<String, String>> resposta(HttpStatus status, String mensagem) {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("erro", mensagem);
        return ResponseEntity.status(status).body(corpo);
    }
}
