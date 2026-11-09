iniciarLayout('Clientes');

const formCliente = document.getElementById('form-cliente');
const modalCliente = document.getElementById('modal-cliente');
const modalEliminar = document.getElementById('modal-eliminar-cliente');
const tablaClientes = document.getElementById('tabla-clientes-body');
const mensajeClientes = document.getElementById('clientes-mensaje');
const busquedaCliente = document.getElementById('buscar-cliente');
const errorCliente = document.getElementById('error-cliente');
const botonGuardarCliente = document.getElementById('btn-guardar-cliente');
let clientes = [];
let clienteAEliminar = null;
let temporizadorBusqueda = null;
let solicitudBusqueda = 0;
let elementoDisparadorModal = null;

function celda(texto) {
  const elemento = document.createElement('td');
  elemento.textContent = texto || '—';
  return elemento;
}

function mostrarFila(mensaje) {
  tablaClientes.replaceChildren();
  const fila = document.createElement('tr');
  const celdaMensaje = celda(mensaje);
  celdaMensaje.colSpan = 6;
  fila.appendChild(celdaMensaje);
  tablaClientes.appendChild(fila);
}

function renderizarClientes() {
  tablaClientes.replaceChildren();
  if (!clientes.length) {
    mostrarFila(busquedaCliente.value.trim()
      ? 'No se encontraron clientes con esa búsqueda.'
      : 'Aún no hay clientes registrados.');
    return;
  }

  clientes.forEach((cliente) => {
    const fila = document.createElement('tr');
    fila.appendChild(celda(cliente.nombre));
    fila.appendChild(celda(cliente.tipoDocumento));
    fila.appendChild(celda(cliente.numeroDocumento));
    fila.appendChild(celda(cliente.telefono));
    fila.appendChild(celda(cliente.direccion));

    const acciones = document.createElement('td');
    const editar = document.createElement('button');
    editar.type = 'button';
    editar.className = 'btn btn-secundario clientes-accion';
    editar.dataset.accion = 'editar';
    editar.dataset.clienteId = cliente.id;
    editar.textContent = 'Editar';
    const eliminar = document.createElement('button');
    eliminar.type = 'button';
    eliminar.className = 'btn btn-peligro clientes-accion';
    eliminar.dataset.accion = 'eliminar';
    eliminar.dataset.clienteId = cliente.id;
    eliminar.textContent = 'Eliminar';
    acciones.append(editar, eliminar);
    fila.appendChild(acciones);
    tablaClientes.appendChild(fila);
  });
}

