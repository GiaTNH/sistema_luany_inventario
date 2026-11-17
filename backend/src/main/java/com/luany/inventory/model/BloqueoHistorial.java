package com.luany.inventory.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "bloqueos_historial")
@Getter
@Setter
@NoArgsConstructor
public class BloqueoHistorial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 20)
    private String accion;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "fecha_vigencia_hasta")
    private LocalDateTime fechaVigenciaHasta;

    @Column(length = 200)
    private String motivo;

    @Column(length = 120)
    private String realizadoPor;

    @Column(length = 45)
    private String ip;
}
