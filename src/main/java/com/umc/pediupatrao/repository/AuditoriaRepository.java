package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Auditoria;
import java.util.List;
import org.springframework.data.repository.Repository;

// Aqui NAO uso MongoRepository de proposito.
// Extendendo so Repository eu escolho os metodos que existem,
// entao nao tem save (update), delete nem deleteAll para a auditoria.
// So da pra inserir registro novo e consultar.
public interface AuditoriaRepository extends Repository<Auditoria, String> {

    Auditoria insert(Auditoria auditoria);

    List<Auditoria> findAllByOrderByDataHoraDesc();

    List<Auditoria> findByEntidadeIdOrderByDataHoraDesc(String entidadeId);
}
