iniciarLayout('Pedidos');

const tbodyPedidos = document.getElementById('tabla-pedidos-body');
const mensajePedidos = document.getElementById('pedidos-mensaje');
const filtroBuscar = document.getElementById('buscar-pedido');
const filtroEstado = document.getElementById('filtro-pedido-estado');
const modalPedido = document.getElementById('modal-pedido');
const modalDetallePedido = document.getElementById('modal-detalle-pedido');
const modalRecepcion = document.getElementById('modal-recepcion');
const formPedido = document.getElementById('form-pedido');
const formRecepcion = document.getElementById('form-recepcion');
const selectProveedor = document.getElementById('pedido-proveedor');
const cuerpoLineasPedido = document.getElementById('pedido-lineas-body');
const cuerpoLineasRecepcion = document.getElementById('recepcion-lineas-body');
const botonGuardarPedido = document.getElementById('btn-guardar-pedido');
const botonConfirmarRecepcion = document.getElementById('btn-confirmar-recepcion');
const botonNuevoPedido = document.getElementById('btn-nuevo-pedido');
let pedidos = [];
let proveedores = [];
let productos = [];
let pedidoRecepcionActual = null;
let claveIdempotenciaRecepcion = null;
let temporizadorBusqueda;
let solicitudListado = 0;

function celda(texto) {
  const elemento = document.createElement('td');
  elemento.textContent = texto ?? '—';
  return elemento;
}

function fechaCorta(valor) {
  if (!valor) return '—';
  const fecha = Array.isArray(valor)
    ? new Date(valor[0], valor[1] - 1, valor[2])
    : new Date(`${valor}`.slice(0, 10) + 'T00:00:00');
  return Number.isNaN(fecha.getTime()) ? '—' : fecha.toLocaleDateString('es-PE');
}

function horaLocal(valor) {
  if (!valor) return '—';
  const fecha = Array.isArray(valor)
    ? new Date(valor[0], valor[1] - 1, valor[2], valor[3] || 0, valor[4] || 0, valor[5] || 0)
    : new Date(valor);
  return Number.isNaN(fecha.getTime()) ? '—' : fecha.toLocaleString('es-PE');
}

function estadoPedidoBadge(estado) {
  const clases = { PENDIENTE: 'badge-bajo', PARCIAL: 'badge-exceso', RECIBIDO: 'badge-normal' };
  const etiquetas = { PENDIENTE: 'Pendiente', PARCIAL: 'Recepción parcial', RECIBIDO: 'Recibido' };
  const badge = document.createElement('span');
  badge.className = `badge ${clases[estado] || 'badge-normal'}`;
  badge.textContent = etiquetas[estado] || estado || '—';
  return badge;
}

function renderizarPedidos() {
  tbodyPedidos.replaceChildren();
  if (!pedidos.length) {
    const fila = document.createElement('tr');
    const vacio = celda('No hay pedidos que coincidan con los filtros.');
    vacio.colSpan = 7;
    fila.appendChild(vacio);
    tbodyPedidos.appendChild(fila);
    return;
  }

  pedidos.forEach((pedido) => {
    const fila = document.createElement('tr');
    fila.appendChild(celda(`#${pedido.id}`));
    fila.appendChild(celda(pedido.proveedorNombre));
    fila.appendChild(celda(fechaCorta(pedido.fechaPedido)));
    fila.appendChild(celda(fechaCorta(pedido.fechaEstimadaEntrega)));
    fila.appendChild(celda(pedido.totalProductos));
    const estado = document.createElement('td');
    estado.appendChild(estadoPedidoBadge(pedido.estado));
    fila.appendChild(estado);

    const acciones = document.createElement('td');
    const detalle = document.createElement('button');
    detalle.type = 'button';
    detalle.className = 'btn btn-secundario pedidos-accion';
    detalle.dataset.accion = 'detalle';
    detalle.dataset.pedidoId = pedido.id;
    detalle.textContent = 'Ver detalle';
    acciones.appendChild(detalle);
    if (pedido.estado !== 'RECIBIDO') {
      const recibir = document.createElement('button');
      recibir.type = 'button';
      recibir.className = 'btn btn-primario pedidos-accion';
      recibir.dataset.accion = 'recibir';
      recibir.dataset.pedidoId = pedido.id;
      recibir.textContent = 'Recibir';
      acciones.appendChild(recibir);
    }
    fila.appendChild(acciones);
    tbodyPedidos.appendChild(fila);
  });
}

