iniciarLayout('Notas de venta');

const tbodyNotasVenta = document.getElementById('tabla-notas-venta-body');
const mensajeNotasVenta = document.getElementById('notas-venta-mensaje');
const busquedaNotasVenta = document.getElementById('buscar-nota-venta');
const modalNuevaVenta = document.getElementById('modal-nueva-venta');
const modalDetalleVenta = document.getElementById('modal-detalle-venta');
const formNuevaVenta = document.getElementById('form-nueva-venta');
const selectClienteVenta = document.getElementById('venta-cliente');
const cuerpoLineasVenta = document.getElementById('venta-lineas-body');
const botonRegistrarVenta = document.getElementById('btn-registrar-venta');
let notasVenta = [];
let clientesVenta = [];
let productosVenta = [];
let temporizadorBusqueda;
let solicitudListado = 0;

function celda(texto) {
  const elemento = document.createElement('td');
  elemento.textContent = texto ?? '—';
  return elemento;
}

function normalizarFecha(fecha) {
  if (!fecha) return '—';
  const date = Array.isArray(fecha)
    ? new Date(fecha[0], fecha[1] - 1, fecha[2], fecha[3] || 0, fecha[4] || 0, fecha[5] || 0)
    : new Date(fecha);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('es-PE');
}

function formatoMoneda(valor) {
  return `S/ ${Number(valor || 0).toFixed(2)}`;
}

function renderizarNotasVenta() {
  tbodyNotasVenta.replaceChildren();
  if (!notasVenta.length) {
    const fila = document.createElement('tr');
    const vacio = celda('No hay notas de venta que coincidan con la búsqueda.');
    vacio.colSpan = 8;
    fila.appendChild(vacio);
    tbodyNotasVenta.appendChild(fila);
    return;
  }

  notasVenta.forEach((nota) => {
    const fila = document.createElement('tr');
    fila.append(
      celda(nota.numeroNota),
      celda(normalizarFecha(nota.fechaEmision)),
      celda(nota.clienteNombre),
      celda(nota.usuarioNombre),
      celda(nota.cantidadProductos),
      celda(formatoMoneda(nota.total)),
      celda(nota.estado),
    );
    const acciones = document.createElement('td');
    const detalle = document.createElement('button');
    detalle.type = 'button';
    detalle.className = 'btn btn-secundario ventas-accion';
    detalle.dataset.notaId = nota.id;
    detalle.textContent = 'Ver detalle';
    acciones.appendChild(detalle);
    fila.appendChild(acciones);
    tbodyNotasVenta.appendChild(fila);
  });
}

async function cargarNotasVenta() {
  const numeroSolicitud = ++solicitudListado;
  mensajeNotasVenta.textContent = 'Cargando notas de venta...';
  const termino = busquedaNotasVenta.value.trim();
  const ruta = termino
    ? `/api/notas-venta?buscar=${encodeURIComponent(termino)}`
    : '/api/notas-venta';
  try {
    const resultado = await apiGet(ruta);
    if (numeroSolicitud !== solicitudListado) return;
    notasVenta = resultado;
    mensajeNotasVenta.textContent =
      `${notasVenta.length} nota${notasVenta.length === 1 ? '' : 's'} de venta`;
    renderizarNotasVenta();
  } catch (error) {
    if (numeroSolicitud !== solicitudListado) return;
    notasVenta = [];
    mensajeNotasVenta.textContent = 'No se pudieron cargar las notas de venta.';
    renderizarNotasVenta();
    mostrarToast(error.message, 'error');
  }
}

function mostrarModal(modal) {
  abrirModalAccesible(modal);
}

function ocultarModal(modal) {
  cerrarModalAccesible(modal);
}

function cerrarModalesVenta() {
  [modalNuevaVenta, modalDetalleVenta].forEach((modal) => {
    if (!modal.hidden) ocultarModal(modal);
  });
}

function catalogoProducto(id) {
  return productosVenta.find((producto) => String(producto.id) === String(id));
}

function opcionesProductoVenta(seleccionado = '') {
  const usados = new Set([...cuerpoLineasVenta.querySelectorAll('.venta-producto')]
    .map((select) => select.value)
    .filter(Boolean));
  const disponibles = productosVenta.filter((producto) =>
    !usados.has(String(producto.id)) || String(producto.id) === String(seleccionado));
  return '<option value="">Selecciona un producto</option>' +
    disponibles.map((producto) =>
      `<option value="${escapar(producto.id)}" ${String(producto.id) === String(seleccionado) ? 'selected' : ''}>${escapar(producto.nombre)}</option>`)
      .join('');
}

