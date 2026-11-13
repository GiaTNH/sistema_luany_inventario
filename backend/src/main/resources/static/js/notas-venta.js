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
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Nota de Venta Nº ${escapar(numeroVisible)}</title>
    <style>
      *{box-sizing:border-box}body{margin:28px auto;padding:24px;max-width:900px;color:#281C4A;font:14px Arial,sans-serif}
      header{display:flex;align-items:center;justify-content:space-between;gap:20px;padding-bottom:18px;border-bottom:2px solid #6D3BD1}
      .logo{width:76px;height:76px;object-fit:contain}.marca{text-align:right;color:#5630A8}.marca h1{margin:0;font-size:22px}.marca p{margin:5px 0 0;color:#756B91}
      h2{margin:24px 0 18px;color:#32216E;font-size:21px}.datos{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px 24px}
      .datos p{margin:4px 0}table{width:100%;margin-top:24px;border-collapse:collapse}th,td{padding:10px;border:1px solid #D8D1E5;text-align:left}
      th{background:#F3EFFA;color:#5630A8}.total{margin:18px 0;color:#32216E;font-size:19px;font-weight:bold;text-align:right}
      .legal{margin-top:28px;color:#8A2C50;font-size:11px;font-weight:bold;text-align:center}.gracias{margin-top:22px;color:#5630A8;font-size:17px;font-weight:bold;text-align:center}
      .eslogan{color:#756B91;text-align:center}.imprimir{display:block;margin:22px auto 0;padding:10px 18px;border:0;border-radius:8px;background:#684BB5;color:white;font-weight:bold;cursor:pointer}
      @media print{body{margin:0;padding:0}.imprimir{display:none}header,tr{break-inside:avoid}th{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
      @media(max-width:560px){body{padding:14px}.datos{grid-template-columns:1fr}header{gap:10px}.logo{width:58px;height:58px}}
    </style></head><body>
    <header><img class="logo" src="/img/logo-bolsa.png" alt="Logo LUANY"><div class="marca"><h1>Luany - tienda para bebés</h1><p>Juega · Viste · Crece</p></div></header>
    <h2>Nota de Venta Nº ${escapar(numeroVisible)}</h2>
    <section class="datos">
      <p><strong>Fecha y hora de emisión:</strong> ${escapar(normalizarFecha(nota.fechaEmision))}</p>
      <p><strong>Cliente:</strong> ${escapar(nota.clienteNombre || 'Cliente general')}</p>
      <p><strong>Documento:</strong> ${escapar(documentoClienteVisible(nota.clienteDocumento))}</p>
      <p><strong>Registrado por:</strong> ${escapar(nota.usuarioNombre || '—')}</p>
    </section>
    <table><thead><tr><th>Producto</th><th>Cantidad</th><th>Precio unitario</th><th>Importe</th></tr></thead><tbody>${filas}</tbody></table>
    <p class="total">Total de la venta: ${escapar(formatoMoneda(nota.total))}</p>
    <p class="legal">DOCUMENTO NO VÁLIDO COMO COMPROBANTE DE PAGO</p>
    <p class="gracias">¡Gracias por tu compra!</p><p class="eslogan">Acompañamos cada etapa de tu bebé</p>
    <button class="imprimir" type="button" onclick="window.print()">Imprimir / Guardar PDF</button>
    <script>window.onload=()=>window.print();</script></body></html>`;
}

function abrirNotaImprimible(nota, ventana) {
  const destino = ventana || window;
  if (ventana) ventana.opener = null;
  destino.document.open();
  destino.document.write(generarNotaImprimible(nota));
  destino.document.close();
}

function renderizarDetalleVenta(nota) {
  notaActualDetalle = nota;
  document.getElementById('modal-detalle-venta-titulo').textContent = `Nota ${nota.numeroNota}`;
  document.getElementById('venta-detalle-resumen').textContent =
    `Fecha: ${normalizarFecha(nota.fechaEmision)} · Cliente: ${nota.clienteNombre} (${documentoClienteVisible(nota.clienteDocumento)}) · Registrado por: ${nota.usuarioNombre}`;
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
    const ventanaImpresion = window.open('', '_blank');
    try {
      let nota;
      try {
        nota = await apiPost('/api/notas-venta', {
          clienteId: selectClienteVenta.value ? Number(selectClienteVenta.value) : null,
          productos,
        });
      } catch (exception) {
        ventanaImpresion?.close();
        error.textContent = exception.message;
        error.hidden = false;
        return;
      }

      mostrarToast(`Venta registrada correctamente: ${nota.numeroNota}.`, 'exito');
      try {
        abrirNotaImprimible(nota, ventanaImpresion);
      } catch (exception) {
        ventanaImpresion?.close();
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
  const ventana = window.open('', '_blank');
  if (!ventana) {
    mostrarToast('El navegador bloqueó la ventana de impresión. Permite ventanas emergentes para continuar.', 'error');
    return;
  }
  abrirNotaImprimible(notaActualDetalle, ventana);
});
