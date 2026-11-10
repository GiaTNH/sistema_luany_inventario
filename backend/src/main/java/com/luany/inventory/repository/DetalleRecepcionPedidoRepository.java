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

    @Query("SELECT d.detallePedido.pedido.id, COALESCE(SUM(d.cantidad), 0) "
            + "FROM DetalleRecepcionPedido d "
            + "WHERE d.detallePedido.pedido.id IN :pedidoIds "
            + "GROUP BY d.detallePedido.pedido.id")
    List<Object[]> cantidadesRecibidasPorPedido(@Param("pedidoIds") List<Long> pedidoIds);
}
