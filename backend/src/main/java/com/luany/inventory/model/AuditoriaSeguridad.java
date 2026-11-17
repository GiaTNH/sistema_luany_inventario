package com.luany.inventory.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria_seguridad")
@Getter
@Setter
@NoArgsConstructor
public class AuditoriaSeguridad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "usuario_relacionado", length = 120)
    private String usuarioRelacionado;

    @Column(name = "tipo_evento", nullable = false, length = 80)
    private String tipoEvento;

    @Column(length = 30)
    private String resultado;

    @Column(length = 45)
    private String ip;

    @Column(length = 500)
    private String descripcion;
}
