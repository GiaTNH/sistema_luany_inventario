package com.luany.inventory.repository;

import com.luany.inventory.model.AuditoriaSeguridad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditoriaSeguridadRepository extends JpaRepository<AuditoriaSeguridad, Long> {
    List<AuditoriaSeguridad> findTop200ByOrderByFechaDesc();

    boolean existsByTipoEventoAndFechaAfter(String tipoEvento, LocalDateTime fecha);
}