async function cargarPedidos() {
  const numeroSolicitud = ++solicitudListado;
  mensajePedidos.textContent = 'Cargando pedidos...';
  const parametros = new URLSearchParams();
  if (filtroBuscar.value.trim()) parametros.set('buscar', filtroBuscar.value.trim());
  if (filtroEstado.value) parametros.set('estado', filtroEstado.value);
  const query = parametros.toString();
  try {
    const resultado = await apiGet(`/api/pedidos${query ? `?${query}` : ''}`);
    if (numeroSolicitud !== solicitudListado) return;
    pedidos = resultado;
    mensajePedidos.textContent = `${pedidos.length} pedido${pedidos.length === 1 ? '' : 's'}`;
    renderizarPedidos();
  } catch (error) {
    if (numeroSolicitud !== solicitudListado) return;
    pedidos = [];
    mensajePedidos.textContent = 'No se pudieron cargar los pedidos.';
    renderizarPedidos();
    mostrarToast(error.message, 'error');
  }
}

function mostrarModal(modal) {
  modal.hidden = false;
  modal.setAttribute('aria-hidden', 'false');
  requestAnimationFrame(() => modal.classList.add('visible'));
}

function ocultarModal(modal) {
  modal.classList.remove('visible');
  modal.hidden = true;
  modal.setAttribute('aria-hidden', 'true');
}

function cerrarModales() {
  [modalPedido, modalDetallePedido, modalRecepcion].forEach((modal) => {
    if (!modal.hidden) ocultarModal(modal);
  });
}

function productosDeProveedor() {
  const proveedorId = selectProveedor.value;
  return productos.filter((producto) => String(producto.proveedor?.id) === proveedorId);
}

function opcionesProducto(valor = '') {
  const opciones = productosDeProveedor()
    .map((producto) => `<option value="${escapar(producto.id)}" ${String(producto.id) === String(valor) ? 'selected' : ''}>${escapar(producto.nombre)}</option>`)
    .join('');
  return `<option value="">Selecciona producto</option>${opciones}`;
}

function agregarLineaPedido() {
  const fila = document.createElement('tr');
  fila.className = 'pedido-linea';
  fila.innerHTML = `
    <td><select class="form-control pedido-producto" required>${opcionesProducto()}</select></td>
    <td><input class="form-control pedido-cantidad" type="number" min="1" step="1" value="1" required /></td>
    <td><input class="form-control pedido-precio" type="number" min="0" step="0.01" value="0.00" required /></td>
    <td><button type="button" class="btn btn-secundario pedidos-quitar-linea" aria-label="Quitar producto">Quitar</button></td>`;
  cuerpoLineasPedido.appendChild(fila);
}

function abrirNuevoPedido() {
  formPedido.reset();
  document.getElementById('pedido-error').hidden = true;
  cuerpoLineasPedido.replaceChildren();
  const proveedorActivo = proveedores.filter((proveedor) =>
    String(proveedor.estado || '').toUpperCase() !== 'INACTIVO');
  selectProveedor.innerHTML = '<option value="">Selecciona proveedor</option>' +
    proveedorActivo.map((proveedor) =>
      `<option value="${escapar(proveedor.id)}">${escapar(proveedor.nombre)}</option>`).join('');
  agregarLineaPedido();
  mostrarModal(modalPedido);
}

