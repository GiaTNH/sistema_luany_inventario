package com.luany.inventory.repository;

import com.luany.inventory.model.Pedido;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> buscarPorIdBloqueando(@Param("id") Long id);

    @Query("SELECT p FROM Pedido p JOIN FETCH p.proveedor JOIN FETCH p.usuario")
    List<Pedido> listarConProveedorYUsuario(Sort sort);

    @Query("SELECT p FROM Pedido p JOIN FETCH p.proveedor JOIN FETCH p.usuario "
            + "WHERE p.fechaPedido >= :desde AND p.fechaPedido < :hasta "
            + "ORDER BY p.fechaPedido DESC, p.id DESC")
    Page<Pedido> listarParaAsistente(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            Pageable pageable);
}
