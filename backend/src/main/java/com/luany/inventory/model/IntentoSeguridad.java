package com.luany.inventory.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "intentos_seguridad")
@Getter
@Setter
@NoArgsConstructor
public class IntentoSeguridad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String identificador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(length = 45)
    private String ip;

    @Column(nullable = false, length = 30)
    private String resultado;

    @Column(nullable = false, length = 80)
    private String tipo;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(length = 200)
    private String descripcion;
}