function renderizarDetalle(pedido) {
  document.getElementById('titulo-detalle-pedido').textContent = `Detalle del pedido #${pedido.id}`;
  document.getElementById('pedido-detalle-resumen').textContent =
    `Proveedor: ${pedido.proveedorNombre} · Creado por: ${pedido.usuarioNombre} · Estado: ${pedido.estado}`;
  const tbody = document.getElementById('pedido-detalle-lineas');
  tbody.replaceChildren();
  pedido.productos.forEach((producto) => {
    const fila = document.createElement('tr');
    fila.append(
      celda(producto.productoNombre),
      celda(producto.cantidadPedida),
      celda(producto.cantidadRecibida),
      celda(producto.cantidadPendiente),
      celda(`S/ ${Number(producto.precio).toFixed(2)}`),
    );
    tbody.appendChild(fila);
  });

  const historial = document.getElementById('pedido-historial-recepciones');
  historial.replaceChildren();
  if (!pedido.recepciones.length) {
    const vacio = document.createElement('p');
    vacio.className = 'text-suave';
    vacio.textContent = 'Todavía no hay recepciones registradas.';
    historial.appendChild(vacio);
    return;
  }
  pedido.recepciones.forEach((recepcion) => {
    const bloque = document.createElement('div');
    bloque.className = 'pedido-recepcion-historica';
    const titulo = document.createElement('p');
    titulo.textContent = `Recepción #${recepcion.id} · ${horaLocal(recepcion.fecha)} · ${recepcion.usuarioNombre}`;
    const lista = document.createElement('ul');
    recepcion.productos.forEach((producto) => {
      const linea = document.createElement('li');
      linea.textContent = `${producto.productoNombre}: ${producto.cantidad} recibidas (movimiento #${producto.movimientoId})`;
      lista.appendChild(linea);
    });
    bloque.append(titulo, lista);
    historial.appendChild(bloque);
  });
}

async function abrirDetallePedido(id, modalDestino = modalDetallePedido) {
  try {
    const pedido = await apiGet(`/api/pedidos/${id}`);
    if (modalDestino === modalRecepcion) {
      prepararRecepcion(pedido);
    } else {
      renderizarDetalle(pedido);
      mostrarModal(modalDetallePedido);
    }
  } catch (error) {
    mostrarToast(error.message, 'error');
  }
}

function prepararRecepcion(pedido) {
  pedidoRecepcionActual = pedido;
  const claveStorage = `pedido-recepcion-request-${pedido.id}`;
  claveIdempotenciaRecepcion = sessionStorage.getItem(claveStorage);
  if (!claveIdempotenciaRecepcion) {
    claveIdempotenciaRecepcion = crypto.randomUUID();
    sessionStorage.setItem(claveStorage, claveIdempotenciaRecepcion);
  }
  document.getElementById('titulo-recepcion-pedido').textContent = `Recibir pedido #${pedido.id}`;
  document.getElementById('recepcion-ayuda').textContent =
    `Proveedor: ${pedido.proveedorNombre}. Registra únicamente las cantidades recibidas en esta entrega.`;
  document.getElementById('recepcion-error').hidden = true;
  cuerpoLineasRecepcion.replaceChildren();
  pedido.productos.filter((producto) => producto.cantidadPendiente > 0).forEach((producto) => {
    const fila = document.createElement('tr');
    fila.dataset.detallePedidoId = producto.detallePedidoId;
    fila.innerHTML = `
      <td>${escapar(producto.productoNombre)}</td>
      <td>${producto.cantidadPendiente}</td>
      <td><input class="form-control recepcion-cantidad" type="number" min="0" max="${producto.cantidadPendiente}" step="1" value="0" aria-label="Cantidad recibida de ${escapar(producto.productoNombre)}" /></td>`;
    cuerpoLineasRecepcion.appendChild(fila);
  });
  mostrarModal(modalRecepcion);
}

document.getElementById('btn-nuevo-pedido').addEventListener('click', abrirNuevoPedido);
document.getElementById('btn-agregar-linea-pedido').addEventListener('click', agregarLineaPedido);
document.querySelectorAll('[data-cerrar-modal]').forEach((boton) => {
  boton.addEventListener('click', cerrarModales);
});
document.querySelectorAll('.pedidos-modal').forEach((modal) => {
  modal.addEventListener('click', (evento) => {
    if (evento.target === modal) ocultarModal(modal);
  });
});
document.addEventListener('keydown', (evento) => {
  if (evento.key === 'Escape') cerrarModales();
});

selectProveedor.addEventListener('change', () => {
  cuerpoLineasPedido.querySelectorAll('.pedido-producto').forEach((select) => {
    select.innerHTML = opcionesProducto();
  });
});

cuerpoLineasPedido.addEventListener('click', (evento) => {
  const boton = evento.target.closest('.pedidos-quitar-linea');
  if (!boton) return;
  boton.closest('tr').remove();
});

