package com.umc.pediupatrao.exception;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Trata os erros das APIs REST e devolve um JSON com a mensagem
// (as telas Thymeleaf tratam o erro no proprio HomeController)
@RestControllerAdvice(annotations = RestController.class)
public class TratamentoErros {

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, String>> regraNegocio(RegraNegocioException e) {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("erro", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo);
    }
}
