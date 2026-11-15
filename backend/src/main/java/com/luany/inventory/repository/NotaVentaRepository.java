package com.luany.inventory.repository;

import com.luany.inventory.model.NotaVenta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface NotaVentaRepository extends JpaRepository<NotaVenta, Long> {

    boolean existsByClienteId(Long clienteId);

    @EntityGraph(attributePaths = {"cliente", "usuario"})
    List<NotaVenta> findAllByOrderByFechaEmisionDesc();

    @EntityGraph(attributePaths = {"cliente", "usuario"})
    Page<NotaVenta> findAllByOrderByFechaEmisionDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "usuario"})
    Page<NotaVenta> findByFechaEmisionGreaterThanEqualAndFechaEmisionLessThanOrderByFechaEmisionDesc(
            LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "usuario"})
    @Query("SELECT n FROM NotaVenta n WHERE n.fechaEmision >= :desde AND n.fechaEmision < :hasta "
            + "AND (:cliente IS NULL OR LOWER(n.cliente.nombre) LIKE LOWER(CONCAT('%', :cliente, '%'))) "
            + "ORDER BY n.fechaEmision DESC")
    Page<NotaVenta> buscarParaAsistente(
            @Param("cliente") String cliente,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            Pageable pageable);

    @Query("SELECT n FROM NotaVenta n JOIN FETCH n.cliente JOIN FETCH n.usuario "
            + "WHERE LOWER(n.numeroNota) LIKE LOWER(CONCAT('%', :termino, '%')) "
            + "OR LOWER(n.cliente.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) "
            + "ORDER BY n.fechaEmision DESC")
    List<NotaVenta> buscar(@Param("termino") String termino);
}
