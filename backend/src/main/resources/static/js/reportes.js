iniciarLayout('Reportes');

const desdeInput = document.getElementById('reporte-desde');
const hastaInput = document.getElementById('reporte-hasta');
const panelesReporte = {
  ventas: document.getElementById('reporte-ventas'),
  productos: document.getElementById('reporte-productos'),
  movimientos: document.getElementById('reporte-movimientos'),
  inventario: document.getElementById('reporte-inventario'),
  pedidos: document.getElementById('reporte-pedidos'),
};
let reporteActual = 'ventas';
let paginaMovimientos = 0;
let totalPaginasMovimientos = 0;

function fechaLocal(fecha) {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

function inicializarFechas() {
  const hoy = new Date();
  const inicio = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() - 29);
  desdeInput.value = fechaLocal(inicio);
  hastaInput.value = fechaLocal(hoy);
}

function rangoQuery() {
  const params = new URLSearchParams({ desde: desdeInput.value, hasta: hastaInput.value });
  return params.toString();
}

function fechaHora(fecha) {
  if (!fecha) return '—';
  return new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(fecha));
}

function moneda(valor) {
  return new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(Number(valor) || 0);
}

function celda(valor) {
  const elemento = document.createElement('td');
  elemento.textContent = valor === null || valor === undefined || valor === '' ? '—' : valor;
  return elemento;
}

function estadoVacio(tbody, columnas, mensaje) {
  tbody.replaceChildren();
  const fila = document.createElement('tr');
  const celdaMensaje = celda(mensaje);
  celdaMensaje.colSpan = columnas;
  fila.appendChild(celdaMensaje);
  tbody.appendChild(fila);
}

function actualizarKpis(contenedor, elementos) {
  contenedor.replaceChildren();
  elementos.forEach(({ etiqueta, valor }) => {
    const tarjeta = document.createElement('div');
    tarjeta.className = 'reportes-kpi';
    const titulo = document.createElement('span');
    titulo.textContent = etiqueta;
    const dato = document.createElement('strong');
    dato.textContent = valor;
    tarjeta.append(titulo, dato);
    contenedor.appendChild(tarjeta);
  });
}

function dibujarBarras(svg, datos, nombre, valor, formato) {
  const ns = 'http://www.w3.org/2000/svg';
  svg.replaceChildren();
  svg.setAttribute('viewBox', `0 0 720 ${Math.max(120, datos.length * 38 + 28)}`);
  svg.setAttribute('aria-label', nombre);
  if (!datos.length) {
    svg.setAttribute('height', '120');
    const mensaje = document.createElementNS(ns, 'text');
    mensaje.setAttribute('x', '16');
    mensaje.setAttribute('y', '62');
    mensaje.setAttribute('class', 'reportes-svg-empty');
    mensaje.textContent = 'Sin datos para el período seleccionado';
    svg.appendChild(mensaje);
    return;
  }
  svg.setAttribute('height', String(Math.max(120, datos.length * 38 + 28)));
  const maximo = Math.max(...datos.map(valor), 1);
  datos.forEach((dato, indice) => {
    const y = 18 + indice * 38;
    const texto = document.createElementNS(ns, 'text');
    texto.setAttribute('x', '0');
    texto.setAttribute('y', String(y + 15));
    texto.setAttribute('class', 'reportes-svg-label');
    texto.textContent = nombre(dato);

    const fondo = document.createElementNS(ns, 'rect');
    fondo.setAttribute('x', '230');
    fondo.setAttribute('y', String(y));
    fondo.setAttribute('width', '390');
    fondo.setAttribute('height', '22');
    fondo.setAttribute('rx', '7');
    fondo.setAttribute('class', 'reportes-svg-track');

    const barra = document.createElementNS(ns, 'rect');
    barra.setAttribute('x', '230');
    barra.setAttribute('y', String(y));
    barra.setAttribute('width', String(Math.max((valor(dato) / maximo) * 390, 2)));
    barra.setAttribute('height', '22');
    barra.setAttribute('rx', '7');
    barra.setAttribute('class', 'reportes-svg-bar');

    const total = document.createElementNS(ns, 'text');
    total.setAttribute('x', '632');
    total.setAttribute('y', String(y + 15));
    total.setAttribute('class', 'reportes-svg-value');
    total.textContent = formato(valor(dato));
    svg.append(texto, fondo, barra, total);
  });
}

