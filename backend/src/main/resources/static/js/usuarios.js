const usuarioActual = obtenerSesion();

if (!usuarioActual) {
  window.location.href = '/index.html';
} else if (usuarioActual.rol !== 'ADMIN') {
  window.location.href = '/dashboard.html';
} else {
  iniciarLayout('Gestión de usuarios');

  const form = document.getElementById('form-crear-empleado');
  const boton = document.getElementById('btn-crear-empleado');
  const error = document.getElementById('error-crear-empleado');
  const resultado = document.getElementById('resultado-crear-empleado');

  form.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    error.hidden = true;
    resultado.hidden = true;

    if (!form.reportValidity()) return;

    boton.disabled = true;
    boton.textContent = 'Creando cuenta...';

    try {
      const creado = await apiPost('/api/usuarios', {
        nombre: document.getElementById('empleado-nombre').value.trim(),
        email: document.getElementById('empleado-email').value.trim(),
      });

      document.getElementById('empleado-creado').textContent =
        `${creado.usuario.nombre} (${creado.usuario.email})`;
      document.getElementById('correo-bienvenida').textContent =
        `Se envió el correo de bienvenida a ${creado.usuario.email}.`;
      resultado.hidden = false;
      form.reset();
    } catch (err) {
      error.textContent = err.message;
      error.hidden = false;
    } finally {
      boton.disabled = false;
      boton.textContent = 'Crear cuenta de empleado';
    }
  });
}