function actualizarLineaVenta(fila) {
  const producto = catalogoProducto(fila.querySelector('.venta-producto').value);
  const stock = producto ? Number(producto.stockActual) : 0;
  const cantidad = Number(fila.querySelector('.venta-cantidad').value) || 0;
  const precio = producto ? Number(producto.precio) : 0;
  fila.querySelector('.venta-stock').textContent = producto ? String(stock) : '—';
  fila.querySelector('.venta-precio').textContent = formatoMoneda(precio);
  fila.querySelector('.venta-subtotal').textContent = formatoMoneda(precio * cantidad);
  fila.querySelector('.venta-cantidad').max = String(stock);
  actualizarTotalVenta();
}

function actualizarTotalVenta() {
  const total = [...cuerpoLineasVenta.querySelectorAll('.venta-linea')]
    .reduce((suma, fila) => {
      const producto = catalogoProducto(fila.querySelector('.venta-producto').value);
      return suma + (producto ? Number(producto.precio) : 0)
        * (Number(fila.querySelector('.venta-cantidad').value) || 0);
    }, 0);
  document.getElementById('venta-total').textContent = formatoMoneda(total);
}

function agregarLineaVenta() {
  if (!productosVenta.length) {
    mostrarToast('No hay productos disponibles para registrar una venta.', 'error');
    return;
  }
  const usados = new Set([...cuerpoLineasVenta.querySelectorAll('.venta-producto')]
    .map((select) => select.value)
    .filter(Boolean));
  if (productosVenta.every((producto) => usados.has(String(producto.id)))) {
    mostrarToast('Ya agregaste todos los productos disponibles.', 'info');
    return;
  }
  const fila = document.createElement('tr');
  fila.className = 'venta-linea';
  fila.innerHTML = `
    <td><select class="form-control venta-producto" required>${opcionesProductoVenta()}</select></td>
    <td class="venta-stock">—</td>
    <td><input class="form-control venta-cantidad" type="number" min="1" step="1" value="1" required /></td>
    <td class="venta-precio">S/ 0.00</td>
    <td class="venta-subtotal">S/ 0.00</td>
    <td><button type="button" class="btn btn-secundario venta-quitar-linea" aria-label="Quitar producto">Quitar</button></td>`;
  cuerpoLineasVenta.appendChild(fila);
  fila.querySelector('.venta-producto').addEventListener('change', () => {
    actualizarLineaVenta(fila);
    cuerpoLineasVenta.querySelectorAll('.venta-producto').forEach((select) => {
      const actual = select.value;
      select.innerHTML = opcionesProductoVenta(actual);
    });
  });
  fila.querySelector('.venta-cantidad').addEventListener('input', () => actualizarLineaVenta(fila));
}

function abrirFormularioVenta() {
  formNuevaVenta.reset();
  document.getElementById('venta-error').hidden = true;
  cuerpoLineasVenta.replaceChildren();
  selectClienteVenta.innerHTML = '<option value="">Selecciona un cliente</option>' +
    clientesVenta.map((cliente) =>
      `<option value="${escapar(cliente.id)}">${escapar(cliente.nombre)} · ${escapar(cliente.numeroDocumento)}</option>`)
      .join('');
  agregarLineaVenta();
  document.getElementById('venta-total').textContent = formatoMoneda(0);
  mostrarModal(modalNuevaVenta);
}

function renderizarDetalleVenta(nota) {
  document.getElementById('modal-detalle-venta-titulo').textContent = `Nota ${nota.numeroNota}`;
  document.getElementById('venta-detalle-resumen').textContent =
    `Fecha: ${normalizarFecha(nota.fechaEmision)} · Cliente: ${nota.clienteNombre} (${nota.clienteDocumento}) · Registrado por: ${nota.usuarioNombre}`;
  const tbody = document.getElementById('venta-detalle-lineas');
  tbody.replaceChildren();
  nota.productos.forEach((producto) => {
    const fila = document.createElement('tr');
    fila.append(
      celda(producto.productoNombre),
      celda(producto.cantidad),
      celda(formatoMoneda(producto.precioUnitario)),
      celda(formatoMoneda(producto.subtotal)),
    );
    tbody.appendChild(fila);
  });
  document.getElementById('venta-detalle-total').textContent = formatoMoneda(nota.total);
  mostrarModal(modalDetalleVenta);
}

