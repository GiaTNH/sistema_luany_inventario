package com.luany.inventory.repository;

import com.luany.inventory.model.BloqueoHistorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BloqueoHistorialRepository extends JpaRepository<BloqueoHistorial, Long> {
    List<BloqueoHistorial> findTop200ByOrderByFechaDesc();
}
