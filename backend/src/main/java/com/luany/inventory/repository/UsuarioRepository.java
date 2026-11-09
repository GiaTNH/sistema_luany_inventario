package com.luany.inventory.repository;

import com.luany.inventory.model.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("SELECT u FROM Usuario u WHERE u.email = :email")
    Optional<Usuario> buscarPorEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.email = :email")
    Optional<Usuario> buscarPorEmailParaActualizar(@Param("email") String email);

    boolean existsByNombreUsuario(String nombreUsuario);

    long countByRol(String rol);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.id = :id")
    Optional<Usuario> buscarPorIdParaActualizar(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.rol = 'ADMIN'")
    List<Usuario> buscarAdministradoresParaActualizar();
}