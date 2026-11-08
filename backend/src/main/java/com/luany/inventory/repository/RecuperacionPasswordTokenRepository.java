package com.luany.inventory.repository;

import com.luany.inventory.model.RecuperacionPasswordToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RecuperacionPasswordTokenRepository
        extends JpaRepository<RecuperacionPasswordToken, Long> {

    Optional<RecuperacionPasswordToken> findByTokenHashAndUtilizadoEnIsNull(String tokenHash);

    @Modifying
    @Query("""
            UPDATE RecuperacionPasswordToken t
            SET t.utilizadoEn = :ahora
            WHERE t.usuario.id = :usuarioId AND t.utilizadoEn IS NULL
            """)
    int invalidarNoUtilizados(
            @Param("usuarioId") Long usuarioId,
            @Param("ahora") LocalDateTime ahora);

    @Modifying
    @Query("""
            UPDATE RecuperacionPasswordToken t
            SET t.utilizadoEn = :ahora
            WHERE t.tokenHash = :tokenHash AND t.utilizadoEn IS NULL
            """)
    int invalidarNoUtilizado(
            @Param("tokenHash") String tokenHash,
            @Param("ahora") LocalDateTime ahora);

    @Modifying
    @Query("""
            UPDATE RecuperacionPasswordToken t
            SET t.utilizadoEn = :ahora
            WHERE t.tokenHash = :tokenHash
              AND t.utilizadoEn IS NULL
              AND t.expiraEn > :ahora
            """)
    int consumirSiVigente(
            @Param("tokenHash") String tokenHash,
            @Param("ahora") LocalDateTime ahora);
}
