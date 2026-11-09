package com.luany.inventory.repository;

import com.luany.inventory.model.NotaVenta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotaVentaRepository extends JpaRepository<NotaVenta, Long> {

    boolean existsByClienteId(Long clienteId);
}
