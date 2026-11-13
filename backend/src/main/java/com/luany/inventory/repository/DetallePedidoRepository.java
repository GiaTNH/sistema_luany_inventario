package com.luany.inventory.repository;

import com.luany.inventory.model.DetallePedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Long> {

    List<DetallePedido> findByPedidoIdOrderById(Long pedidoId);

    @Query("SELECT d.pedido.id, COALESCE(SUM(d.cantidad), 0) "
            + "FROM DetallePedido d WHERE d.pedido.id IN :pedidoIds GROUP BY d.pedido.id")
    List<Object[]> cantidadesSolicitadasPorPedido(@Param("pedidoIds") List<Long> pedidoIds);

    @Query("SELECT d.pedido.id, d.producto.id "
            + "FROM DetallePedido d WHERE d.pedido.id IN :pedidoIds")
    List<Object[]> productosPorPedido(@Param("pedidoIds") List<Long> pedidoIds);
}
