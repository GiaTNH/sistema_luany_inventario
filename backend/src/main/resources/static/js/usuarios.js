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
      .filter((usuario) => filtroEstado.value === 'TODOS' || estadoVisible(usuario) === filtroEstado.value)
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

  function estadoVisible(usuario) {
    if (usuario.estado === 'INACTIVO') return 'INACTIVO';
    if (usuario.bloqueadoManual) return 'BLOQUEADO';
    return usuario.debeCambiarPassword ? 'PENDIENTE' : 'ACTIVO';
  }

  function etiquetaEstado(usuario) {
    const estado = estadoVisible(usuario);
    return {
      PENDIENTE: 'Pendiente',
      ACTIVO: 'Activo',
      INACTIVO: 'Inactivo',
      BLOQUEADO: 'Bloqueado',
    }[estado];
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
      fila.appendChild(celda(nombreVisibleRol(usuario.rol)));
      fila.appendChild(celda(etiquetaEstado(usuario)));

      const celdaAccion = document.createElement('td');
      const editar = document.createElement('button');
      editar.type = 'button';
      editar.className = 'btn btn-secundario usuarios-editar';
      editar.dataset.usuarioId = usuario.id;
      editar.textContent = 'Editar';
      celdaAccion.appendChild(editar);
      const seguridad = document.createElement('button');
      seguridad.type = 'button';
      seguridad.className = 'btn btn-secundario usuarios-seguridad';
      seguridad.dataset.usuarioId = usuario.id;
      seguridad.dataset.bloqueado = String(usuario.bloqueadoManual === true);
      seguridad.textContent = usuario.bloqueadoManual ? 'Desbloquear'
        : usuario.estado === 'INACTIVO' ? 'Reactivar' : 'Bloquear';
      celdaAccion.appendChild(seguridad);
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

  tablaUsuarios.addEventListener('click', async (evento) => {
    const boton = evento.target.closest('.usuarios-seguridad');
    if (!boton) return;
    const id = Number(boton.dataset.usuarioId);
    const usuario = usuarios.find((item) => Number(item.id) === id);
    if (!usuario) return;
    const motivo = window.prompt(
      usuario.bloqueadoManual || usuario.estado === 'INACTIVO'
        ? 'Motivo del desbloqueo'
        : 'Motivo del bloqueo temporal administrativo',
      usuario.bloqueadoManual || usuario.estado === 'INACTIVO'
        ? 'Desbloqueo solicitado por administración'
        : 'Bloqueo manual administrativo',
    );
    if (!motivo?.trim() || boton.disabled) return;
    boton.disabled = true;
    try {
      if (usuario.bloqueadoManual || usuario.estado === 'INACTIVO') {
        const estadoSeguridad = await apiPost(
          `/api/seguridad/usuarios/${id}/desbloquear`,
          { motivo: motivo.trim() },
        );
        usuario.bloqueadoManual = estadoSeguridad.bloqueadoManual;
        usuario.bloqueadoHasta = estadoSeguridad.bloqueadoHasta;
        renderizarUsuarios();

        if (usuario.estado === 'INACTIVO') {
          const actualizado = await apiPut(`/api/usuarios/${id}`, {
            nombre: usuario.nombre,
            apellido: usuario.apellido || '',
            email: usuario.email,
            rol: usuario.rol,
            estado: 'ACTIVO',
          });
          Object.assign(usuario, actualizado);
          renderizarUsuarios();
          mostrarToast('La cuenta fue reactivada.', 'exito');
        } else {
          mostrarToast('La cuenta fue desbloqueada y sus sesiones fueron invalidadas.', 'exito');
        }
      } else {
        const estadoSeguridad = await apiPost(
          `/api/seguridad/usuarios/${id}/bloquear`,
          { motivo: motivo.trim() },
        );
        usuario.bloqueadoManual = estadoSeguridad.bloqueadoManual;
        usuario.bloqueadoHasta = estadoSeguridad.bloqueadoHasta;
        renderizarUsuarios();
        mostrarToast('La cuenta fue bloqueada y sus sesiones fueron invalidadas.', 'exito');
      }
      await cargarSeguridad();
    } catch (error) {
      mostrarToast(error.message, 'error');
    } finally {
      boton.disabled = false;
    }
  });

  async function cargarSeguridad() {
    const tbody = document.getElementById('tabla-seguridad-body');
    try {
      const [intentos, auditoria, bloqueos] = await Promise.all([
        apiGet('/api/seguridad/intentos'),
        apiGet('/api/seguridad/auditoria'),
        apiGet('/api/seguridad/bloqueos'),
      ]);
      const filas = [
        ...auditoria.map((item) => ({
          fecha: item.fecha,
          cuenta: item.usuarioRelacionado || item.ip || 'Sistema',
          evento: item.tipoEvento,
          resultado: item.resultado,
          descripcion: item.descripcion,
        })),
        ...intentos.map((item) => ({
          fecha: item.fecha,
          cuenta: item.identificador || item.ip || 'Desconocido',
          evento: item.tipo,
          resultado: item.resultado,
          descripcion: item.descripcion,
        })),
        ...bloqueos.map((item) => ({
          fecha: item.fecha,
          cuenta: item.usuario?.email || item.realizadoPor || 'Sistema',
          evento: item.accion,
          resultado: item.tipo,
          descripcion: item.motivo,
        })),
      ].sort((a, b) => String(b.fecha).localeCompare(String(a.fecha))).slice(0, 100);
      tbody.replaceChildren(...filas.map((fila) => {
        const tr = document.createElement('tr');
        tr.append(celda(normalizarFechaSeguridad(fila.fecha)), celda(fila.cuenta),
          celda(fila.evento), celda(fila.resultado), celda(fila.descripcion));
        return tr;
      }));
    } catch (error) {
      tbody.replaceChildren();
      const tr = document.createElement('tr');
      const td = celda(`No se pudo cargar la actividad: ${error.message}`);
      td.colSpan = 5;
      tr.append(td);
      tbody.append(tr);
    }
  }

  function normalizarFechaSeguridad(fecha) {
    return typeof normalizarFecha === 'function' ? normalizarFecha(fecha) : String(fecha || '—');
  }

  document.getElementById('btn-recargar-seguridad').addEventListener('click', cargarSeguridad);

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
      const notificacionFallida = actualizado.notificacionFallida === true;
      cerrarEdicion();
      if (id === usuarioIdActual
          && (actualizado.estado === 'INACTIVO' || actualizado.email !== usuarioActual.email)) {
        if (notificacionFallida) {
          sessionStorage.setItem('avisoCorreoNoNotificado', '1');
        }
        sessionStorage.removeItem('usuario');
        window.location.href = '/index.html';
        return;
      }
      mostrarToast(
        notificacionFallida
          ? 'Los datos se guardaron, pero no se pudo enviar la notificación de cambio de correo.'
          : 'Los datos del personal se actualizaron correctamente.',
        notificacionFallida ? 'info' : 'exito',
      );
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

    if (!formCrear.reportValidity()) return;

    botonCrear.disabled = true;
    botonCrear.textContent = 'Creando cuenta...';

    try {
      const resultado = await apiPost('/api/usuarios', {
        nombre: document.getElementById('empleado-nombre').value.trim(),
        apellido: document.getElementById('empleado-apellido').value.trim(),
        email: document.getElementById('empleado-email').value.trim(),
        rol: document.getElementById('empleado-rol').value,
      });

      cerrarRegistro();
      mostrarToast(
        resultado.notificacionFallida
          ? 'La cuenta se guardó, pero no se pudo enviar la notificación de bienvenida.'
          : 'La cuenta se creó correctamente.',
        resultado.notificacionFallida ? 'info' : 'exito',
      );
      await cargarUsuarios();
    } catch (err) {
      errorCrear.textContent = err.message;
      errorCrear.hidden = false;
    } finally {
      botonCrear.disabled = false;
      botonCrear.textContent = 'Crear cuenta para almacén';
    }
  });

  cargarUsuarios();
  cargarSeguridad();
}
