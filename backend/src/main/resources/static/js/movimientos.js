iniciarLayout('Movimientos');

let movimientos = [];
let paginaMovimientos = 0;
let totalPaginasMovimientos = 0;
let totalElementosMovimientos = 0;
let solicitudMovimientos = 0;
const tamanoPaginaMovimientos = 20;

function fechaMovimientoComoDate(fecha) {
  if (Array.isArray(fecha)) {
    const [anio, mes, dia, hora = 0, minuto = 0, segundo = 0, nanosegundo = 0] = fecha;
    const date = new Date(anio, mes - 1, dia, hora, minuto, segundo, Math.floor(nanosegundo / 1000000));
    return Number.isNaN(date.getTime()) ? null : date;
  }
  if (!fecha) return null;

  if (typeof fecha === 'string') {
    const fechaLocal = fecha.match(/^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d+))?)?)?$/);
    if (fechaLocal) {
      const [, anio, mes, dia, hora = '0', minuto = '0', segundo = '0', fraccion = '0'] = fechaLocal;
      const date = new Date(
        Number(anio),
        Number(mes) - 1,
        Number(dia),
        Number(hora),
        Number(minuto),
        Number(segundo),
        Number(fraccion.padEnd(3, '0').slice(0, 3)),
      );
      return Number.isNaN(date.getTime()) ? null : date;
    }
  }

  const date = new Date(fecha);
  return Number.isNaN(date.getTime()) ? null : date;
}

function normalizarFechaMovimiento(fecha) {
  const date = fechaMovimientoComoDate(fecha);
  return date ? date.toLocaleString('es-PE') : '—';
}

function etiquetaTipoMovimiento(tipo) {
  const etiquetas = {
    ENTRADA: 'Entrada',
    SALIDA: 'Salida',
    VENTA: 'Venta',
  };
  return etiquetas[tipo] || tipo || '—';
}

function claseTipoMovimiento(tipo) {
  const clases = {
    ENTRADA: 'badge-normal',
    SALIDA: 'badge-exceso',
    VENTA: 'badge-bajo',
  };
  return clases[tipo] || 'badge-normal';
}

function renderizarMovimientos() {
  const tbody = document.getElementById('tabla-movimientos-body');
  const mensaje = document.getElementById('movimientos-mensaje');
  if (!movimientos.length) {
    mensaje.textContent = 'No hay movimientos que coincidan con los filtros seleccionados.';
    tbody.innerHTML = '<tr><td colspan="8"><div class="estado-vacio"><p>No se encontraron movimientos.</p></div></td></tr>';
    return;
  }

  const inicio = paginaMovimientos * tamanoPaginaMovimientos + 1;
  const fin = Math.min(inicio + movimientos.length - 1, totalElementosMovimientos);
  mensaje.textContent = `Mostrando ${inicio}–${fin} de ${totalElementosMovimientos} movimiento${totalElementosMovimientos === 1 ? '' : 's'}`;
  tbody.innerHTML = movimientos.map(movimiento => {
    const tipo = movimiento.tipo || '';
    return `<tr class="productos-card-row">
      <td data-label="Tipo"><span class="badge ${claseTipoMovimiento(tipo)}">${escapar(etiquetaTipoMovimiento(tipo))}</span></td>
      <td data-label="Producto">${escapar(movimiento.producto?.nombre || 'Producto no disponible')}</td>
      <td data-label="Cantidad">${Number(movimiento.cantidad) || 0}</td>
      <td data-label="Stock anterior">${escapar(movimiento.stockAnterior ?? '—')}</td>
      <td data-label="Stock nuevo">${escapar(movimiento.stockNuevo ?? '—')}</td>
      <td data-label="Fecha">${escapar(normalizarFechaMovimiento(movimiento.fecha))}</td>
      <td data-label="Usuario responsable">${escapar(movimiento.usuarioNombre || 'No disponible')}</td>
      <td data-label="Observación">${escapar(movimiento.observacion || '—')}</td>
    </tr>`;
  }).join('');
}

function actualizarPaginacionMovimientos(pagina = null) {
  const anterior = document.getElementById('btn-movimientos-anterior');
  const siguiente = document.getElementById('btn-movimientos-siguiente');
  const info = document.getElementById('movimientos-pagina-info');
  const totalPaginas = pagina?.totalPages ?? totalPaginasMovimientos;
  const numero = pagina?.number ?? paginaMovimientos;
  info.textContent = `Página ${totalPaginas ? numero + 1 : 1} de ${totalPaginas || 1}`;
  anterior.disabled = numero <= 0;
  siguiente.disabled = totalPaginas === 0 || numero >= totalPaginas - 1;
}

