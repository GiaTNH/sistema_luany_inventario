package com.luany.inventory.repository;

import com.luany.inventory.model.DetallePedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Long> {

    List<DetallePedido> findByPedidoIdOrderById(Long pedidoId);
}
