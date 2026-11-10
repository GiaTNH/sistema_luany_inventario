iniciarLayout('Proveedores');

let todosProveedores = [];

function mostrarErrorProveedor(id, visible) {
  document.getElementById(id)?.classList.toggle('visible', visible);
}

function abrirModalProveedor(id = null) {
  const proveedor = id == null
    ? null
    : todosProveedores.find(item => String(item.id) === String(id));
  document.getElementById('prov-id').value = proveedor?.id || '';
  document.getElementById('prov-nombre').value = proveedor?.nombre || '';
  document.getElementById('prov-ruc').value = proveedor?.ruc || '';
  document.getElementById('prov-telefono').value = proveedor?.telefono || '';
  document.getElementById('prov-correo').value = proveedor?.correo || '';
  document.getElementById('prov-direccion').value = proveedor?.direccion || '';
  document.getElementById('prov-estado').value = proveedor?.estado ?? 'ACTIVO';
  document.getElementById('modal-prov-titulo').textContent = proveedor ? 'Editar proveedor' : 'Nuevo proveedor';
  mostrarErrorProveedor('err-prov-nombre', false);
  abrirModalAccesible(document.getElementById('modal-proveedor'));
}

function cerrarModalProveedor(id) {
  cerrarModalAccesible(document.getElementById(id));
}

function renderizarProveedores() {
  const tbody = document.getElementById('tabla-proveedores-body');
  const mensaje = document.getElementById('proveedores-mensaje');
  if (!todosProveedores.length) {
    mensaje.textContent = 'No hay proveedores registrados.';
    tbody.innerHTML = '<tr><td colspan="6"><div class="estado-vacio"><p>Sin proveedores registrados.</p></div></td></tr>';
    return;
  }

  mensaje.textContent = `${todosProveedores.length} proveedor${todosProveedores.length === 1 ? '' : 'es'}`;
  tbody.innerHTML = todosProveedores.map(proveedor => {
    const nombre = escapar(proveedor.nombre || '');
    const estado = escapar(proveedor.estado || '—');
    const claseEstado = String(proveedor.estado || '').toUpperCase() === 'ACTIVO' ? 'badge-normal' : 'badge-bajo';
    return `<tr class="productos-card-row">
      <td><strong class="productos-entity-name">${nombre}</strong></td>
      <td>${escapar(proveedor.ruc || '—')}</td>
      <td><span class="productos-entity-phone">${escapar(proveedor.telefono || '—')}</span></td>
      <td>${escapar(proveedor.correo || '—')}</td>
      <td>${escapar(proveedor.direccion || '—')}</td>
      <td><div class="productos-provider-status-cell"><span class="badge ${claseEstado}">${estado}</span><div class="productos-row-actions productos-entity-actions">
        <button type="button" class="productos-action-button productos-row-edit" title="Editar proveedor" aria-label="Editar proveedor ${nombre}" onclick="abrirModalProveedor(${proveedor.id})"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4 11.5-11.5Z"/></svg></button>
        <button type="button" class="productos-action-button productos-row-delete" title="Eliminar proveedor" aria-label="Eliminar proveedor ${nombre}" onclick="confirmarEliminarProveedor(${proveedor.id})"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h16M10 11v6m4-6v6M6 7l1 14h10l1-14M9 7V4h6v3"/></svg></button>
      </div></div></td>
    </tr>`;
  }).join('');
}

async function cargarProveedores() {
  const tbody = document.getElementById('tabla-proveedores-body');
  const mensaje = document.getElementById('proveedores-mensaje');
  mensaje.textContent = 'Cargando proveedores...';
  tbody.innerHTML = '<tr><td colspan="6"><span class="spinner spinner-dark"></span> Cargando proveedores...</td></tr>';
  try {
    todosProveedores = await apiGet('/api/proveedores');
    renderizarProveedores();
  } catch (error) {
    mensaje.textContent = 'No se pudieron cargar los proveedores.';
    tbody.innerHTML = `<tr><td colspan="6">${escapar(error.message || 'Ocurrió un error al consultar proveedores.')}</td></tr>`;
    mostrarToast(error.message, 'error');
  }
}

async function guardarProveedor() {
  const nombre = document.getElementById('prov-nombre').value.trim();
  if (!nombre) {
    mostrarErrorProveedor('err-prov-nombre', true);
    return;
  }
  const correoInput = document.getElementById('prov-correo');
  if (!correoInput.checkValidity()) {
    correoInput.reportValidity();
    return;
  }
  const rucInput = document.getElementById('prov-ruc');
  if (!rucInput.checkValidity()) {
    rucInput.reportValidity();
    return;
  }

  const id = document.getElementById('prov-id').value;
  const datos = {
    nombre,
    ruc: rucInput.value.trim(),
    telefono: document.getElementById('prov-telefono').value.trim(),
    correo: correoInput.value.trim(),
    direccion: document.getElementById('prov-direccion').value.trim(),
    estado: document.getElementById('prov-estado').value.trim(),
  };
  const boton = document.getElementById('btn-guardar-proveedor');
  boton.disabled = true;
  try {
    if (id) {
      await apiPut(`/api/proveedores/${id}`, datos);
      mostrarToast('Proveedor actualizado correctamente.', 'exito');
    } else {
      await apiPost('/api/proveedores', datos);
      mostrarToast('Proveedor registrado correctamente.', 'exito');
    }
    cerrarModalProveedor('modal-proveedor');
    await cargarProveedores();
  } catch (error) {
    mostrarToast(error.message, 'error');
  } finally {
    boton.disabled = false;
  }
}

function confirmarEliminarProveedor(id) {
  const proveedor = todosProveedores.find(item => String(item.id) === String(id));
  if (!proveedor) return;
  document.getElementById('confirmar-mensaje-proveedor').textContent =
    `¿Eliminar el proveedor "${proveedor.nombre}"? Esta acción no se puede deshacer.`;
  const boton = document.getElementById('btn-confirmar-proveedor');
  boton.onclick = () => eliminarProveedor(id);
  document.getElementById('modal-confirmar-proveedor').classList.add('visible');
}

async function eliminarProveedor(id) {
  const boton = document.getElementById('btn-confirmar-proveedor');
  boton.disabled = true;
  try {
    await apiDelete(`/api/proveedores/${id}`);
    mostrarToast('Proveedor eliminado correctamente.', 'exito');
    cerrarModalProveedor('modal-confirmar-proveedor');
    await cargarProveedores();
  } catch (error) {
    mostrarToast(error.message, 'error');
  } finally {
    boton.disabled = false;
  }
}

document.getElementById('btn-nuevo-proveedor').addEventListener('click', () => abrirModalProveedor());
document.querySelectorAll('.modal-overlay').forEach(overlay => {
  overlay.addEventListener('click', evento => {
    if (evento.target === overlay) cerrarModalProveedor(overlay.id);
  });
});
document.addEventListener('keydown', evento => {
  if (evento.key === 'Escape') {
    cerrarModalProveedor('modal-proveedor');
    cerrarModalProveedor('modal-confirmar-proveedor');
  }
});

cargarProveedores();
