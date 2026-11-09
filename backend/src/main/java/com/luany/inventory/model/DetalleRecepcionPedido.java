package com.luany.inventory.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "detalle_recepcion_pedido")
@Getter
@Setter
@NoArgsConstructor
public class DetalleRecepcionPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recepcion_id", nullable = false)
    private RecepcionPedido recepcion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "detalle_pedido_id", nullable = false)
    private DetallePedido detallePedido;

    @Column(nullable = false)
    private Integer cantidad;

    @OneToOne(optional = false)
    @JoinColumn(name = "movimiento_id", nullable = false, unique = true)
    private Movimiento movimiento;
}
