package com.luany.inventory.repository;

import com.luany.inventory.model.BloqueoIp;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface BloqueoIpRepository extends JpaRepository<BloqueoIp, String> {

    @Query("SELECT b FROM BloqueoIp b WHERE b.ip = :ip AND b.bloqueadoHasta > :ahora")
    Optional<BloqueoIp> buscarBloqueoActivo(@Param("ip") String ip, @Param("ahora") LocalDateTime ahora);

    /**
     * UPSERT atómico que gestiona los cinco casos en una sola sentencia SQL:
     *
     *  - IP inexistente               → INSERT con intentos = 1.
     *  - Bloqueo vigente              → conserva todos los campos sin modificarlos.
     *  - Bloqueo expirado             → reinicia intentos a 1, primera_vez a :ahora
     *                                   y bloqueado_hasta a NULL.
     *  - Ventana expirada sin bloqueo → reinicia intentos a 1 y primera_vez a :ahora.
     *  - Ventana vigente sin bloqueo  → incrementa intentos en 1.
     *
     * Seguridad frente a concurrencia:
     * MySQL evalúa las asignaciones de ON DUPLICATE KEY UPDATE de izquierda a derecha.
     * El orden actual es seguro porque cada CASE solo lee columnas que todavía no han
     * sido modificadas por una asignación anterior en la misma sentencia:
     *   - intentos    lee bloqueado_hasta y primera_vez  (ninguna asignada antes).
     *   - primera_vez lee bloqueado_hasta y primera_vez  (ninguna asignada antes).
     *   - ultima_vez  lee bloqueado_hasta                (no asignada antes).
     *   - bloqueado_hasta no lee ninguna columna de la fila.
     *
     * MySQL serializa ON DUPLICATE KEY UPDATE a nivel de fila, por lo que dos
     * solicitudes concurrentes desde una IP nueva producen INSERT + UPDATE
     * en lugar de dos INSERTs, garantizando que no se pierdan intentos.
     */
    @Modifying
    @Query(value = """
        INSERT INTO bloqueos_ip (ip, intentos, primera_vez, ultima_vez, bloqueado_hasta)
        VALUES (:ip, 1, :ahora, :ahora, NULL)
        ON DUPLICATE KEY UPDATE
          intentos = CASE
            WHEN bloqueado_hasta IS NOT NULL AND bloqueado_hasta >  :ahora THEN intentos
            WHEN bloqueado_hasta IS NOT NULL AND bloqueado_hasta <= :ahora THEN 1
            WHEN primera_vez < :ventanaInicio                              THEN 1
            ELSE intentos + 1
          END,
          primera_vez = CASE
            WHEN bloqueado_hasta IS NOT NULL AND bloqueado_hasta >  :ahora THEN primera_vez
            WHEN bloqueado_hasta IS NOT NULL AND bloqueado_hasta <= :ahora THEN :ahora
            WHEN primera_vez < :ventanaInicio                              THEN :ahora
            ELSE primera_vez
          END,
          ultima_vez = CASE
            WHEN bloqueado_hasta IS NOT NULL AND bloqueado_hasta > :ahora THEN ultima_vez
            ELSE :ahora
          END,
          bloqueado_hasta = CASE
            WHEN bloqueado_hasta IS NOT NULL AND bloqueado_hasta > :ahora THEN bloqueado_hasta
            ELSE NULL
          END
    """, nativeQuery = true)
    void upsertIntentoFallido(
            @Param("ip") String ip,
            @Param("ahora") LocalDateTime ahora,
            @Param("ventanaInicio") LocalDateTime ventanaInicio);

    /**
     * SELECT FOR UPDATE sobre la fila ya existente tras el upsert.
     * Garantiza exclusión mutua para aplicar bloqueado_hasta al quinto intento.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BloqueoIp b WHERE b.ip = :ip")
    Optional<BloqueoIp> buscarParaBloqueo(@Param("ip") String ip);
}
