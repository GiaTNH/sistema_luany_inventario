iniciarLayout(window.location.pathname.endsWith('/nueva-nota-venta.html') ? 'Nueva nota de venta' : 'Notas de venta');

const tbodyNotasVenta = document.getElementById('tabla-notas-venta-body');
const mensajeNotasVenta = document.getElementById('notas-venta-mensaje');
const busquedaNotasVenta = document.getElementById('buscar-nota-venta');
const modalDetalleVenta = document.getElementById('modal-detalle-venta');
const formNuevaVenta = document.getElementById('form-nueva-venta');
const selectClienteVenta = document.getElementById('venta-cliente');
const cuerpoLineasVenta = document.getElementById('venta-lineas-body');
const botonRegistrarVenta = document.getElementById('btn-registrar-venta');
let notaActualDetalle = null;
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

function documentoClienteVisible(documento) {
  return documento === 'CLIENTE-GENERAL' ? 'No proporcionado' : documento || '—';
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
  if (!modalDetalleVenta.hidden) ocultarModal(modalDetalleVenta);
}

function catalogoProducto(id) {
  return productosVenta.find((producto) => String(producto.id) === String(id));
}

function etiquetaProductoVenta(producto) {
  const codigo = producto.codigo?.trim();
  return `${producto.nombre}${codigo ? ` · ${codigo}` : ` · ID ${producto.id}`}`;
}

function actualizarListaProductosVenta() {
  const lista = document.getElementById('venta-productos-sugeridos');
  if (!lista) return;
  const usados = new Set([...cuerpoLineasVenta.querySelectorAll('.venta-producto')]
    .map((input) => input.dataset.productoId)
    .filter(Boolean));
  lista.innerHTML = productosVenta
    .filter((producto) => !usados.has(String(producto.id)))
    .map((producto) => `<option value="${escapar(etiquetaProductoVenta(producto))}"></option>`)
    .join('');
}

function buscarProductoPorEtiqueta(etiqueta) {
  return productosVenta.find((producto) => etiquetaProductoVenta(producto) === etiqueta.trim());
}

function actualizarLineaVenta(fila) {
  const producto = catalogoProducto(fila.dataset.productoId);
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
      const producto = catalogoProducto(fila.dataset.productoId);
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
    .map((input) => input.dataset.productoId)
    .filter(Boolean));
  if (productosVenta.every((producto) => usados.has(String(producto.id)))) {
    mostrarToast('Ya agregaste todos los productos disponibles.', 'info');
    return;
  }
  const fila = document.createElement('tr');
  fila.className = 'venta-linea';
  fila.innerHTML = `
    <td><input class="form-control venta-producto venta-producto-busqueda" type="search" list="venta-productos-sugeridos" placeholder="Buscar por nombre o código" autocomplete="off" required /></td>
    <td class="venta-stock">—</td>
    <td><input class="form-control venta-cantidad" type="number" min="1" step="1" value="1" required /></td>
    <td class="venta-precio">S/ 0.00</td>
    <td class="venta-subtotal">S/ 0.00</td>
    <td><button type="button" class="btn btn-secundario venta-quitar-linea" aria-label="Quitar producto">Quitar</button></td>`;
  cuerpoLineasVenta.appendChild(fila);
  const inputProducto = fila.querySelector('.venta-producto');
  inputProducto.addEventListener('input', () => {
    const producto = buscarProductoPorEtiqueta(inputProducto.value);
    fila.dataset.productoId = producto ? String(producto.id) : '';
    actualizarLineaVenta(fila);
    actualizarListaProductosVenta();
  });
  fila.querySelector('.venta-cantidad').addEventListener('input', () => actualizarLineaVenta(fila));
  actualizarListaProductosVenta();
}

function quitarLineaVenta(evento) {
  const boton = evento.target.closest('.venta-quitar-linea');
  if (!boton) return;
  boton.closest('.venta-linea').remove();
  actualizarListaProductosVenta();
  actualizarTotalVenta();
}

