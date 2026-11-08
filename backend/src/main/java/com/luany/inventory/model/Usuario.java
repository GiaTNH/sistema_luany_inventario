package com.luany.inventory.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuarios")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(name = "nombre_usuario", nullable = false, unique = true, length = 100)
    private String nombreUsuario;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 20)
    private String rol = "EMPLEADO";

    @Column(length = 100)
    private String apellido;

    @Column(length = 20)
    private String estado = "ACTIVO";

    @Column(name = "debe_cambiar_password", nullable = false,
            columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean debeCambiarPassword = false;
}
