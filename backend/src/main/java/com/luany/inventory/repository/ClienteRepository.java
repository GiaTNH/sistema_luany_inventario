package com.luany.inventory.repository;

import com.luany.inventory.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByNumeroDocumentoIgnoreCase(String numeroDocumento);

    List<Cliente> findByNombreContainingIgnoreCaseOrNumeroDocumentoContainingIgnoreCase(
            String nombre, String numeroDocumento, Sort sort);

    @Query("SELECT c FROM Cliente c WHERE LOWER(c.nombre) LIKE LOWER(CONCAT('%', :nombre, '%'))")
    Page<Cliente> buscarPorNombreParaAsistente(
            @Param("nombre") String nombre,
            Pageable pageable);
}
