package com.luany.inventory.repository;

import com.luany.inventory.model.DetalleRecepcionPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DetalleRecepcionPedidoRepository extends JpaRepository<DetalleRecepcionPedido, Long> {

    @Query("SELECT COALESCE(SUM(d.cantidad), 0) FROM DetalleRecepcionPedido d "
            + "WHERE d.detallePedido.id = :detallePedidoId")
    Long totalRecibido(@Param("detallePedidoId") Long detallePedidoId);

    List<DetalleRecepcionPedido> findByRecepcionIdOrderById(Long recepcionId);
}
