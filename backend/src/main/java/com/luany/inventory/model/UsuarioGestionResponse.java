package com.luany.inventory.model;

public record UsuarioGestionResponse(
        Long id,
        String nombre,
        String apellido,
        String email,
        String nombreUsuario,
        String rol,
        String estado,
        boolean debeCambiarPassword,
        boolean bloqueadoManual,
        java.time.LocalDateTime bloqueadoHasta
) {
    public UsuarioGestionResponse(
            Long id,
            String nombre,
            String apellido,
            String email,
            String nombreUsuario,
            String rol,
            String estado,
            boolean debeCambiarPassword) {
        this(id, nombre, apellido, email, nombreUsuario, rol, estado,
                debeCambiarPassword, false, null);
    }
}
