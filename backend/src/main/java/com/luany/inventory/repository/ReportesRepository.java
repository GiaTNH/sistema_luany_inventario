package com.luany.inventory.repository;

import com.luany.inventory.model.Movimiento;
import com.luany.inventory.model.TipoMovimiento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ReportesRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<Object[]> ventasPorDia(LocalDateTime desde, LocalDateTime hastaExclusivo) {
        return entityManager.createNativeQuery("""
                SELECT DATE(fecha_emision), COALESCE(SUM(total), 0), COUNT(*)
                FROM notas_venta
                WHERE fecha_emision >= :desde AND fecha_emision < :hasta
                GROUP BY DATE(fecha_emision)
                ORDER BY DATE(fecha_emision)
                """)
                .setParameter("desde", desde)
                .setParameter("hasta", hastaExclusivo)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> productosMasVendidos(
            LocalDateTime desde, LocalDateTime hastaExclusivo, int limite) {
        return entityManager.createNativeQuery("""
                SELECT p.id, p.nombre, SUM(dv.cantidad)
                FROM detalle_venta dv
                JOIN notas_venta nv ON nv.id = dv.nota_venta_id
                JOIN productos p ON p.id = dv.producto_id
                WHERE nv.fecha_emision >= :desde AND nv.fecha_emision < :hasta
                GROUP BY p.id, p.nombre
                ORDER BY SUM(dv.cantidad) DESC, p.nombre ASC
                LIMIT :limite
                """)
                .setParameter("desde", desde)
                .setParameter("hasta", hastaExclusivo)
                .setParameter("limite", limite)
                .getResultList();
    }

    public List<Movimiento> movimientos(
            LocalDateTime desde,
            LocalDateTime hastaExclusivo,
            Long productoId,
            TipoMovimiento tipo,
            int offset,
            int tamano) {
        StringBuilder where = new StringBuilder(" WHERE m.fecha >= :desde AND m.fecha < :hasta");
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("desde", desde);
        parametros.put("hasta", hastaExclusivo);
        if (productoId != null) {
            where.append(" AND m.producto.id = :productoId");
            parametros.put("productoId", productoId);
        }
        if (tipo != null) {
            where.append(" AND m.tipo = :tipo");
            parametros.put("tipo", tipo);
        }

        TypedQuery<Movimiento> query = entityManager.createQuery(
                "SELECT m FROM Movimiento m JOIN FETCH m.producto" + where + " ORDER BY m.fecha DESC, m.id DESC",
                Movimiento.class);
        aplicarParametros(query, parametros);
        return query.setFirstResult(offset).setMaxResults(tamano).getResultList();
    }

    public long contarMovimientos(
            LocalDateTime desde, LocalDateTime hastaExclusivo, Long productoId, TipoMovimiento tipo) {
        StringBuilder where = new StringBuilder(" WHERE m.fecha >= :desde AND m.fecha < :hasta");
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("desde", desde);
        parametros.put("hasta", hastaExclusivo);
        if (productoId != null) {
            where.append(" AND m.producto.id = :productoId");
            parametros.put("productoId", productoId);
        }
        if (tipo != null) {
            where.append(" AND m.tipo = :tipo");
            parametros.put("tipo", tipo);
        }
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(m.id) FROM Movimiento m" + where, Long.class);
        aplicarParametros(query, parametros);
        return query.getSingleResult();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> resumenEstadosPedido(LocalDateTime desde, LocalDateTime hastaExclusivo) {
        return entityManager.createQuery("""
                SELECT p.estado, COUNT(p.id), MIN(p.fechaPedido), MAX(p.fechaPedido)
                FROM Pedido p
                WHERE p.fechaPedido >= :desde AND p.fechaPedido < :hasta
                GROUP BY p.estado
                ORDER BY p.estado
                """)
                .setParameter("desde", desde)
                .setParameter("hasta", hastaExclusivo)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> pedidosConRecepciones(LocalDateTime desde, LocalDateTime hastaExclusivo) {
        return entityManager.createNativeQuery("""
                SELECT p.id, p.fecha_pedido, pr.nombre, p.estado,
                       COALESCE((SELECT SUM(dp.cantidad) FROM detalle_pedido dp WHERE dp.pedido_id = p.id), 0),
                       COALESCE((
                           SELECT SUM(drp.cantidad)
                           FROM detalle_recepcion_pedido drp
                           JOIN detalle_pedido dp2 ON dp2.id = drp.detalle_pedido_id
                           WHERE dp2.pedido_id = p.id
                       ), 0),
                       (SELECT MAX(r.fecha)
                        FROM recepciones_pedido r
                        WHERE r.pedido_id = p.id)
                FROM pedidos p
                JOIN proveedores pr ON pr.id = p.proveedor_id
                WHERE p.fecha_pedido >= :desde AND p.fecha_pedido < :hasta
                ORDER BY p.fecha_pedido DESC, p.id DESC
                """)
                .setParameter("desde", desde)
                .setParameter("hasta", hastaExclusivo)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> recepcionesPorDia(LocalDateTime desde, LocalDateTime hastaExclusivo) {
        return entityManager.createNativeQuery("""
                SELECT DATE(r.fecha), COUNT(DISTINCT r.id), COALESCE(SUM(drp.cantidad), 0)
                FROM recepciones_pedido r
                LEFT JOIN detalle_recepcion_pedido drp ON drp.recepcion_id = r.id
                WHERE r.fecha >= :desde AND r.fecha < :hasta
                GROUP BY DATE(r.fecha)
                ORDER BY DATE(r.fecha)
                """)
                .setParameter("desde", desde)
                .setParameter("hasta", hastaExclusivo)
                .getResultList();
    }

    private void aplicarParametros(TypedQuery<?> query, Map<String, Object> parametros) {
        parametros.forEach(query::setParameter);
    }
}
