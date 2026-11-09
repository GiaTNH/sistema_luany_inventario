package com.luany.inventory.repository;

import com.luany.inventory.model.RecepcionPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecepcionPedidoRepository extends JpaRepository<RecepcionPedido, Long> {

    List<RecepcionPedido> findByPedidoIdOrderByFechaDescIdDesc(Long pedidoId);

    Optional<RecepcionPedido> findByClaveIdempotencia(String claveIdempotencia);
}