async function cargarVentas() {
  const datos = await apiGet(`/api/reportes/ventas?${rangoQuery()}`);
  actualizarKpis(document.getElementById('ventas-resumen'), [
    { etiqueta: 'Importe total', valor: moneda(datos.importeTotal) },
    { etiqueta: 'Notas de venta', valor: Number(datos.cantidadNotas || 0).toLocaleString('es-PE') },
  ]);
  dibujarBarras(
    document.getElementById('ventas-grafico'),
    datos.ventasPorDia || [],
    (dia) => dia.fecha,
    (dia) => Number(dia.importe) || 0,
    moneda,
  );
  const tbody = document.getElementById('ventas-tabla');
  if (!datos.ventasPorDia?.length) return estadoVacio(tbody, 3, 'No hay ventas en el período seleccionado.');
  tbody.replaceChildren(...datos.ventasPorDia.map((dia) => {
    const fila = document.createElement('tr');
    fila.append(celda(dia.fecha), celda(dia.cantidadNotas), celda(moneda(dia.importe)));
    return fila;
  }));
}

async function cargarProductosVendidos() {
  const limite = document.getElementById('productos-limite').value;
  const datos = await apiGet(`/api/reportes/productos-mas-vendidos?${rangoQuery()}&limite=${encodeURIComponent(limite)}`);
  dibujarBarras(
    document.getElementById('productos-grafico'),
    datos.productos || [],
    (producto) => producto.nombre,
    (producto) => Number(producto.unidadesVendidas) || 0,
    (total) => `${total.toLocaleString('es-PE')} uds.`,
  );
  const tbody = document.getElementById('productos-tabla');
  if (!datos.productos?.length) return estadoVacio(tbody, 3, 'No hay productos vendidos en el período seleccionado.');
  tbody.replaceChildren(...datos.productos.map((producto, indice) => {
    const fila = document.createElement('tr');
    fila.append(celda(indice + 1), celda(producto.nombre), celda(Number(producto.unidadesVendidas).toLocaleString('es-PE')));
    return fila;
  }));
}

async function cargarProductosFiltro() {
  const selector = document.getElementById('movimiento-producto');
  try {
    const productos = await apiGet('/api/productos');
    selector.replaceChildren(new Option('Todos los productos', ''));
    productos.sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')).forEach((producto) => {
      selector.add(new Option(producto.nombre, String(producto.id)));
    });
  } catch (error) {
    mostrarToast(`No se pudo cargar el filtro de productos: ${error.message}`, 'error');
  }
}

async function cargarMovimientos() {
  const params = new URLSearchParams(rangoQuery());
  const productoId = document.getElementById('movimiento-producto').value;
  const tipo = document.getElementById('movimiento-tipo').value;
  params.set('pagina', String(paginaMovimientos));
  params.set('tamano', '20');
  if (productoId) params.set('productoId', productoId);
  if (tipo) params.set('tipo', tipo);
  const datos = await apiGet(`/api/reportes/movimientos?${params}`);
  totalPaginasMovimientos = datos.totalPaginas;
  document.getElementById('movimientos-pagina-info').textContent =
    `${datos.totalElementos.toLocaleString('es-PE')} movimientos · Página ${datos.totalPaginas ? datos.pagina + 1 : 0} de ${datos.totalPaginas}`;
  document.getElementById('movimientos-anterior').disabled = datos.pagina === 0;
  document.getElementById('movimientos-siguiente').disabled =
    datos.totalPaginas === 0 || datos.pagina + 1 >= datos.totalPaginas;
  const tbody = document.getElementById('movimientos-tabla');
  if (!datos.movimientos?.length) return estadoVacio(tbody, 7, 'No hay movimientos con esos filtros.');
  tbody.replaceChildren(...datos.movimientos.map((movimiento) => {
    const fila = document.createElement('tr');
    fila.append(
      celda(fechaHora(movimiento.fecha)),
      celda(movimiento.productoNombre),
      celda(movimiento.tipo),
      celda(movimiento.cantidad),
      celda(movimiento.stockAnterior),
      celda(movimiento.stockNuevo),
      celda(movimiento.observacion),
    );
    return fila;
  }));
}

