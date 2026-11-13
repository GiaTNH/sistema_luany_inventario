iniciarLayout('Alertas');

const tablaAlertas = document.getElementById('alertas-tabla');
const totalAlertas = document.getElementById('alertas-total');
const botonReponerAlertas = document.getElementById('btn-reponer-alertas');
const columnasAlerta = 7;
const usuarioAlertas = obtenerSesion();
const esAdministradorAlertas = usuarioAlertas?.rol === 'ADMIN';
const modalReposicion = document.getElementById('modal-reponer-producto');
const formReposicion = document.getElementById('form-reponer-producto');
const botonConfirmarReposicion = document.getElementById('btn-confirmar-reposicion');
const errorReposicion = document.getElementById('reposicion-error');
let productosCatalogoReposicion = [];
let proveedoresCatalogoReposicion = [];
let productoReposicionActual = null;
let guardandoReposicion = false;

if (botonReponerAlertas && esAdministradorAlertas) {
  botonReponerAlertas.hidden = false;
}

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

function productosParaReposicion(alertas) {
  return alertas
    .filter((alerta) => Number.isSafeInteger(Number(alerta.productoId))
      && Number(alerta.productoId) > 0
      && Number.isSafeInteger(Number(alerta.cantidadSugerida))
      && Number(alerta.cantidadSugerida) > 0)
    .map((alerta) => ({
      productoId: Number(alerta.productoId),
      cantidad: Number(alerta.cantidadSugerida),
    }));
}

function datosPedidoReposicion(producto, cantidad, precio, fechaEstimadaEntrega) {
  const cantidadNumerica = Number(cantidad);
  const precioNumerico = Number(precio);
  const productoId = Number(producto?.id);
  const proveedorId = Number(producto?.proveedor?.id);
  if (!producto || !Number.isSafeInteger(cantidadNumerica) || cantidadNumerica < 1
      || !Number.isFinite(precioNumerico) || precioNumerico < 0
      || !Number.isSafeInteger(productoId) || productoId < 1
      || !Number.isSafeInteger(proveedorId) || proveedorId < 1) {
    throw new Error('Verifica el producto, proveedor, cantidad y precio del pedido.');
  }
  return {
    proveedorId,
    fechaEstimadaEntrega: fechaEstimadaEntrega || null,
    productos: [{
      productoId,
      cantidad: cantidadNumerica,
      precio: precioNumerico,
    }],
  };
}

async function crearPedidoDesdeAlerta(datos) {
  if (guardandoReposicion) return false;
  guardandoReposicion = true;
  botonConfirmarReposicion.disabled = true;
  try {
    await apiPost('/api/pedidos', datos);
    return true;
  } finally {
    guardandoReposicion = false;
    botonConfirmarReposicion.disabled = false;
  }
}

async function abrirModalReposicion(productoId, cantidadSugerida) {
  if (!esAdministradorAlertas) return;
  const cantidad = Number(cantidadSugerida);
  if (!Number.isSafeInteger(cantidad) || cantidad < 1) {
    mostrarToast('Este producto no tiene una cantidad de reposición válida.', 'error');
    return;
  }
  try {
    if (!productosCatalogoReposicion.length || !proveedoresCatalogoReposicion.length) {
      [productosCatalogoReposicion, proveedoresCatalogoReposicion] = await Promise.all([
        apiGet('/api/productos'),
        apiGet('/api/proveedores'),
      ]);
      if (!Array.isArray(productosCatalogoReposicion) || !Array.isArray(proveedoresCatalogoReposicion)) {
        throw new Error('Los catálogos de productos y proveedores no tienen el formato esperado.');
      }
    }
    const producto = productosCatalogoReposicion.find((item) => String(item.id) === String(productoId));
    const proveedor = producto && proveedoresCatalogoReposicion.find((item) =>
      String(item.id) === String(producto.proveedor?.id));
    if (!producto || !proveedor || String(proveedor.estado || '').trim().toUpperCase() !== 'ACTIVO') {
      throw new Error('El producto no tiene un proveedor activo disponible para registrar el pedido.');
    }
    document.getElementById('reposicion-proveedor').value = proveedor.nombre;
    document.getElementById('reposicion-producto').value = producto.nombre;
    document.getElementById('reposicion-cantidad').value = String(cantidad);
    document.getElementById('reposicion-precio').value = Number(producto.precio).toFixed(2);
    document.getElementById('reposicion-fecha-estimada').value = '';
    errorReposicion.hidden = true;
    errorReposicion.textContent = '';
    productoReposicionActual = producto;
    abrirModalAccesible(modalReposicion);
  } catch (error) {
    mostrarToast(`No se pudo preparar el pedido: ${error.message}`, 'error');
  }
}

