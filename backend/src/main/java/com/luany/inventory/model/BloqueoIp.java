package com.luany.inventory.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "bloqueos_ip")
@Getter
@Setter
@NoArgsConstructor
public class BloqueoIp {

    @Id
    @Column(name = "ip", length = 45, nullable = false)
    private String ip;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "primera_vez", nullable = false)
    private LocalDateTime primeraVez;

    @Column(name = "ultima_vez", nullable = false)
    private LocalDateTime ultimaVez;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;
}