document.getElementById('btn-nueva-venta').addEventListener('click', abrirFormularioVenta);
document.getElementById('btn-agregar-producto-venta').addEventListener('click', agregarLineaVenta);
document.querySelectorAll('[data-cerrar-venta]').forEach((boton) => {
  boton.addEventListener('click', cerrarModalesVenta);
});
document.querySelectorAll('.ventas-modal').forEach((modal) => {
  modal.addEventListener('click', (evento) => {
    if (evento.target === modal) ocultarModal(modal);
  });
});
document.addEventListener('keydown', (evento) => {
  if (evento.key === 'Escape') cerrarModalesVenta();
});

cuerpoLineasVenta.addEventListener('click', (evento) => {
  const boton = evento.target.closest('.venta-quitar-linea');
  if (!boton) return;
  boton.closest('.venta-linea').remove();
  cuerpoLineasVenta.querySelectorAll('.venta-producto').forEach((select) => {
    const actual = select.value;
    select.innerHTML = opcionesProductoVenta(actual);
  });
  actualizarTotalVenta();
});

tbodyNotasVenta.addEventListener('click', async (evento) => {
  const boton = evento.target.closest('[data-nota-id]');
  if (!boton) return;
  try {
    const nota = await apiGet(`/api/notas-venta/${boton.dataset.notaId}`);
    renderizarDetalleVenta(nota);
  } catch (error) {
    mostrarToast(error.message, 'error');
  }
});

busquedaNotasVenta.addEventListener('input', () => {
  clearTimeout(temporizadorBusqueda);
  temporizadorBusqueda = setTimeout(cargarNotasVenta, 250);
});

formNuevaVenta.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const error = document.getElementById('venta-error');
  error.hidden = true;
  if (!formNuevaVenta.reportValidity()) return;

  const filas = [...cuerpoLineasVenta.querySelectorAll('.venta-linea')];
  if (!filas.length) {
    error.textContent = 'Agrega al menos un producto a la nota.';
    error.hidden = false;
    return;
  }
  const productos = filas.map((fila) => ({
    productoId: Number(fila.querySelector('.venta-producto').value),
    cantidad: Number(fila.querySelector('.venta-cantidad').value),
  }));
  if (productos.some((producto) => !producto.productoId || producto.cantidad < 1)) {
    error.textContent = 'Selecciona un producto y una cantidad válida en cada línea.';
    error.hidden = false;
    return;
  }
  if (new Set(productos.map((producto) => producto.productoId)).size !== productos.length) {
    error.textContent = 'No repitas un producto en la misma nota de venta.';
    error.hidden = false;
    return;
  }
  const sinStock = productos.find((linea) => {
    const producto = catalogoProducto(linea.productoId);
    return !producto || linea.cantidad > Number(producto.stockActual);
  });
  if (sinStock) {
    error.textContent = `Stock insuficiente para ${catalogoProducto(sinStock.productoId)?.nombre || 'un producto'}.`;
    error.hidden = false;
    return;
  }

  botonRegistrarVenta.disabled = true;
  try {
    const nota = await apiPost('/api/notas-venta', {
      clienteId: Number(selectClienteVenta.value),
      productos,
    });
    cerrarModalesVenta();
    mostrarToast(`Venta registrada correctamente: ${nota.numeroNota}.`, 'exito');
    await cargarNotasVenta();
  } catch (exception) {
    error.textContent = exception.message;
    error.hidden = false;
  } finally {
    botonRegistrarVenta.disabled = false;
  }
});

async function cargarCatalogosVenta() {
  const botonNuevaVenta = document.getElementById('btn-nueva-venta');
  botonNuevaVenta.disabled = true;
  try {
    [clientesVenta, productosVenta] = await Promise.all([
      apiGet('/api/clientes'),
      apiGet('/api/productos'),
    ]);
    botonNuevaVenta.disabled = clientesVenta.length === 0 || productosVenta.length === 0;
    if (botonNuevaVenta.disabled) {
      mostrarToast('Registra al menos un cliente y un producto antes de vender.', 'info');
    }
  } catch (error) {
    mostrarToast(`No se pudieron cargar los catálogos: ${error.message}`, 'error');
  }
}

Promise.all([cargarCatalogosVenta(), cargarNotasVenta()]);