async function cargarInventario() {
  const estado = document.getElementById('inventario-estado').value;
  const ruta = estado ? `/api/reportes/inventario?estado=${encodeURIComponent(estado)}` : '/api/reportes/inventario';
  const datos = await apiGet(ruta);
  const tbody = document.getElementById('inventario-tabla');
  if (!datos.productos?.length) return estadoVacio(tbody, 5, 'No hay productos para el filtro seleccionado.');
  tbody.replaceChildren(...datos.productos.map((producto) => {
    const fila = document.createElement('tr');
    fila.append(
      celda(producto.nombre),
      celda(producto.stockActual),
      celda(producto.stockMinimo),
      celda(producto.stockMaximo),
      celda(producto.estado),
    );
    fila.lastElementChild.className = `reportes-estado reportes-estado-${producto.estado.toLowerCase()}`;
    return fila;
  }));
}

async function cargarPedidos() {
  const datos = await apiGet(`/api/reportes/pedidos-recepciones?${rangoQuery()}`);
  actualizarKpis(document.getElementById('pedidos-resumen'), (datos.resumenPorEstado || []).map((estado) => ({
    etiqueta: estado.estado,
    valor: Number(estado.cantidad).toLocaleString('es-PE'),
  })));
  dibujarBarras(
    document.getElementById('recepciones-grafico'),
    datos.recepcionesPorDia || [],
    (dia) => dia.fecha,
    (dia) => Number(dia.unidadesRecibidas) || 0,
    (total) => `${total.toLocaleString('es-PE')} uds.`,
  );
  const tbody = document.getElementById('pedidos-tabla');
  if (!datos.pedidos?.length) return estadoVacio(tbody, 8, 'No hay pedidos en el período seleccionado.');
  tbody.replaceChildren(...datos.pedidos.map((pedido) => {
    const fila = document.createElement('tr');
    fila.append(
      celda(pedido.pedidoId),
      celda(fechaHora(pedido.fechaPedido)),
      celda(pedido.proveedor),
      celda(pedido.estado),
      celda(pedido.unidadesPedidas),
      celda(pedido.unidadesRecibidas),
      celda(pedido.unidadesPendientes),
      celda(fechaHora(pedido.ultimaRecepcion)),
    );
    return fila;
  }));
}

const cargadoresReporte = {
  ventas: cargarVentas,
  productos: cargarProductosVendidos,
  movimientos: cargarMovimientos,
  inventario: cargarInventario,
  pedidos: cargarPedidos,
};

async function cargarReporteActual() {
  if (!desdeInput.value || !hastaInput.value || desdeInput.value > hastaInput.value) {
    mostrarToast('Selecciona un rango de fechas válido.', 'error');
    return;
  }
  try {
    await cargadoresReporte[reporteActual]();
  } catch (error) {
    mostrarToast(`No se pudo cargar el reporte: ${error.message}`, 'error');
  }
}

document.querySelectorAll('.reportes-tab').forEach((boton) => {
  boton.addEventListener('click', () => {
    reporteActual = boton.dataset.reporte;
    document.querySelectorAll('.reportes-tab').forEach((elemento) => {
      const activo = elemento === boton;
      elemento.classList.toggle('activo', activo);
      elemento.setAttribute('aria-selected', String(activo));
    });
    Object.entries(panelesReporte).forEach(([nombre, panel]) => {
      panel.hidden = nombre !== reporteActual;
    });
    cargarReporteActual();
  });
});

document.getElementById('btn-actualizar-reportes').addEventListener('click', () => {
  paginaMovimientos = 0;
  cargarReporteActual();
});
document.getElementById('productos-limite').addEventListener('change', cargarReporteActual);
document.getElementById('movimiento-producto').addEventListener('change', () => {
  paginaMovimientos = 0;
  cargarReporteActual();
});
document.getElementById('movimiento-tipo').addEventListener('change', () => {
  paginaMovimientos = 0;
  cargarReporteActual();
});
document.getElementById('inventario-estado').addEventListener('change', cargarReporteActual);
document.getElementById('movimientos-anterior').addEventListener('click', () => {
  if (paginaMovimientos > 0) {
    paginaMovimientos -= 1;
    cargarReporteActual();
  }
});
document.getElementById('movimientos-siguiente').addEventListener('click', () => {
  if (paginaMovimientos + 1 < totalPaginasMovimientos) {
    paginaMovimientos += 1;
    cargarReporteActual();
  }
});

inicializarFechas();
cargarProductosFiltro();
cargarReporteActual();
