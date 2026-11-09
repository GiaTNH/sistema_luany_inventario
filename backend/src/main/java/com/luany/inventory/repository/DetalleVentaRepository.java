package com.luany.inventory.repository;

import com.luany.inventory.model.DetalleVenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {

    List<DetalleVenta> findByNotaVentaIdOrderById(Long notaVentaId);

    int countByNotaVentaId(Long notaVentaId);
}