function renderizarAlertas(alertas) {
  tablaAlertas.replaceChildren();
  totalAlertas.classList.remove('is-error');

  if (alertas.length === 0) {
    if (botonReponerAlertas) botonReponerAlertas.disabled = true;
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
  if (botonReponerAlertas) {
    botonReponerAlertas.disabled = productosParaReposicion(alertas).length === 0;
  }
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
    const accion = document.createElement('td');
    accion.className = 'alertas-replenishment-action';
    if (esAdministradorAlertas) {
      const boton = document.createElement('button');
      boton.type = 'button';
      boton.className = 'btn btn-secundario alertas-reponer-producto';
      boton.dataset.productoId = alerta.productoId;
      boton.dataset.cantidadSugerida = cantidadSugerida ?? '';
      boton.textContent = 'Reponer';
      boton.disabled = !Number.isSafeInteger(cantidadSugerida) || cantidadSugerida < 1
        || !Number.isSafeInteger(Number(alerta.productoId)) || Number(alerta.productoId) < 1;
      accion.appendChild(boton);
    } else {
      accion.textContent = '—';
    }
    fila.append(nombre, categoria, stock, minimo, estadoCelda, sugerida, accion);
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
    const productoEnlace = window.location.hash.match(/^#alerta-producto-(\d+)$/);
    if (productoEnlace && esAdministradorAlertas) {
      const url = new URL(window.location.href);
      url.hash = '';
      window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}`);
      const alerta = alertas.find((item) => String(item.productoId) === productoEnlace[1]);
      if (alerta) await abrirModalReposicion(alerta.productoId, alerta.cantidadSugerida);
    }
  } catch (error) {
    if (botonReponerAlertas) botonReponerAlertas.disabled = true;
    totalAlertas.textContent = 'No disponible';
    totalAlertas.classList.remove('is-empty');
    totalAlertas.classList.add('is-error');
    mostrarMensajeAlertas(`No se pudieron cargar las alertas: ${error.message}`, 'error');
    mostrarToast(`No se pudieron cargar las alertas: ${error.message}`, 'error');
  }
}

botonReponerAlertas?.addEventListener('click', async () => {
  if (!esAdministradorAlertas || botonReponerAlertas.disabled) return;
  botonReponerAlertas.disabled = true;
  try {
    const alertas = await apiGet('/api/alertas/stock');
    if (!Array.isArray(alertas)) {
      throw new Error('La respuesta del servidor no tiene el formato esperado.');
    }
    const productos = productosParaReposicion(alertas);
    if (!productos.length) {
      mostrarToast('No hay productos con una cantidad de reposición válida.', 'info');
      return;
    }
    sessionStorage.setItem('luany-reposicion-pendiente', JSON.stringify(productos));
    window.location.href = '/pedidos.html?reponer=1';
  } catch (error) {
    botonReponerAlertas.disabled = false;
    mostrarToast(`No se pudo preparar la reposición: ${error.message}`, 'error');
  }
});

tablaAlertas.addEventListener('click', (evento) => {
  const boton = evento.target.closest('.alertas-reponer-producto');
  if (boton) abrirModalReposicion(boton.dataset.productoId, boton.dataset.cantidadSugerida);
});

formReposicion.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  errorReposicion.hidden = true;
  if (!formReposicion.reportValidity()) return;
  let datos;
  try {
    datos = datosPedidoReposicion(
      productoReposicionActual,
      document.getElementById('reposicion-cantidad').value,
      document.getElementById('reposicion-precio').value,
      document.getElementById('reposicion-fecha-estimada').value,
    );
    const creado = await crearPedidoDesdeAlerta(datos);
    if (!creado) return;
    cerrarModalAccesible(modalReposicion);
    mostrarToast('Pedido registrado. El stock se actualizará al confirmar la recepción.', 'exito');
    await cargarAlertas();
  } catch (error) {
    errorReposicion.textContent = error.message;
    errorReposicion.hidden = false;
  }
});

document.querySelectorAll('[data-cerrar-reposicion]').forEach((boton) => {
  boton.addEventListener('click', () => cerrarModalAccesible(modalReposicion));
});
modalReposicion.addEventListener('click', (evento) => {
  if (evento.target === modalReposicion) cerrarModalAccesible(modalReposicion);
});
document.addEventListener('keydown', (evento) => {
  if (evento.key === 'Escape' && !modalReposicion.hidden) cerrarModalAccesible(modalReposicion);
});

cargarAlertas();