async function poblarFiltroProductos() {
  const select = document.getElementById('filtro-movimiento-producto');
  const productoSeleccionado = select.value;
  const productos = await apiGet('/api/productos');
  select.innerHTML = '<option value="">Todos los productos</option>' +
    productos
      .slice()
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
      .map(producto => `<option value="${escapar(producto.id)}">${escapar(producto.nombre)}</option>`)
      .join('');
  select.value = productoSeleccionado;
}

async function cargarMovimientos() {
  const tbody = document.getElementById('tabla-movimientos-body');
  const mensaje = document.getElementById('movimientos-mensaje');
  const numeroSolicitud = ++solicitudMovimientos;
  const desde = document.getElementById('filtro-movimiento-desde').value;
  const hasta = document.getElementById('filtro-movimiento-hasta').value;
  if (desde && hasta && desde > hasta) {
    movimientos = [];
    paginaMovimientos = 0;
    totalPaginasMovimientos = 0;
    totalElementosMovimientos = 0;
    mensaje.textContent = 'La fecha inicial no puede ser posterior a la fecha final.';
    tbody.innerHTML = '<tr><td colspan="8">Ajusta el rango de fechas para consultar los movimientos.</td></tr>';
    actualizarPaginacionMovimientos();
    return;
  }

  mensaje.textContent = 'Cargando movimientos...';
  tbody.innerHTML = '<tr><td colspan="8">Cargando movimientos...</td></tr>';
  const parametros = new URLSearchParams({
    pagina: String(paginaMovimientos),
    tamano: String(tamanoPaginaMovimientos),
  });
  const productoId = document.getElementById('filtro-movimiento-producto').value;
  const tipo = document.getElementById('filtro-movimiento-tipo').value;
  if (productoId) parametros.set('productoId', productoId);
  if (tipo) parametros.set('tipo', tipo);
  if (desde) parametros.set('desde', desde);
  if (hasta) parametros.set('hasta', hasta);

  try {
    const pagina = await apiGet(`/api/movimientos/pagina?${parametros.toString()}`);
    if (numeroSolicitud !== solicitudMovimientos) return;
    movimientos = pagina.content || [];
    paginaMovimientos = pagina.number || 0;
    totalPaginasMovimientos = pagina.totalPages || 0;
    totalElementosMovimientos = pagina.totalElements || 0;
    renderizarMovimientos();
    actualizarPaginacionMovimientos(pagina);
  } catch (error) {
    if (numeroSolicitud !== solicitudMovimientos) return;
    movimientos = [];
    totalElementosMovimientos = 0;
    totalPaginasMovimientos = 0;
    mensaje.textContent = 'No se pudo cargar el historial. Intenta actualizar nuevamente.';
    tbody.innerHTML = '<tr><td colspan="8">Ocurrió un error al consultar los movimientos.</td></tr>';
    actualizarPaginacionMovimientos();
    mostrarToast(error.message, 'error');
  }
}

function cambiarPaginaMovimientos(delta) {
  const siguiente = paginaMovimientos + delta;
  if (siguiente < 0 || (totalPaginasMovimientos && siguiente >= totalPaginasMovimientos)) return;
  paginaMovimientos = siguiente;
  cargarMovimientos();
}

function reiniciarPaginaMovimientos() {
  paginaMovimientos = 0;
  cargarMovimientos();
}

document.getElementById('filtro-movimiento-producto').addEventListener('change', reiniciarPaginaMovimientos);
document.getElementById('filtro-movimiento-tipo').addEventListener('change', reiniciarPaginaMovimientos);
document.getElementById('filtro-movimiento-desde').addEventListener('change', reiniciarPaginaMovimientos);
document.getElementById('filtro-movimiento-hasta').addEventListener('change', reiniciarPaginaMovimientos);
document.getElementById('btn-recargar-movimientos').addEventListener('click', cargarMovimientos);
document.getElementById('btn-movimientos-anterior').addEventListener('click', () => cambiarPaginaMovimientos(-1));
document.getElementById('btn-movimientos-siguiente').addEventListener('click', () => cambiarPaginaMovimientos(1));

async function iniciarHistorialMovimientos() {
  try {
    await poblarFiltroProductos();
  } catch (error) {
    mostrarToast(`No se pudo cargar la lista de productos: ${error.message}`, 'error');
  }
  await cargarMovimientos();
}

iniciarHistorialMovimientos();
