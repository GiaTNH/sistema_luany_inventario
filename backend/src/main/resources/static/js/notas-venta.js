iniciarLayout(window.location.pathname.endsWith('/nueva-nota-venta.html') ? 'Nueva nota de venta' : 'Ventas');

const tbodyNotasVenta = document.getElementById('tabla-notas-venta-body');
const mensajeNotasVenta = document.getElementById('notas-venta-mensaje');
const busquedaNotasVenta = document.getElementById('buscar-nota-venta');
const modalDetalleVenta = document.getElementById('modal-detalle-venta');
const modalNuevoClienteVenta = document.getElementById('modal-nuevo-cliente-venta');
const formNuevaVenta = document.getElementById('form-nueva-venta');
const selectClienteVenta = document.getElementById('venta-cliente');
const formNuevoClienteVenta = document.getElementById('form-nuevo-cliente-venta');
const errorNuevoClienteVenta = document.getElementById('error-nuevo-cliente-venta');
const botonGuardarClienteVenta = document.getElementById('btn-guardar-nuevo-cliente-venta');
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
  return formatearFechaHoraLuany(fecha);
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

function abrirModalNuevoClienteVenta() {
  formNuevoClienteVenta.reset();
  errorNuevoClienteVenta.hidden = true;
  mostrarModal(modalNuevoClienteVenta);
}

function cerrarModalNuevoClienteVenta() {
  ocultarModal(modalNuevoClienteVenta);
  formNuevoClienteVenta.reset();
  errorNuevoClienteVenta.hidden = true;
  botonGuardarClienteVenta.disabled = false;
}

function cerrarModalesVenta() {
  if (!modalDetalleVenta.hidden) ocultarModal(modalDetalleVenta);
}

function renderizarSelectorClientesVenta(clienteSeleccionadoId = selectClienteVenta?.value || '') {
  if (!selectClienteVenta) return;
  const clientesEspecificos = clientesVenta.filter((cliente) =>
    cliente.numeroDocumento?.trim().toUpperCase() !== 'CLIENTE-GENERAL');
  selectClienteVenta.innerHTML = '<option value="">Cliente general (sin datos personales)</option>' +
    clientesEspecificos.map((cliente) =>
      `<option value="${escapar(cliente.id)}">${escapar(cliente.nombre)} · ${escapar(cliente.numeroDocumento)}</option>`)
      .join('');
  if (clienteSeleccionadoId) selectClienteVenta.value = String(clienteSeleccionadoId);
}

async function guardarNuevoClienteVenta(evento) {
  evento.preventDefault();
  errorNuevoClienteVenta.hidden = true;
  if (!formNuevoClienteVenta.reportValidity()) return;

  botonGuardarClienteVenta.disabled = true;
  const datos = {
    nombre: document.getElementById('venta-cliente-nombre').value.trim(),
    tipoDocumento: document.getElementById('venta-cliente-tipo-documento').value,
    numeroDocumento: document.getElementById('venta-cliente-numero-documento').value.trim(),
    telefono: document.getElementById('venta-cliente-telefono').value.trim(),
    direccion: document.getElementById('venta-cliente-direccion').value.trim(),
  };

  try {
    const cliente = await apiPost('/api/clientes', datos);
    if (!cliente?.id) throw new Error('El servidor no devolvió los datos del cliente creado.');
    clientesVenta = [
      ...clientesVenta.filter((existente) => String(existente.id) !== String(cliente.id)),
      cliente,
    ];
    renderizarSelectorClientesVenta(cliente.id);
    cerrarModalNuevoClienteVenta();
    mostrarToast('Cliente registrado correctamente.', 'exito');
  } catch (error) {
    errorNuevoClienteVenta.textContent = error.message || 'No se pudo registrar el cliente.';
    errorNuevoClienteVenta.hidden = false;
  } finally {
    botonGuardarClienteVenta.disabled = false;
  }
}

function catalogoProducto(id) {
  return productosVenta.find((producto) => String(producto.id) === String(id));
}

function etiquetaProductoVenta(producto) {
  const codigo = producto.codigo?.trim();
  return `${producto.nombre}${codigo ? ` · ${codigo}` : ` · ID ${producto.id}`}`;
}

function productosParaBusquedaVenta(fila, termino = '') {
  const normalizar = (valor) => String(valor || '').normalize('NFD')
    .replace(/\p{M}/gu, '').toLocaleLowerCase('es-PE');
  const consulta = normalizar(termino.trim());
  const usados = new Set([...cuerpoLineasVenta.querySelectorAll('.venta-linea')]
    .filter((linea) => linea !== fila)
    .map((linea) => linea.dataset.productoId)
    .filter(Boolean));
  return productosVenta.filter((producto) => Number(producto.stockActual) > 0
    && !usados.has(String(producto.id))
    && (!consulta || normalizar(`${producto.nombre} ${producto.codigo || ''}`).includes(consulta)));
}

function renderizarOpcionesProducto(fila, termino = '') {
  const lista = fila.querySelector('.venta-product-options');
  if (!lista) return;
  const productos = productosParaBusquedaVenta(fila, termino).slice(0, 20);
  lista.innerHTML = productos.map((producto) => `
    <button type="button" class="venta-product-option" role="option"
      aria-selected="false" data-producto-id="${escapar(producto.id)}">
      <span class="venta-product-option-name">${escapar(producto.nombre)}</span>
      <span class="venta-product-option-code">${escapar(producto.codigo || `ID ${producto.id}`)}</span>
      <span class="venta-product-option-price">${escapar(formatoMoneda(producto.precio))}</span>
      <span class="venta-product-option-stock">Stock: ${escapar(producto.stockActual)}</span>
    </button>`).join('');
  lista.hidden = productos.length === 0;
  lista.querySelectorAll('.venta-product-option').forEach((opcion) => {
    opcion.addEventListener('mousedown', (evento) => evento.preventDefault());
    opcion.addEventListener('click', () => {
      seleccionarProductoVenta(fila, catalogoProducto(opcion.dataset.productoId));
    });
  });
}

