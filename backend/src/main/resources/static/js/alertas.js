iniciarLayout('Alertas');

const tablaAlertas = document.getElementById('alertas-tabla');
const totalAlertas = document.getElementById('alertas-total');
const columnasAlerta = 6;

function mostrarMensajeAlertas(texto, estado = '') {
  tablaAlertas.replaceChildren();
  const fila = document.createElement('tr');
  const celda = document.createElement('td');
  celda.colSpan = columnasAlerta;
  celda.className = `alertas-message${estado ? ` is-${estado}` : ''}`;
  celda.textContent = texto;
  fila.appendChild(celda);
  tablaAlertas.appendChild(fila);
}

function crearCeldaAlerta(texto, clase = '') {
  const celda = document.createElement('td');
  celda.textContent = texto;
  if (clase) celda.className = clase;
  return celda;
}

function validarValorStock(valor) {
  if (typeof valor !== 'number' && typeof valor !== 'string') return null;
  if (typeof valor === 'string' && valor.trim() === '') return null;
  const numero = Number(valor);
  return Number.isFinite(numero) ? numero : null;
}

function renderizarAlertas(alertas) {
  tablaAlertas.replaceChildren();
  totalAlertas.classList.remove('is-error');

  if (alertas.length === 0) {
    totalAlertas.textContent = 'Sin alertas';
    totalAlertas.classList.add('is-empty');
    const fila = document.createElement('tr');
    const celda = document.createElement('td');
    celda.colSpan = columnasAlerta;
    celda.className = 'alertas-message';
    celda.textContent = 'No hay productos con stock bajo.';
    fila.appendChild(celda);
    tablaAlertas.appendChild(fila);
    return;
  }

  totalAlertas.classList.remove('is-empty');
  totalAlertas.textContent = `${alertas.length} ${alertas.length === 1 ? 'producto' : 'productos'}`;
  alertas.forEach((alerta) => {
    const stockActual = validarValorStock(alerta.stockActual);
    const stockMinimo = validarValorStock(alerta.stockMinimo);
    const datosStockValidos = stockActual !== null && stockMinimo !== null;
    const estado = !datosStockValidos || stockActual < 0
      ? 'DATO INVÁLIDO'
      : stockActual === 0
        ? 'AGOTADO'
        : stockActual <= stockMinimo
          ? 'BAJO'
          : 'DATO INVÁLIDO';
    const fila = document.createElement('tr');
    const nombre = crearCeldaAlerta(alerta.nombre || 'Producto sin nombre', 'alertas-product-name');
    const categoria = crearCeldaAlerta(alerta.categoria || '—', 'alertas-category');
    const stock = crearCeldaAlerta(stockActual === null ? '—' : String(stockActual), 'alertas-stock-current');
    const minimo = crearCeldaAlerta(stockMinimo === null ? '—' : String(stockMinimo), 'alertas-stock-minimum');
    const estadoCelda = document.createElement('td');
    const insignia = document.createElement('span');
    insignia.className = `alertas-status${datosStockValidos ? ` is-${estado.toLowerCase()}` : ' is-invalid'}`;
    insignia.textContent = estado;
    estadoCelda.appendChild(insignia);
    const cantidadSugerida = validarValorStock(alerta.cantidadSugerida);
    const sugerida = crearCeldaAlerta(
      cantidadSugerida === null ? '—' : `${cantidadSugerida} uds.`,
      'alertas-replenishment',
    );
    fila.append(nombre, categoria, stock, minimo, estadoCelda, sugerida);
    tablaAlertas.appendChild(fila);
  });
}

async function cargarAlertas() {
  try {
    const alertas = await apiGet('/api/alertas/stock');
    if (!Array.isArray(alertas)) {
      throw new Error('La respuesta del servidor no tiene el formato esperado.');
    }
    renderizarAlertas(alertas);
  } catch (error) {
    totalAlertas.textContent = 'No disponible';
    totalAlertas.classList.remove('is-empty');
    totalAlertas.classList.add('is-error');
    mostrarMensajeAlertas(`No se pudieron cargar las alertas: ${error.message}`, 'error');
    mostrarToast(`No se pudieron cargar las alertas: ${error.message}`, 'error');
  }
}

cargarAlertas();
