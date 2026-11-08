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
  const tablaUsuarios = document.getElementById('tabla-usuarios-body');
  const usuarioIdActual = Number(usuarioActual.id);

  function celda(texto) {
    const td = document.createElement('td');
    td.textContent = texto || '—';
    return td;
  }

  async function cargarUsuarios() {
    tablaUsuarios.replaceChildren();
    const filaCarga = document.createElement('tr');
    const celdaCarga = celda('Cargando usuarios...');
    celdaCarga.colSpan = 6;
    filaCarga.appendChild(celdaCarga);
    tablaUsuarios.appendChild(filaCarga);

    try {
      const usuarios = await apiGet('/api/usuarios');
      tablaUsuarios.replaceChildren();
      if (!usuarios.length) {
        const filaVacia = document.createElement('tr');
        const mensaje = celda('No hay usuarios registrados.');
        mensaje.colSpan = 6;
        filaVacia.appendChild(mensaje);
        tablaUsuarios.appendChild(filaVacia);
        return;
      }

      usuarios.forEach((usuario) => {
        const fila = document.createElement('tr');
        fila.appendChild(celda([usuario.nombre, usuario.apellido].filter(Boolean).join(' ')));
        fila.appendChild(celda(usuario.email));
        fila.appendChild(celda(usuario.nombreUsuario));

        const celdaRol = document.createElement('td');
        const selector = document.createElement('select');
        selector.className = 'form-control usuarios-rol-select';
        selector.setAttribute('aria-label', `Rol de ${usuario.nombre}`);
        ['ADMIN', 'EMPLEADO'].forEach((rol) => {
          const opcion = document.createElement('option');
          opcion.value = rol;
          opcion.textContent = rol;
          opcion.selected = usuario.rol === rol;
          selector.appendChild(opcion);
        });
        celdaRol.appendChild(selector);
        fila.appendChild(celdaRol);
        fila.appendChild(celda(usuario.estado));

        const celdaAccion = document.createElement('td');
        const guardar = document.createElement('button');
        guardar.type = 'button';
        guardar.className = 'btn btn-secundario usuarios-guardar-rol';
        guardar.dataset.usuarioId = usuario.id;
        guardar.textContent = 'Guardar rol';
        if (Number(usuario.id) === usuarioIdActual) {
          guardar.disabled = true;
          guardar.title = 'No puedes modificar tu propio rol';
        }
        celdaAccion.appendChild(guardar);
        fila.appendChild(celdaAccion);
        tablaUsuarios.appendChild(fila);
      });
    } catch (err) {
      tablaUsuarios.replaceChildren();
      const filaError = document.createElement('tr');
      const mensaje = celda(`No se pudo cargar la lista de usuarios: ${err.message}`);
      mensaje.colSpan = 6;
      filaError.appendChild(mensaje);
      tablaUsuarios.appendChild(filaError);
    }
  }

  tablaUsuarios.addEventListener('click', async (evento) => {
    const botonRol = evento.target.closest('.usuarios-guardar-rol');
    if (!botonRol || botonRol.disabled) return;

    const fila = botonRol.closest('tr');
    const selector = fila.querySelector('.usuarios-rol-select');
    botonRol.disabled = true;
    try {
      await apiPut(`/api/usuarios/${botonRol.dataset.usuarioId}/rol`, {
        rol: selector.value,
      });
      mostrarToast('Rol actualizado correctamente.', 'exito');
      await cargarUsuarios();
    } catch (err) {
      mostrarToast(err.message, 'error');
    } finally {
      botonRol.disabled = false;
    }
  });

  document.getElementById('btn-recargar-usuarios').addEventListener('click', cargarUsuarios);

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
      document.getElementById('empleado-nombre-usuario').textContent =
        `Nombre de usuario: ${creado.nombreUsuario}`;
      document.getElementById('correo-bienvenida').textContent =
        `Se envió el correo de bienvenida a ${creado.usuario.email}.`;
      resultado.hidden = false;
      form.reset();
      await cargarUsuarios();
    } catch (err) {
      error.textContent = err.message;
      error.hidden = false;
    } finally {
      boton.disabled = false;
      boton.textContent = 'Crear cuenta de empleado';
    }
  });

  cargarUsuarios();
}
