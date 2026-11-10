package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// API da auditoria: somente consulta (GET).
// Nao existe POST, PUT nem DELETE aqui, os registros sao criados pelos services.
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    @Autowired
    private AuditoriaService auditoriaService;

    @GetMapping
    public List<Auditoria> listar(@RequestParam(required = false) String entidadeId) {
        return auditoriaService.listar(entidadeId);
    }
}
