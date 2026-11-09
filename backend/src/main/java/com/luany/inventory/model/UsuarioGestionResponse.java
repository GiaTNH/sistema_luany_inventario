package com.luany.inventory.model;

public record UsuarioGestionResponse(
        Long id,
        String nombre,
        String apellido,
        String email,
        String nombreUsuario,
        String rol,
        String estado,
        boolean debeCambiarPassword
) {
}
