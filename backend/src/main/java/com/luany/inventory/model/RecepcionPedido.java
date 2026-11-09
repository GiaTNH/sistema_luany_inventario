package com.luany.inventory.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "recepciones_pedido")
@Getter
@Setter
@NoArgsConstructor
public class RecepcionPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "clave_idempotencia", nullable = false, unique = true, length = 36)
    private String claveIdempotencia;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
}