async function cargarClientes() {
  const numeroSolicitud = ++solicitudBusqueda;
  mensajeClientes.textContent = 'Cargando clientes...';
  mostrarFila('Cargando clientes...');

  const termino = busquedaCliente.value.trim();
  const ruta = termino
    ? `/api/clientes?buscar=${encodeURIComponent(termino)}`
    : '/api/clientes';

  try {
    const resultado = await apiGet(ruta);
    if (numeroSolicitud !== solicitudBusqueda) return;
    clientes = resultado;
    mensajeClientes.textContent = clientes.length
      ? `${clientes.length} cliente${clientes.length === 1 ? '' : 's'} encontrado${clientes.length === 1 ? '' : 's'}.`
      : termino ? 'No se encontraron clientes con esa búsqueda.' : 'Aún no hay clientes registrados.';
    renderizarClientes();
  } catch (error) {
    if (numeroSolicitud !== solicitudBusqueda) return;
    mensajeClientes.textContent = 'No se pudieron cargar los clientes.';
    mostrarFila(`Error al cargar clientes: ${error.message}`);
    mostrarToast(error.message, 'error');
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

function cerrarModal(modal) {
  modal.classList.remove('visible');
  modal.hidden = true;
  modal.setAttribute('aria-hidden', 'true');
  if (elementoDisparadorModal instanceof HTMLElement && elementoDisparadorModal.isConnected) {
    elementoDisparadorModal.focus();
  }
}

function cerrarFormularioCliente() {
  cerrarModal(modalCliente);
  formCliente.reset();
  errorCliente.hidden = true;
  botonGuardarCliente.disabled = false;
}

function abrirFormularioCliente(cliente = null) {
  formCliente.reset();
  errorCliente.hidden = true;
  document.getElementById('cliente-id').value = cliente?.id || '';
  document.getElementById('cliente-nombre').value = cliente?.nombre || '';
  document.getElementById('cliente-tipo-documento').value = cliente?.tipoDocumento || '';
  document.getElementById('cliente-numero-documento').value = cliente?.numeroDocumento || '';
  document.getElementById('cliente-telefono').value = cliente?.telefono || '';
  document.getElementById('cliente-direccion').value = cliente?.direccion || '';
  document.getElementById('modal-cliente-titulo').textContent =
    cliente ? 'Editar cliente' : 'Nuevo cliente';
  botonGuardarCliente.textContent = cliente ? 'Guardar cambios' : 'Guardar cliente';
  abrirModal(modalCliente);
  document.getElementById('cliente-nombre').focus();
}

document.getElementById('btn-nuevo-cliente').addEventListener('click', () => {
  abrirFormularioCliente();
});

document.getElementById('btn-cerrar-modal-cliente').addEventListener('click', cerrarFormularioCliente);
document.getElementById('btn-cancelar-cliente').addEventListener('click', cerrarFormularioCliente);

document.getElementById('btn-cerrar-eliminar-cliente').addEventListener('click', () => cerrarModal(modalEliminar));
document.getElementById('btn-cancelar-eliminar-cliente').addEventListener('click', () => cerrarModal(modalEliminar));

[modalCliente, modalEliminar].forEach((modal) => {
  modal.addEventListener('click', (evento) => {
    if (evento.target !== modal) return;
    if (modal === modalCliente) cerrarFormularioCliente();
    else cerrarModal(modalEliminar);
  });
});

document.addEventListener('keydown', (evento) => {
  if (evento.key !== 'Escape') return;
  if (!modalCliente.hidden) cerrarFormularioCliente();
  else if (!modalEliminar.hidden) cerrarModal(modalEliminar);
});

tablaClientes.addEventListener('click', (evento) => {
  const boton = evento.target.closest('.clientes-accion');
  if (!boton) return;
  const cliente = clientes.find((item) => Number(item.id) === Number(boton.dataset.clienteId));
  if (!cliente) return;

  if (boton.dataset.accion === 'editar') {
    abrirFormularioCliente(cliente);
    return;
  }

  clienteAEliminar = cliente;
  document.getElementById('mensaje-eliminar-cliente').textContent =
    `¿Deseas eliminar a ${cliente.nombre}?`;
  abrirModal(modalEliminar);
});

busquedaCliente.addEventListener('input', () => {
  clearTimeout(temporizadorBusqueda);
  temporizadorBusqueda = setTimeout(cargarClientes, 250);
});

formCliente.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  errorCliente.hidden = true;
  if (!formCliente.reportValidity()) return;

  botonGuardarCliente.disabled = true;
  const id = document.getElementById('cliente-id').value;
  const datos = {
    nombre: document.getElementById('cliente-nombre').value.trim(),
    tipoDocumento: document.getElementById('cliente-tipo-documento').value,
    numeroDocumento: document.getElementById('cliente-numero-documento').value.trim(),
    telefono: document.getElementById('cliente-telefono').value.trim(),
    direccion: document.getElementById('cliente-direccion').value.trim(),
  };

  try {
    if (id) await apiPut(`/api/clientes/${id}`, datos);
    else await apiPost('/api/clientes', datos);
    cerrarFormularioCliente();
    mostrarToast(id ? 'Cliente actualizado correctamente.' : 'Cliente registrado correctamente.', 'exito');
    await cargarClientes();
  } catch (error) {
    errorCliente.textContent = error.message;
    errorCliente.hidden = false;
  } finally {
    botonGuardarCliente.disabled = false;
  }
});

document.getElementById('btn-confirmar-eliminar-cliente').addEventListener('click', async (evento) => {
  if (!clienteAEliminar) return;
  const boton = evento.currentTarget;
  boton.disabled = true;
  try {
    await apiDelete(`/api/clientes/${clienteAEliminar.id}`);
    cerrarModal(modalEliminar);
    mostrarToast('Cliente eliminado correctamente.', 'exito');
    clienteAEliminar = null;
    await cargarClientes();
  } catch (error) {
    cerrarModal(modalEliminar);
    mostrarToast(error.message, 'error');
  } finally {
    boton.disabled = false;
  }
});

cargarClientes();
