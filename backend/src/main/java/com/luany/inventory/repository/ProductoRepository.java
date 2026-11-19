package com.luany.inventory.repository;

import com.luany.inventory.model.Producto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    @Query("SELECT p FROM Producto p WHERE p.activo = true AND p.stockActual <= p.stockMinimo ORDER BY p.stockActual ASC")
    List<Producto> findStockBajo();

    @Query("SELECT p FROM Producto p WHERE p.activo = true AND p.stockActual <= p.stockMinimo ORDER BY p.stockActual ASC")
    List<Producto> findStockBajo(Pageable pageable);

    @Query("SELECT p FROM Producto p WHERE p.activo = true AND LOWER(p.nombre) LIKE LOWER(CONCAT('%', :nombre, '%'))")
    List<Producto> buscarPorNombre(@Param("nombre") String nombre);

    Optional<Producto> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<Producto> findAllByActivoTrue(Sort sort);

    Page<Producto> findAllByActivoTrue(Pageable pageable);

    List<Producto> findAllByActivoTrue();

    Optional<Producto> findByIdAndActivoTrue(Long id);

    @Query("SELECT p FROM Producto p WHERE p.activo = true AND p.categoria.id = :categoriaId")
    List<Producto> findPorCategoria(@Param("categoriaId") Long categoriaId);
}
