iniciarLayout('Pedidos');

const tbodyPedidos = document.getElementById('tabla-pedidos-body');
const mensajePedidos = document.getElementById('pedidos-mensaje');
const filtroBuscar = document.getElementById('buscar-pedido');
const filtroEstado = document.getElementById('filtro-pedido-estado');
filtroBuscar.value = new URLSearchParams(window.location.search).get('buscar') || '';
const pedidoIdExacto = new URLSearchParams(window.location.search).get('pedidoId');
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
let reposicionesPendientes = [];
let reposicionActiva = false;
let guardandoPedido = false;
const claveReposicion = 'luany-reposicion-pendiente';

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
  if (pedidoIdExacto) parametros.set('pedidoId', pedidoIdExacto);
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
  abrirModalAccesible(modal);
}

function ocultarModal(modal) {
  if (modal === modalPedido && reposicionActiva) cancelarReposicionPreparada();
  cerrarModalAccesible(modal);
}

function cancelarReposicionPreparada() {
  reposicionActiva = false;
  reposicionesPendientes = [];
  sessionStorage.removeItem(claveReposicion);
  const url = new URL(window.location.href);
  url.searchParams.delete('reponer');
  window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`);
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

function opcionesProveedoresActivos() {
  return proveedores.filter((proveedor) =>
    String(proveedor.estado || '').trim().toUpperCase() === 'ACTIVO');
}

function mostrarGrupoReposicion(proveedorId) {
  const grupo = reposicionesPendientes.find((item) => String(item.proveedorId) === String(proveedorId));
  if (!grupo) return false;
  formPedido.reset();
  cuerpoLineasPedido.replaceChildren();
  selectProveedor.value = String(grupo.proveedorId);
  grupo.productos.forEach((sugerido) => {
    agregarLineaPedido();
    const fila = cuerpoLineasPedido.lastElementChild;
    const productoSelect = fila.querySelector('.pedido-producto');
    productoSelect.value = String(sugerido.producto.id);
    fila.querySelector('.pedido-cantidad').value = String(sugerido.cantidad);
    fila.querySelector('.pedido-precio').value = Number(sugerido.producto.precio).toFixed(2);
  });
  document.getElementById('pedido-error').hidden = true;
  const ayuda = document.getElementById('pedido-reposicion-ayuda');
  ayuda.textContent = `Reposición para ${grupo.proveedorNombre}: revisa o edita los productos y cantidades. Cada proveedor requiere confirmar su propio pedido; el stock solo cambiará al registrar la recepción.`;
  ayuda.hidden = false;
  return true;
}

function agruparReposiciones(productosSugeridos) {
  const proveedoresActivos = new Map(opcionesProveedoresActivos()
    .map((proveedor) => [String(proveedor.id), proveedor]));
  const productosPorId = new Map(productos.map((producto) => [String(producto.id), producto]));
  const grupos = new Map();
  productosSugeridos.forEach((sugerido) => {
    const producto = productosPorId.get(String(sugerido.productoId));
    const proveedor = producto && proveedoresActivos.get(String(producto.proveedor?.id));
    const cantidad = Number(sugerido.cantidad);
    if (!producto || !proveedor || !Number.isSafeInteger(cantidad) || cantidad < 1) return;
    const id = String(proveedor.id);
    if (!grupos.has(id)) grupos.set(id, { proveedorId: proveedor.id, proveedorNombre: proveedor.nombre, productos: [] });
    grupos.get(id).productos.push({ producto, cantidad });
  });
  return [...grupos.values()];
}

function persistirReposicionesPendientes() {
  const restantes = reposicionesPendientes.flatMap((grupo) => grupo.productos.map((item) => ({
    productoId: Number(item.producto.id),
    cantidad: Number(item.cantidad),
  })));
  if (restantes.length) {
    sessionStorage.setItem(claveReposicion, JSON.stringify(restantes));
  } else {
    sessionStorage.removeItem(claveReposicion);
  }
}

function prepararReposicionDesdeAlertas() {
  let sugeridos;
  try {
    sugeridos = JSON.parse(sessionStorage.getItem(claveReposicion) || '[]');
  } catch (error) {
    sessionStorage.removeItem(claveReposicion);
    mostrarToast('No se pudo leer la reposición preparada. Vuelve a intentarlo desde Alertas.', 'error');
    return;
  }
  if (!Array.isArray(sugeridos)) {
    sessionStorage.removeItem(claveReposicion);
    mostrarToast('La reposición preparada no tiene un formato válido.', 'error');
    return;
  }
  reposicionesPendientes = agruparReposiciones(sugeridos);
  if (!reposicionesPendientes.length) {
    sessionStorage.removeItem(claveReposicion);
    mostrarToast('No se encontraron productos válidos con proveedores activos para reponer.', 'error');
    return;
  }
  reposicionActiva = true;
  const url = new URL(window.location.href);
  url.searchParams.delete('reponer');
  window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`);
  abrirNuevoPedido();
  const cantidadPreparada = reposicionesPendientes.reduce(
    (total, grupo) => total + grupo.productos.length,
    0,
  );
  if (cantidadPreparada < sugeridos.length) {
    mostrarToast('Algunos productos no se pudieron preparar porque no tienen un proveedor activo asociado.', 'error');
  }
  if (reposicionesPendientes.length > 1) {
    mostrarToast(`Hay ${reposicionesPendientes.length} proveedores; revisa y confirma cada pedido por separado.`, 'info');
  }
}

