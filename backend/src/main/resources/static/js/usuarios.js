const usuarioActual = obtenerSesion();

if (!usuarioActual) {
  window.location.href = '/index.html';
} else if (usuarioActual.rol !== 'ADMIN') {
  window.location.href = '/dashboard.html';
} else {
  iniciarLayout('Gestión de usuarios');

  const formCrear = document.getElementById('form-crear-empleado');
  const modalRegistro = document.getElementById('modal-registro-empleado');
  const botonCrear = document.getElementById('btn-crear-empleado');
  const errorCrear = document.getElementById('error-crear-empleado');
  const resultado = document.getElementById('resultado-crear-empleado');
  const formEditar = document.getElementById('form-editar-usuario');
  const modalEdicion = document.getElementById('modal-editar-usuario');
  const errorEdicion = document.getElementById('error-editar-usuario');
  const tablaUsuarios = document.getElementById('tabla-usuarios-body');
  const filtroBusqueda = document.getElementById('filtro-busqueda');
  const filtroEstado = document.getElementById('filtro-estado');
  const filtroRol = document.getElementById('filtro-rol');
  const usuarioIdActual = Number(usuarioActual.id);
  let usuarios = [];
  let elementoDisparadorModal = null;

  function celda(texto) {
    const td = document.createElement('td');
    td.textContent = texto || '—';
    return td;
  }

  function normalizar(texto) {
    return String(texto || '')
      .normalize('NFD')
      .replace(/\p{Diacritic}/gu, '')
      .toLocaleLowerCase('es');
  }

  function usuariosFiltrados() {
    const busqueda = normalizar(filtroBusqueda.value.trim());
    return usuarios
      .filter((usuario) => filtroEstado.value === 'TODOS' || usuario.estado === filtroEstado.value)
      .filter((usuario) => filtroRol.value === 'TODOS' || usuario.rol === filtroRol.value)
      .filter((usuario) => {
        const campos = [usuario.nombre, usuario.apellido, usuario.email, usuario.nombreUsuario];
        return !busqueda || campos.some((campo) => normalizar(campo).includes(busqueda));
      })
      .sort((a, b) => {
        const apellido = (a.apellido || '').localeCompare(b.apellido || '', 'es', { sensitivity: 'base' });
        return apellido || (a.nombre || '').localeCompare(b.nombre || '', 'es', { sensitivity: 'base' });
      });
  }

  function renderizarUsuarios() {
    tablaUsuarios.replaceChildren();
    const visibles = usuariosFiltrados();
    if (!visibles.length) {
      const filaVacia = document.createElement('tr');
      const mensaje = celda(usuarios.length
        ? 'No hay personal que coincida con los filtros.'
        : 'No hay usuarios registrados.');
      mensaje.colSpan = 5;
      filaVacia.appendChild(mensaje);
      tablaUsuarios.appendChild(filaVacia);
      return;
    }

    visibles.forEach((usuario) => {
      const fila = document.createElement('tr');
      fila.appendChild(celda([usuario.nombre, usuario.apellido].filter(Boolean).join(' ')));
      fila.appendChild(celda(usuario.email));
      fila.appendChild(celda(usuario.rol === 'ADMIN' ? 'Administrador' : 'Empleado'));
      fila.appendChild(celda(usuario.estado === 'ACTIVO' ? 'Activo' : 'Inactivo'));

      const celdaAccion = document.createElement('td');
      const editar = document.createElement('button');
      editar.type = 'button';
      editar.className = 'btn btn-secundario usuarios-editar';
      editar.dataset.usuarioId = usuario.id;
      editar.textContent = 'Editar';
      celdaAccion.appendChild(editar);
      fila.appendChild(celdaAccion);
      tablaUsuarios.appendChild(fila);
    });
  }

  async function cargarUsuarios() {
    tablaUsuarios.replaceChildren();
    const filaCarga = document.createElement('tr');
    const celdaCarga = celda('Cargando usuarios...');
    celdaCarga.colSpan = 5;
    filaCarga.appendChild(celdaCarga);
    tablaUsuarios.appendChild(filaCarga);

    try {
      usuarios = await apiGet('/api/usuarios');
      renderizarUsuarios();
    } catch (err) {
      tablaUsuarios.replaceChildren();
      const filaError = document.createElement('tr');
      const mensaje = celda(`No se pudo cargar la lista de usuarios: ${err.message}`);
      mensaje.colSpan = 5;
      filaError.appendChild(mensaje);
      tablaUsuarios.appendChild(filaError);
    }
  }

  function abrirModal(modal) {
    elementoDisparadorModal = document.activeElement;
    modal.hidden = false;
    modal.setAttribute('aria-hidden', 'false');
    requestAnimationFrame(() => {
      if (!modal.hidden) modal.classList.add('visible');
    });
  }

  function cerrarModal(modal, formulario, error) {
    modal.classList.remove('visible');
    modal.hidden = true;
    modal.setAttribute('aria-hidden', 'true');
    formulario.reset();
    error.hidden = true;
    if (elementoDisparadorModal instanceof HTMLElement && elementoDisparadorModal.isConnected) {
      elementoDisparadorModal.focus();
    }
  }

  function cerrarRegistro() {
    cerrarModal(modalRegistro, formCrear, errorCrear);
  }

  function cerrarEdicion() {
    cerrarModal(modalEdicion, formEditar, errorEdicion);
  }

  document.getElementById('btn-anadir-personal').addEventListener('click', () => {
    formCrear.reset();
    errorCrear.hidden = true;
    abrirModal(modalRegistro);
    document.getElementById('empleado-nombre').focus();
  });

  document.getElementById('btn-cancelar-registro').addEventListener('click', cerrarRegistro);
  document.getElementById('btn-cerrar-registro').addEventListener('click', cerrarRegistro);
  document.getElementById('btn-cancelar-edicion').addEventListener('click', cerrarEdicion);
  document.getElementById('btn-cerrar-edicion').addEventListener('click', cerrarEdicion);

  [modalRegistro, modalEdicion].forEach((modal) => {
    modal.addEventListener('click', (evento) => {
      if (evento.target !== modal) return;
      if (modal === modalRegistro) cerrarRegistro();
      else cerrarEdicion();
    });
  });

  document.addEventListener('keydown', (evento) => {
    if (evento.key !== 'Escape') return;
    if (!modalRegistro.hidden) cerrarRegistro();
    else if (!modalEdicion.hidden) cerrarEdicion();
  });

  tablaUsuarios.addEventListener('click', (evento) => {
    const botonEditar = evento.target.closest('.usuarios-editar');
    if (!botonEditar) return;

    const usuario = usuarios.find((item) => Number(item.id) === Number(botonEditar.dataset.usuarioId));
    if (!usuario) return;
    document.getElementById('edicion-id').value = usuario.id;
    document.getElementById('edicion-nombre').value = usuario.nombre || '';
    document.getElementById('edicion-apellido').value = usuario.apellido || '';
    document.getElementById('edicion-email').value = usuario.email || '';
    const selectorRol = document.getElementById('edicion-rol');
    selectorRol.value = usuario.rol;
    selectorRol.disabled = Number(usuario.id) === usuarioIdActual;
    document.getElementById('edicion-estado').value = usuario.estado;
    errorEdicion.hidden = true;
    abrirModal(modalEdicion);
    document.getElementById('edicion-nombre').focus();
  });

  filtroBusqueda.addEventListener('input', renderizarUsuarios);
  filtroEstado.addEventListener('change', renderizarUsuarios);
  filtroRol.addEventListener('change', renderizarUsuarios);
  document.getElementById('btn-recargar-usuarios').addEventListener('click', cargarUsuarios);

  formEditar.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    errorEdicion.hidden = true;
    if (!formEditar.reportValidity()) return;

    const id = Number(document.getElementById('edicion-id').value);
    const usuario = usuarios.find((item) => Number(item.id) === id);
    const nuevoEstado = document.getElementById('edicion-estado').value;
    if (usuario?.estado === 'ACTIVO' && nuevoEstado === 'INACTIVO'
        && !window.confirm(`¿Deseas desactivar la cuenta de ${usuario.nombre}?`)) {
      document.getElementById('edicion-estado').value = 'ACTIVO';
      return;
    }

    const botonGuardar = document.getElementById('btn-guardar-edicion');
    botonGuardar.disabled = true;
    try {
      const actualizado = await apiPut(`/api/usuarios/${id}`, {
        nombre: document.getElementById('edicion-nombre').value.trim(),
        apellido: document.getElementById('edicion-apellido').value.trim(),
        email: document.getElementById('edicion-email').value.trim(),
        rol: document.getElementById('edicion-rol').value,
        estado: nuevoEstado,
      });
      cerrarEdicion();
      if (id === usuarioIdActual
          && (actualizado.estado === 'INACTIVO' || actualizado.email !== usuarioActual.email)) {
        sessionStorage.removeItem('usuario');
        window.location.href = '/index.html';
        return;
      }
      mostrarToast('Los datos del personal se actualizaron correctamente.', 'exito');
      await cargarUsuarios();
    } catch (err) {
      errorEdicion.textContent = err.message;
      errorEdicion.hidden = false;
    } finally {
      botonGuardar.disabled = false;
    }
  });

  formCrear.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    errorCrear.hidden = true;
    resultado.hidden = true;

    if (!formCrear.reportValidity()) return;

    botonCrear.disabled = true;
    botonCrear.textContent = 'Creando cuenta...';

    try {
      const creado = await apiPost('/api/usuarios', {
        nombre: document.getElementById('empleado-nombre').value.trim(),
        apellido: document.getElementById('empleado-apellido').value.trim(),
        email: document.getElementById('empleado-email').value.trim(),
      });

      document.getElementById('empleado-creado').textContent =
        `${creado.usuario.nombre} (${creado.usuario.email})`;
      document.getElementById('empleado-nombre-usuario').textContent =
        `Nombre de usuario: ${creado.nombreUsuario}`;
      document.getElementById('correo-bienvenida').textContent =
        `Se envió el correo de bienvenida a ${creado.usuario.email}.`;
      resultado.hidden = false;
      cerrarRegistro();
      await cargarUsuarios();
    } catch (err) {
      errorCrear.textContent = err.message;
      errorCrear.hidden = false;
    } finally {
      botonCrear.disabled = false;
      botonCrear.textContent = 'Crear cuenta de empleado';
    }
  });

  cargarUsuarios();
}
