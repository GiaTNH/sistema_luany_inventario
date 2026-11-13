package com.luany.inventory.service;

import com.luany.inventory.model.Proveedor;
import com.luany.inventory.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private static final Pattern FORMATO_CORREO = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_INACTIVO = "INACTIVO";

    private final ProveedorRepository proveedorRepository;

    public List<Proveedor> listar() {
        return proveedorRepository.findAll();
    }

    public Proveedor crear(Proveedor datos) {
        validar(datos);
        Proveedor proveedor = new Proveedor();
        aplicarDatos(proveedor, datos);
        return proveedorRepository.save(proveedor);
    }

    public Proveedor actualizar(Long id, Proveedor datos) {
        validar(datos);
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no existe"));
        aplicarDatos(proveedor, datos);
        return proveedorRepository.save(proveedor);
    }

    @Transactional
    public void eliminar(Long id) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no existe"));
        try {
            proveedorRepository.delete(proveedor);
            proveedorRepository.flush();
        } catch (DataIntegrityViolationException error) {
            throw new IllegalArgumentException(
                    "No se puede eliminar el proveedor porque está asociado a productos, pedidos u otros registros.");
        }
    }

    // Los campos opcionales (ruc, correo, direccion, estado) solo se cambian
    // si vienen en la peticion; asi el formulario actual no los borra.
    private void aplicarDatos(Proveedor proveedor, Proveedor datos) {
        proveedor.setNombre(datos.getNombre().trim());
        proveedor.setTelefono(datos.getTelefono());
        if (datos.getRuc() != null) {
            String ruc = datos.getRuc().trim();
            if (ruc.isEmpty()) {
                proveedor.setRuc(null);
            } else {
                proveedorRepository.findByRuc(ruc)
                        .filter(otro -> !otro.getId().equals(proveedor.getId()))
                        .ifPresent(otro -> {
                            throw new IllegalArgumentException("Ya existe un proveedor con ese RUC");
                        });
                proveedor.setRuc(ruc);
            }
        }
        if (datos.getCorreo() != null) {
            proveedor.setCorreo(vacioANulo(datos.getCorreo()));
        }
        if (datos.getDireccion() != null) {
            proveedor.setDireccion(vacioANulo(datos.getDireccion()));
        }
        if (datos.getEstado() != null && !datos.getEstado().isBlank()) {
            proveedor.setEstado(normalizarEstado(datos.getEstado()));
        }
    }

    private String vacioANulo(String valor) {
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private void validar(Proveedor datos) {
        if (datos.getNombre() == null || datos.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del proveedor es obligatorio");
        }
        validarLongitud(datos.getNombre().trim(), 120, "El nombre del proveedor");
        validarLongitud(datos.getTelefono(), 20, "El teléfono");
        validarLongitud(datos.getRuc(), 20, "El RUC");
        validarLongitud(datos.getDireccion(), 200, "La dirección");
        if (datos.getCorreo() != null && !datos.getCorreo().isBlank()) {
            String correo = datos.getCorreo().trim();
            if (correo.length() > 120 || !FORMATO_CORREO.matcher(correo).matches()) {
                throw new IllegalArgumentException("El correo del proveedor no tiene un formato válido");
            }
        }
        if (datos.getRuc() != null && !datos.getRuc().isBlank()
                && !datos.getRuc().trim().matches("\\d{11}")) {
            throw new IllegalArgumentException("El RUC debe contener exactamente 11 dígitos");
        }
        if (datos.getEstado() != null && !datos.getEstado().isBlank()) {
            normalizarEstado(datos.getEstado());
        }
    }

    private void validarLongitud(String valor, int maximo, String campo) {
        if (valor != null && valor.trim().length() > maximo) {
            throw new IllegalArgumentException(campo + " no puede superar " + maximo + " caracteres");
        }
    }

    private String normalizarEstado(String estado) {
        String normalizado = estado.trim().toUpperCase(java.util.Locale.ROOT);
        if (!ESTADO_ACTIVO.equals(normalizado) && !ESTADO_INACTIVO.equals(normalizado)) {
            throw new IllegalArgumentException("El estado del proveedor debe ser ACTIVO o INACTIVO");
        }
        return normalizado;
    }
}