function abrirNuevoPedido() {
  formPedido.reset();
  document.getElementById('pedido-error').hidden = true;
  document.getElementById('pedido-reposicion-ayuda').hidden = true;
  cuerpoLineasPedido.replaceChildren();
  const proveedoresFormulario = reposicionActiva
    ? opcionesProveedoresActivos().filter((proveedor) => reposicionesPendientes.some((grupo) =>
      String(grupo.proveedorId) === String(proveedor.id)))
    : opcionesProveedoresActivos();
  selectProveedor.innerHTML = '<option value="">Selecciona proveedor</option>' +
    proveedoresFormulario.map((proveedor) =>
      `<option value="${escapar(proveedor.id)}">${escapar(proveedor.nombre)}</option>`).join('');
  if (!reposicionActiva || !reposicionesPendientes.length) agregarLineaPedido();
  mostrarModal(modalPedido);
  if (reposicionActiva && reposicionesPendientes.length) {
    mostrarGrupoReposicion(reposicionesPendientes[0].proveedorId);
  }
}

function renderizarDetalle(pedido) {
  document.getElementById('titulo-detalle-pedido').textContent = `Detalle del pedido #${pedido.id}`;
  const resumen = document.getElementById('pedido-detalle-resumen');
  resumen.replaceChildren();
  [
    ['Proveedor', pedido.proveedorNombre],
    ['Fecha del pedido', horaLocal(pedido.fechaPedido)],
    ['Entrega estimada', fechaCorta(pedido.fechaEstimadaEntrega)],
    ['Registrado por', pedido.usuarioNombre],
    ['Estado', pedido.estado],
  ].forEach(([etiqueta, valor]) => {
    const bloque = document.createElement('div');
    const titulo = document.createElement('dt');
    titulo.textContent = etiqueta;
    const contenido = document.createElement('dd');
    contenido.textContent = valor || '—';
    bloque.append(titulo, contenido);
    resumen.appendChild(bloque);
  });
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
      celda(`S/ ${Number(producto.subtotal).toFixed(2)}`),
    );
    tbody.appendChild(fila);
  });
  document.getElementById('pedido-detalle-total').textContent =
    `S/ ${Number(pedido.total).toFixed(2)}`;

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
  if (reposicionActiva && mostrarGrupoReposicion(selectProveedor.value)) return;
  cuerpoLineasPedido.querySelectorAll('.pedido-producto').forEach((select) => {
    const valor = select.value;
    select.innerHTML = opcionesProducto(valor);
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
  if (guardandoPedido) return;
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
  if (datos.productos.some((item) => !Number.isSafeInteger(item.cantidad) || item.cantidad < 1
      || !Number.isFinite(item.precio) || item.precio < 0
      || String(productos.find((producto) => Number(producto.id) === item.productoId)?.proveedor?.id)
        !== String(datos.proveedorId))) {
    error.textContent = 'Verifica que los productos pertenezcan al proveedor y que las cantidades y precios sean válidos.';
    error.hidden = false;
    return;
  }

  guardandoPedido = true;
  botonGuardarPedido.disabled = true;
  try {
    await apiPost('/api/pedidos', datos);
    if (reposicionActiva) {
      reposicionesPendientes = reposicionesPendientes.filter((grupo) =>
        String(grupo.proveedorId) !== String(datos.proveedorId));
      persistirReposicionesPendientes();
      if (reposicionesPendientes.length) {
        mostrarGrupoReposicion(reposicionesPendientes[0].proveedorId);
        mostrarToast('Pedido registrado. Revisa y confirma el siguiente proveedor; el stock se actualizará al recibir.', 'exito');
      } else {
        reposicionActiva = false;
        cerrarModales();
        mostrarToast('Pedidos de reposición registrados. El stock se actualizará al confirmar cada recepción.', 'exito');
      }
    } else {
      cerrarModales();
      mostrarToast('Pedido registrado. El stock se actualizará al confirmar la recepción.', 'exito');
    }
    await cargarPedidos();
  } catch (exception) {
    error.textContent = exception.message;
    error.hidden = false;
  } finally {
    guardandoPedido = false;
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
    if (new URLSearchParams(window.location.search).get('reponer') === '1') {
      prepararReposicionDesdeAlertas();
    }
  } catch (error) {
    mostrarToast(`No se pudieron cargar los catálogos: ${error.message}`, 'error');
  }
}

Promise.all([cargarCatalogos(), cargarPedidos()]);
