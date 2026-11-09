package com.luany.inventory.repository;

import com.luany.inventory.model.Cliente;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByNumeroDocumentoIgnoreCase(String numeroDocumento);

    List<Cliente> findByNombreContainingIgnoreCaseOrNumeroDocumentoContainingIgnoreCase(
            String nombre, String numeroDocumento, Sort sort);
}