tbodyPedidos.addEventListener('click', (evento) => {
  const boton = evento.target.closest('[data-accion]');
  if (!boton) return;
  const modalDestino = boton.dataset.accion === 'recibir' ? modalRecepcion : modalDetallePedido;
  abrirDetallePedido(boton.dataset.pedidoId, modalDestino);
});

filtroBuscar.addEventListener('input', () => {
  clearTimeout(temporizadorBusqueda);
  temporizadorBusqueda = setTimeout(cargarPedidos, 250);
});
filtroEstado.addEventListener('change', cargarPedidos);

formPedido.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const error = document.getElementById('pedido-error');
  error.hidden = true;
  if (!formPedido.reportValidity()) return;

  const lineas = [...cuerpoLineasPedido.querySelectorAll('.pedido-linea')];
  if (!lineas.length) {
    error.textContent = 'Agrega al menos un producto al pedido.';
    error.hidden = false;
    return;
  }
  const datos = {
    proveedorId: Number(selectProveedor.value),
    fechaEstimadaEntrega: document.getElementById('pedido-fecha-estimada').value || null,
    productos: lineas.map((fila) => ({
      productoId: Number(fila.querySelector('.pedido-producto').value),
      cantidad: Number(fila.querySelector('.pedido-cantidad').value),
      precio: Number(fila.querySelector('.pedido-precio').value),
    })),
  };
  if (new Set(datos.productos.map((item) => item.productoId)).size !== datos.productos.length) {
    error.textContent = 'No repitas un producto en el mismo pedido.';
    error.hidden = false;
    return;
  }

  botonGuardarPedido.disabled = true;
  try {
    await apiPost('/api/pedidos', datos);
    cerrarModales();
    mostrarToast('Pedido registrado. El stock se actualizará al confirmar la recepción.', 'exito');
    await cargarPedidos();
  } catch (exception) {
    error.textContent = exception.message;
    error.hidden = false;
  } finally {
    botonGuardarPedido.disabled = false;
  }
});

formRecepcion.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const error = document.getElementById('recepcion-error');
  error.hidden = true;
  const productosRecibidos = [...cuerpoLineasRecepcion.querySelectorAll('tr')]
    .map((fila) => ({
      detallePedidoId: Number(fila.dataset.detallePedidoId),
      cantidad: Number(fila.querySelector('.recepcion-cantidad').value),
    }))
    .filter((item) => item.cantidad > 0);
  if (!productosRecibidos.length) {
    error.textContent = 'Indica al menos una cantidad recibida.';
    error.hidden = false;
    return;
  }
  if (productosRecibidos.some((item) => item.cantidad > Number(
    cuerpoLineasRecepcion.querySelector(`[data-detalle-pedido-id="${item.detallePedidoId}"] .recepcion-cantidad`).max,
  ))) {
    error.textContent = 'Una cantidad supera lo pendiente para ese producto.';
    error.hidden = false;
    return;
  }

  botonConfirmarRecepcion.disabled = true;
  try {
    const actualizado = await apiPost(
      `/api/pedidos/${pedidoRecepcionActual.id}/recepciones`,
      { claveIdempotencia: claveIdempotenciaRecepcion, productos: productosRecibidos },
    );
    sessionStorage.removeItem(`pedido-recepcion-request-${pedidoRecepcionActual.id}`);
    claveIdempotenciaRecepcion = null;
    cerrarModales();
    mostrarToast('Recepción confirmada y stock actualizado correctamente.', 'exito');
    await cargarPedidos();
    renderizarDetalle(actualizado);
    mostrarModal(modalDetallePedido);
  } catch (exception) {
    error.textContent = exception.message;
    error.hidden = false;
  } finally {
    botonConfirmarRecepcion.disabled = false;
  }
});

async function cargarCatalogos() {
  botonNuevoPedido.disabled = true;
  try {
    [proveedores, productos] = await Promise.all([
      apiGet('/api/proveedores'),
      apiGet('/api/productos'),
    ]);
    botonNuevoPedido.disabled = false;
  } catch (error) {
    mostrarToast(`No se pudieron cargar los catálogos: ${error.message}`, 'error');
  }
}

Promise.all([cargarCatalogos(), cargarPedidos()]);