function generarNotaImprimible(nota) {
  const filas = nota.productos.map((producto) => `
    <tr><td>${escapar(producto.productoNombre)}</td><td>${Number(producto.cantidad)}</td>
    <td>${escapar(formatoMoneda(producto.precioUnitario))}</td><td>${escapar(formatoMoneda(producto.subtotal))}</td></tr>
  `).join('');
  const numero = String(nota.numeroNota || '').match(/\d+/g)?.join('') || nota.numeroNota;
  const numeroVisible = numero ? numero.padStart(3, '0') : '—';
  return `<article class="nota-venta-imprimible">
    <header class="nota-venta-encabezado">
      <div class="nota-venta-marca">
        <img class="nota-venta-logo" src="/img/logo_luany.png" alt="LUANY - Juega, Viste, Crece" />
      </div>
      <div class="nota-venta-tienda"><strong>Luany - tienda para bebés</strong><span>Juega · Viste · Crece</span></div>
    </header>
    <h2>Nota de Venta Nº ${escapar(numeroVisible)}</h2>
    <section class="nota-venta-datos">
      <p><strong>Fecha y hora de emisión:</strong> ${escapar(normalizarFecha(nota.fechaEmision))}</p>
      <p><strong>Cliente:</strong> ${escapar(nota.clienteNombre || 'Cliente general')}</p>
      <p><strong>Documento:</strong> ${escapar(documentoClienteVisible(nota.clienteDocumento))}</p>
      <p><strong>Registrado por:</strong> ${escapar(nota.usuarioNombre || '—')}</p>
    </section>
    <table><thead><tr><th>Producto</th><th>Cantidad</th><th>Precio unitario</th><th>Importe</th></tr></thead><tbody>${filas}</tbody></table>
    <p class="nota-venta-total">Total de la venta: ${escapar(formatoMoneda(nota.total))}</p>
    <p class="nota-venta-legal">DOCUMENTO NO VÁLIDO COMO COMPROBANTE DE PAGO</p>
    <p class="nota-venta-gracias">¡Gracias por tu compra!</p>
    <p class="nota-venta-eslogan">Acompañamos cada etapa de tu bebé</p>
  </article>`;
}

function abrirNotaImprimible(nota) {
  notaActualDetalle = nota;
  document.getElementById('modal-detalle-venta-titulo').textContent = `Nota ${nota.numeroNota}`;
  document.getElementById('venta-documento-imprimible').innerHTML = generarNotaImprimible(nota);
  mostrarModal(modalDetalleVenta);
}

function renderizarDetalleVenta(nota) {
  abrirNotaImprimible(nota);
}

document.getElementById('btn-agregar-producto-venta')?.addEventListener('click', agregarLineaVenta);
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
window.addEventListener('afterprint', () => document.body.classList.remove('printing-venta'));

cuerpoLineasVenta?.addEventListener('click', quitarLineaVenta);

tbodyNotasVenta?.addEventListener('click', async (evento) => {
  const boton = evento.target.closest('[data-nota-id]');
  if (!boton) return;
  try {
    const nota = await apiGet(`/api/notas-venta/${boton.dataset.notaId}`);
    renderizarDetalleVenta(nota);
  } catch (error) {
    mostrarToast(error.message, 'error');
  }
});

busquedaNotasVenta?.addEventListener('input', () => {
  clearTimeout(temporizadorBusqueda);
  temporizadorBusqueda = setTimeout(cargarNotasVenta, 250);
});

async function cargarCatalogosVenta() {
  const botonNuevaVenta = document.getElementById('btn-nueva-venta');
  if (botonNuevaVenta) botonNuevaVenta.setAttribute('aria-disabled', 'true');
  try {
    [clientesVenta, productosVenta] = await Promise.all([
      apiGet('/api/clientes'),
      apiGet('/api/productos'),
    ]);
    if (selectClienteVenta) {
      const clientesEspecificos = clientesVenta.filter((cliente) =>
        cliente.numeroDocumento?.trim().toUpperCase() !== 'CLIENTE-GENERAL');
      selectClienteVenta.innerHTML = '<option value="">Cliente general (sin datos personales)</option>' +
        clientesEspecificos.map((cliente) =>
          `<option value="${escapar(cliente.id)}">${escapar(cliente.nombre)} · ${escapar(cliente.numeroDocumento)}</option>`)
          .join('');
    }
    if (botonNuevaVenta) {
      botonNuevaVenta.removeAttribute('aria-disabled');
      if (!productosVenta.length) {
        botonNuevaVenta.setAttribute('aria-disabled', 'true');
        mostrarToast('No hay productos disponibles para registrar una venta.', 'info');
      }
    }
  } catch (error) {
    mostrarToast(`No se pudieron cargar los catálogos: ${error.message}`, 'error');
    if (botonNuevaVenta) botonNuevaVenta.setAttribute('aria-disabled', 'true');
  }
}

if (tbodyNotasVenta) {
  cargarNotasVenta();
}

if (formNuevaVenta) {
  cargarCatalogosVenta().then(() => {
    if (productosVenta.length) agregarLineaVenta();
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
      productoId: Number(fila.dataset.productoId),
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
        clienteId: selectClienteVenta.value ? Number(selectClienteVenta.value) : null,
        productos,
      });
      mostrarToast(`Venta registrada correctamente: ${nota.numeroNota}.`, 'exito');
      try {
        abrirNotaImprimible(nota);
      } catch (exception) {
        mostrarToast(
          `La venta ${nota.numeroNota} se guardó, pero no se pudo abrir la vista imprimible: ${exception.message}`,
          'error',
        );
      }
    } finally {
      botonRegistrarVenta.disabled = false;
    }
  });
}

document.getElementById('btn-imprimir-nota')?.addEventListener('click', () => {
  if (!notaActualDetalle) return;
  document.body.classList.add('printing-venta');
  window.print();
});
