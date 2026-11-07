package com.luany.inventory.repository;

import com.luany.inventory.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    @Query("SELECT p FROM Producto p WHERE p.stockActual <= p.stockMinimo ORDER BY p.stockActual ASC")
    List<Producto> findStockBajo();

    @Query("SELECT p FROM Producto p WHERE LOWER(p.nombre) LIKE LOWER(CONCAT('%', :nombre, '%'))")
    List<Producto> buscarPorNombre(@Param("nombre") String nombre);

    Optional<Producto> findByCodigo(String codigo);

    @Query("SELECT p FROM Producto p WHERE p.categoria.id = :categoriaId")
    List<Producto> findPorCategoria(@Param("categoriaId") Long categoriaId);
}
