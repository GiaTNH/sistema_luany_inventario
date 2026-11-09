package com.luany.inventory.repository;

import com.luany.inventory.model.NotaVenta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotaVentaRepository extends JpaRepository<NotaVenta, Long> {

    boolean existsByClienteId(Long clienteId);

    @EntityGraph(attributePaths = {"cliente", "usuario"})
    List<NotaVenta> findAllByOrderByFechaEmisionDesc();

    @Query("SELECT n FROM NotaVenta n JOIN FETCH n.cliente JOIN FETCH n.usuario "
            + "WHERE LOWER(n.numeroNota) LIKE LOWER(CONCAT('%', :termino, '%')) "
            + "OR LOWER(n.cliente.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) "
            + "ORDER BY n.fechaEmision DESC")
    List<NotaVenta> buscar(@Param("termino") String termino);
}
