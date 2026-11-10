package com.luany.inventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "proveedores")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 20)
    private String telefono;

    @Column(length = 20)
    private String ruc;

    @Column(length = 120)
    @Email(message = "El correo del proveedor no tiene un formato válido")
    @Size(max = 120, message = "El correo del proveedor no puede superar 120 caracteres")
    private String correo;

    @Column(length = 200)
    private String direccion;

    @Column(length = 20)
    private String estado = "ACTIVO";
}
