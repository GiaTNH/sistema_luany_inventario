package com.luany.inventory.repository;

import com.luany.inventory.model.IntentoSeguridad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface IntentoSeguridadRepository extends JpaRepository<IntentoSeguridad, Long> {
    long countByIdentificadorIgnoreCaseAndResultadoAndFechaAfter(
            String identificador, String resultado, LocalDateTime fecha);

    long countByIpAndResultadoAndFechaAfter(String ip, String resultado, LocalDateTime fecha);

    List<IntentoSeguridad> findTop100ByOrderByFechaDesc();
}