function actualizarListaProductosVenta(filaActiva = null) {
  cuerpoLineasVenta.querySelectorAll('.venta-linea').forEach((fila) => {
    const lista = fila.querySelector('.venta-product-options');
    if (!lista) return;
    const estabaVisible = !lista.hidden;
    const input = fila.querySelector('.venta-producto');
    renderizarOpcionesProducto(fila, input.value);
    if (fila !== filaActiva && !estabaVisible) lista.hidden = true;
    input.setAttribute('aria-expanded', String(!lista.hidden));
  });
}

function buscarProductoPorEtiqueta(etiqueta) {
  return productosVenta.find((producto) => etiquetaProductoVenta(producto) === etiqueta.trim());
}

function seleccionarProductoVenta(fila, producto) {
  if (!producto || Number(producto.stockActual) <= 0) return;
  const input = fila.querySelector('.venta-producto');
  input.value = etiquetaProductoVenta(producto);
  input.setAttribute('aria-expanded', 'false');
  fila.dataset.productoId = String(producto.id);
  const lista = fila.querySelector('.venta-product-options');
  if (lista) lista.hidden = true;
  actualizarLineaVenta(fila);
  actualizarListaProductosVenta();
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
  if (!productosVenta.some((producto) => Number(producto.stockActual) > 0)) {
    mostrarToast('No hay productos con stock disponible para registrar una venta.', 'error');
    return;
  }
  const usados = new Set([...cuerpoLineasVenta.querySelectorAll('.venta-producto')]
    .map((input) => input.dataset.productoId)
    .filter(Boolean));
  if (productosVenta.filter((producto) => Number(producto.stockActual) > 0)
    .every((producto) => usados.has(String(producto.id)))) {
    mostrarToast('Ya agregaste todos los productos disponibles.', 'info');
    return;
  }
  const fila = document.createElement('tr');
  fila.className = 'venta-linea';
  fila.innerHTML = `
    <td><div class="venta-producto-selector">
      <input class="form-control venta-producto venta-producto-busqueda" type="search"
        role="combobox" aria-autocomplete="list" aria-expanded="false"
        aria-label="Buscar producto por nombre o código"
        placeholder="Buscar por nombre o código" autocomplete="off" required />
      <div class="venta-product-options" role="listbox" hidden></div>
    </div></td>
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
    actualizarListaProductosVenta(fila);
  });
  inputProducto.addEventListener('focus', () => {
    inputProducto.setAttribute('aria-expanded', 'true');
    renderizarOpcionesProducto(fila, inputProducto.value);
  });
  inputProducto.addEventListener('keydown', (evento) => {
    const lista = fila.querySelector('.venta-product-options');
    const opciones = [...(lista?.querySelectorAll('.venta-product-option') || [])];
    if (evento.key === 'Escape') {
      if (lista) lista.hidden = true;
      inputProducto.setAttribute('aria-expanded', 'false');
    } else if (evento.key === 'ArrowDown' && opciones.length) {
      evento.preventDefault();
      opciones[0].focus();
    } else if (evento.key === 'Enter' && opciones.length) {
      evento.preventDefault();
      seleccionarProductoVenta(fila, catalogoProducto(opciones[0].dataset.productoId));
      inputProducto.focus();
    }
  });
  fila.querySelector('.venta-cantidad').addEventListener('input', () => actualizarLineaVenta(fila));
  actualizarListaProductosVenta(fila);
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
        <img class="nota-venta-bolsa" src="/img/logo-bolsa.png" alt="" />
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
    <table class="nota-venta-detalles">
      <thead><tr><th>Producto</th><th>CANTIDAD</th><th>PRECIO<br />UNITARIO</th><th>IMPORTE</th></tr></thead>
      <tbody>${filas}</tbody>
    </table>
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
document.getElementById('btn-nuevo-cliente-venta')?.addEventListener('click', abrirModalNuevoClienteVenta);
document.getElementById('btn-cerrar-nuevo-cliente-venta')?.addEventListener('click', cerrarModalNuevoClienteVenta);
document.getElementById('btn-cancelar-nuevo-cliente-venta')?.addEventListener('click', cerrarModalNuevoClienteVenta);
formNuevoClienteVenta?.addEventListener('submit', guardarNuevoClienteVenta);
modalNuevoClienteVenta?.addEventListener('click', (evento) => {
  if (evento.target === modalNuevoClienteVenta) cerrarModalNuevoClienteVenta();
});
document.querySelectorAll('[data-cerrar-venta]').forEach((boton) => {
  boton.addEventListener('click', cerrarModalesVenta);
});
document.querySelectorAll('.ventas-modal').forEach((modal) => {
  modal.addEventListener('click', (evento) => {
    if (evento.target === modal) ocultarModal(modal);
  });
});
document.addEventListener('keydown', (evento) => {
  if (evento.key !== 'Escape') return;
  if (modalNuevoClienteVenta && !modalNuevoClienteVenta.hidden) cerrarModalNuevoClienteVenta();
  else cerrarModalesVenta();
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
    productosVenta = productosVenta.filter((producto) => Number(producto.stockActual) > 0);
    if (selectClienteVenta) {
      renderizarSelectorClientesVenta();
    }
    if (botonNuevaVenta) {
      botonNuevaVenta.removeAttribute('aria-disabled');
      if (!productosVenta.length) {
        botonNuevaVenta.setAttribute('aria-disabled', 'true');
        mostrarToast('No hay productos con stock disponible para registrar una venta.', 'info');
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
