iniciarLayout('Alertas');

const tablaAlertas = document.getElementById('alertas-tabla');
const totalAlertas = document.getElementById('alertas-total');
const botonNotificarStock = document.getElementById('btn-notificar-stock');
const modalNotificacion = document.getElementById('modal-notificar-stock');
const formNotificacion = document.getElementById('form-notificar-stock');
const listaDestinatarios = document.getElementById('notificacion-destinatarios');
const seleccionarTodosDestinatarios = document.getElementById('notificacion-seleccionar-todos');
const errorNotificacion = document.getElementById('notificacion-error');
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
    if (botonNotificarStock) botonNotificarStock.disabled = true;
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
    if (botonNotificarStock) botonNotificarStock.disabled = alertas.length === 0;
  } catch (error) {
    totalAlertas.textContent = 'No disponible';
    totalAlertas.classList.remove('is-empty');
    totalAlertas.classList.add('is-error');
    mostrarMensajeAlertas(`No se pudieron cargar las alertas: ${error.message}`, 'error');
    mostrarToast(`No se pudieron cargar las alertas: ${error.message}`, 'error');
  }
}


botonNotificarStock?.addEventListener('click', async () => {
  if (botonNotificarStock.disabled) return;
  botonNotificarStock.disabled = true;
  try {
    const destinatarios = await apiGet('/api/alertas/destinatarios');
    if (!Array.isArray(destinatarios) || destinatarios.length === 0) {
      throw new Error('No hay administradores activos con correo disponible.');
    }
    listaDestinatarios.replaceChildren();
    destinatarios.forEach((destinatario) => {
      const etiqueta = document.createElement('label');
      etiqueta.className = 'alertas-destinatario';
      const casilla = document.createElement('input');
      casilla.type = 'checkbox';
      casilla.name = 'destinatario';
      casilla.value = String(destinatario.id);
      const texto = document.createElement('span');
      texto.textContent = `${destinatario.nombre} · ${destinatario.correo}`;
      etiqueta.append(casilla, texto);
      listaDestinatarios.appendChild(etiqueta);
    });
    actualizarSeleccionTodos();
    errorNotificacion.hidden = true;
    errorNotificacion.textContent = '';
    abrirModalAccesible(modalNotificacion);
  } catch (error) {
    mostrarToast(`No se pudo preparar la notificación: ${error.message}`, 'error');
  } finally {
    botonNotificarStock.disabled = false;
  }
});

function actualizarSeleccionTodos() {
  const casillas = [...listaDestinatarios.querySelectorAll('input[name="destinatario"]')];
  const seleccionadas = casillas.filter((casilla) => casilla.checked).length;
  seleccionarTodosDestinatarios.checked = casillas.length > 0 && seleccionadas === casillas.length;
  seleccionarTodosDestinatarios.indeterminate = seleccionadas > 0 && seleccionadas < casillas.length;
}

function seleccionarDestinatarios(seleccionar) {
  listaDestinatarios.querySelectorAll('input[name="destinatario"]')
    .forEach((casilla) => { casilla.checked = seleccionar; });
  actualizarSeleccionTodos();
}

seleccionarTodosDestinatarios?.addEventListener('change', () => {
  seleccionarDestinatarios(seleccionarTodosDestinatarios.checked);
});
listaDestinatarios?.addEventListener('change', actualizarSeleccionTodos);

formNotificacion?.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const destinatarioIds = [...listaDestinatarios.querySelectorAll('input[name="destinatario"]:checked')]
    .map((casilla) => Number(casilla.value));
  if (!destinatarioIds.length) {
    errorNotificacion.textContent = 'Selecciona al menos un destinatario.';
    errorNotificacion.hidden = false;
    return;
  }
  const boton = document.getElementById('btn-confirmar-notificacion');
  boton.disabled = true;
  try {
    const respuesta = await apiPost('/api/alertas/notificar-stock', { destinatarioIds });
    cerrarModalAccesible(modalNotificacion);
    mostrarToast(`Notificación enviada con ${respuesta.cantidadProductos} productos.`, 'exito');
  } catch (error) {
    errorNotificacion.textContent = `No se pudo enviar la notificación: ${error.message}`;
    errorNotificacion.hidden = false;
  } finally {
    boton.disabled = false;
  }
});

document.querySelectorAll('[data-cerrar-notificacion]').forEach((boton) => {
  boton.addEventListener('click', () => cerrarModalAccesible(modalNotificacion));
});

cargarAlertas();
