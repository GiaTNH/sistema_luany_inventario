iniciarLayout('Movimientos');

let movimientos = [];

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

function fechaMovimientoComoDia(fecha) {
  const date = fechaMovimientoComoDate(fecha);
  if (!date || Number.isNaN(date.getTime())) return '';
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
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
  const productoId = document.getElementById('filtro-movimiento-producto').value;
  const desde = document.getElementById('filtro-movimiento-desde').value;
  const hasta = document.getElementById('filtro-movimiento-hasta').value;

  if (desde && hasta && desde > hasta) {
    mensaje.textContent = 'La fecha inicial no puede ser posterior a la fecha final.';
    tbody.innerHTML = '<tr><td colspan="8">Ajusta el rango de fechas para consultar los movimientos.</td></tr>';
    return;
  }

  const filtrados = movimientos.filter(movimiento => {
    const coincideProducto = !productoId || String(movimiento.producto?.id) === productoId;
    const dia = fechaMovimientoComoDia(movimiento.fecha);
    return coincideProducto && (!desde || (dia && dia >= desde)) && (!hasta || (dia && dia <= hasta));
  });

  if (!filtrados.length) {
    mensaje.textContent = movimientos.length
      ? 'No hay movimientos que coincidan con los filtros seleccionados.'
      : 'Todavía no hay movimientos registrados.';
    tbody.innerHTML = '<tr><td colspan="8"><div class="estado-vacio"><p>No se encontraron movimientos.</p></div></td></tr>';
    return;
  }

  mensaje.textContent = `${filtrados.length} movimiento${filtrados.length === 1 ? '' : 's'}`;
  tbody.innerHTML = filtrados.map(movimiento => {
    const tipo = movimiento.tipo || '';
    return `<tr class="productos-card-row">
      <td><span class="badge ${claseTipoMovimiento(tipo)}">${escapar(etiquetaTipoMovimiento(tipo))}</span></td>
      <td>${escapar(movimiento.producto?.nombre || 'Producto no disponible')}</td>
      <td>${Number(movimiento.cantidad) || 0}</td>
      <td>${escapar(movimiento.stockAnterior ?? '—')}</td>
      <td>${escapar(movimiento.stockNuevo ?? '—')}</td>
      <td>${escapar(normalizarFechaMovimiento(movimiento.fecha))}</td>
      <td>${escapar(movimiento.usuarioNombre || 'No disponible')}</td>
      <td>${escapar(movimiento.observacion || '—')}</td>
    </tr>`;
  }).join('');
}

function poblarFiltroProductos() {
  const select = document.getElementById('filtro-movimiento-producto');
  const productos = new Map();
  movimientos.forEach(movimiento => {
    if (movimiento.producto?.id != null) {
      productos.set(String(movimiento.producto.id), movimiento.producto.nombre || `Producto ${movimiento.producto.id}`);
    }
  });

  select.innerHTML = '<option value="">Todos los productos</option>' +
    [...productos.entries()]
      .sort((a, b) => a[1].localeCompare(b[1], 'es'))
      .map(([id, nombre]) => `<option value="${escapar(id)}">${escapar(nombre)}</option>`)
      .join('');
}

async function cargarMovimientos() {
  const tbody = document.getElementById('tabla-movimientos-body');
  const mensaje = document.getElementById('movimientos-mensaje');
  mensaje.textContent = 'Cargando movimientos...';
  tbody.innerHTML = '<tr><td colspan="8">Cargando movimientos...</td></tr>';

  try {
    movimientos = await apiGet('/api/movimientos');
    poblarFiltroProductos();
    renderizarMovimientos();
  } catch (error) {
    movimientos = [];
    mensaje.textContent = 'No se pudo cargar el historial. Intenta actualizar nuevamente.';
    tbody.innerHTML = '<tr><td colspan="8">Ocurrió un error al consultar los movimientos.</td></tr>';
    mostrarToast(error.message, 'error');
  }
}

document.getElementById('filtro-movimiento-producto').addEventListener('change', renderizarMovimientos);
document.getElementById('filtro-movimiento-desde').addEventListener('change', renderizarMovimientos);
document.getElementById('filtro-movimiento-hasta').addEventListener('change', renderizarMovimientos);
document.getElementById('btn-recargar-movimientos').addEventListener('click', cargarMovimientos);

cargarMovimientos();
