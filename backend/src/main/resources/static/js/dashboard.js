/* ===== dashboard.js ===== */

iniciarLayout('Dashboard');

const intlFecha = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'long', year: 'numeric' });
const intlHora = new Intl.DateTimeFormat('es-PE', { hour: '2-digit', minute: '2-digit', hour12: false });
let movimientosDashboard = [];

function capitalizar(texto) {
  return texto.charAt(0).toLocaleUpperCase('es-PE') + texto.slice(1);
}

function actualizarFechaHora() {
  const ahora = new Date();
  const dia = capitalizar(ahora.toLocaleDateString('es-PE', { weekday: 'short' }).replace(/\.$/, ''));
  const fechaEl = document.getElementById('dashboard-date');
  const horaEl = document.getElementById('dashboard-time');
  if (fechaEl) {
    fechaEl.textContent = `${dia}, ${intlFecha.format(ahora)}`;
    fechaEl.dateTime = ahora.toISOString();
  }
  if (horaEl) horaEl.textContent = `${dia} · ${intlHora.format(ahora)}`;
}

function inicializarCabecera() {
  const usuario = obtenerSesion();
  const avatar = document.getElementById('dashboard-avatar-inicial');
  if (avatar && usuario?.nombre) avatar.textContent = usuario.nombre.trim().charAt(0).toLocaleUpperCase('es-PE');

  actualizarFechaHora();
  window.setInterval(actualizarFechaHora, 60000);

  document.addEventListener('keydown', (evento) => {
    if ((evento.ctrlKey || evento.metaKey) && evento.key.toLowerCase() === 'k') {
      evento.preventDefault();
      document.getElementById('dashboard-product-search')?.focus();
    }
  });

  const alertasLink = document.getElementById('dashboard-alerts-link');
  alertasLink?.addEventListener('click', (evento) => {
    evento.preventDefault();
    document.getElementById('dashboard-alertas')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });

  document.getElementById('dashboard-period')?.addEventListener('change', (evento) => {
    renderGrafico(movimientosDashboard, evento.target.value);
  });

  inicializarBusqueda();
}

function inicializarBusqueda() {
  const input = document.getElementById('dashboard-product-search');
  const results = document.getElementById('dashboard-search-results');
  const wrap = document.getElementById('dashboard-search-wrap');
  if (!input || !results || !wrap) return;

  let temporizador;
  let solicitud = 0;

  const ocultarResultados = () => {
    results.hidden = true;
    input.setAttribute('aria-expanded', 'false');
  };

  const mostrarResultados = () => {
    results.hidden = false;
    input.setAttribute('aria-expanded', 'true');
  };

  input.addEventListener('input', () => {
    const termino = input.value.trim();
    window.clearTimeout(temporizador);
    const idSolicitud = ++solicitud;
    results.replaceChildren();

    if (!termino) {
      ocultarResultados();
      return;
    }

    mostrarResultados();
    const cargando = document.createElement('p');
    cargando.className = 'dashboard-search-message';
    cargando.textContent = 'Buscando productos...';
    results.appendChild(cargando);

    temporizador = window.setTimeout(async () => {
      try {
        const productos = await apiGet(`/api/productos/buscar?nombre=${encodeURIComponent(termino)}`);
        if (idSolicitud !== solicitud) return;
        renderResultadosBusqueda(productos, results, mostrarResultados, ocultarResultados);
      } catch (error) {
        if (idSolicitud !== solicitud) return;
        results.replaceChildren();
        const mensaje = document.createElement('p');
        mensaje.className = 'dashboard-search-message';
        mensaje.textContent = error.message || 'No se pudo completar la búsqueda.';
        results.appendChild(mensaje);
        mostrarResultados();
      }
    }, 250);
  });

  input.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape') ocultarResultados();
  });

  document.addEventListener('pointerdown', (evento) => {
    if (!wrap.contains(evento.target)) ocultarResultados();
  });
}

function renderResultadosBusqueda(productos, contenedor, abrir, cerrar) {
  contenedor.replaceChildren();
  if (!productos.length) {
    const vacio = document.createElement('p');
    vacio.className = 'dashboard-search-message';
    vacio.textContent = 'No se encontraron productos.';
    contenedor.appendChild(vacio);
    abrir();
    return;
  }

  productos.slice(0, 7).forEach((producto) => {
    const resultado = document.createElement('a');
    resultado.className = 'dashboard-search-result';
    resultado.href = '/productos.html';
    resultado.setAttribute('role', 'option');

    const nombre = document.createElement('span');
    nombre.className = 'dashboard-search-name';
    nombre.textContent = producto.nombre || 'Producto sin nombre';

    const detalle = document.createElement('span');
    detalle.className = 'dashboard-search-detail';
    const estado = calcularEstado(producto.stockActual, producto.stockMinimo, producto.stockMaximo);
    detalle.textContent = `Stock: ${Number(producto.stockActual) || 0} · ${estado}`;

    resultado.append(nombre, detalle);
    resultado.addEventListener('click', cerrar);
    contenedor.appendChild(resultado);
  });
  abrir();
}

/** Fecha de hoy en formato YYYY-MM-DD local */
function hoyStr() {
  const h = new Date();
  return `${h.getFullYear()}-${String(h.getMonth()+1).padStart(2,'0')}-${String(h.getDate()).padStart(2,'0')}`;
}

/** Comprueba si una fecha (ISO string o arreglo) corresponde a hoy */
function esHoy(fecha) {
  if (!fecha) return false;
  let d;
  if (Array.isArray(fecha)) {
    const [y, m, day] = fecha;
    d = new Date(y, m - 1, day);
  } else {
    d = new Date(fecha);
  }
  const h = new Date();
  return d.getFullYear() === h.getFullYear() &&
         d.getMonth()    === h.getMonth()    &&
         d.getDate()     === h.getDate();
}

function fechaMovimiento(fecha) {
  if (!fecha) return null;
  if (Array.isArray(fecha)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = fecha;
    return new Date(year, month - 1, day, hour, minute, second);
  }
  const fechaParseada = new Date(fecha);
  return Number.isNaN(fechaParseada.getTime()) ? null : fechaParseada;
}

function claveDia(fecha) {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

/** Comprueba si una fecha está dentro de los últimos N días */
function enUltimosDias(fecha, dias) {
  if (!fecha) return false;
  let d;
  if (Array.isArray(fecha)) {
    const [y, m, day] = fecha;
    d = new Date(y, m - 1, day);
  } else {
    d = new Date(fecha);
  }
  const limite = new Date();
  limite.setDate(limite.getDate() - dias);
  return d >= limite;
}

async function cargarDashboard() {
  const [productosResult, alertasResult, movimientosResult] = await Promise.allSettled([
    apiGet('/api/productos'),
    apiGet('/api/alertas/stock'),
    apiGet('/api/movimientos'),
  ]);

  if (productosResult.status === 'fulfilled') {
    const productos = productosResult.value;
    const totalUnidades = productos.reduce((suma, producto) => suma + (Number(producto.stockActual) || 0), 0);
    const stockBajo = productos.filter((producto) => calcularEstado(producto.stockActual, producto.stockMinimo, producto.stockMaximo) === 'BAJO').length;
    const criticos = productos.filter((producto) => calcularEstado(producto.stockActual, producto.stockMinimo, producto.stockMaximo) === 'AGOTADO').length;

    document.getElementById('stat-total').textContent = totalUnidades.toLocaleString('es-PE');
    document.getElementById('stat-bajo').textContent = stockBajo;
    document.getElementById('stat-criticos').textContent = criticos;

    const productosCount = document.getElementById('stat-productos-count');
    if (productosCount) productosCount.textContent = `${productos.length} productos`;

    const bajoPct = document.getElementById('stat-bajo-pct');
    if (bajoPct) bajoPct.textContent = `${productos.length ? Math.round((stockBajo / productos.length) * 100) : 0}% del catálogo`;
  } else {
    ['stat-total', 'stat-bajo', 'stat-criticos'].forEach((id) => {
      const elemento = document.getElementById(id);
      if (elemento) elemento.textContent = '—';
    });
    mostrarToast('No se pudieron cargar los datos del catálogo.', 'error');
  }

  if (alertasResult.status === 'fulfilled') {
    const alertas = alertasResult.value;
    const alertCount = document.getElementById('dashboard-alert-count');
    const alertLink = document.getElementById('dashboard-alerts-link');
    if (alertCount) {
      alertCount.textContent = alertas.length;
      alertCount.hidden = alertas.length === 0;
    }
    if (alertLink) alertLink.setAttribute('aria-label', `${alertas.length} alertas de stock. Ver alertas recientes`);
    renderAlertas(alertas);
  } else {
    renderAlertas(null);
    const alertCount = document.getElementById('dashboard-alert-count');
    if (alertCount) alertCount.hidden = true;
  }

  if (movimientosResult.status === 'fulfilled') {
    movimientosDashboard = movimientosResult.value;
    const ventasHoy = movimientosDashboard
      .filter((movimiento) => movimiento.tipo === 'VENTA' && esHoy(movimiento.fecha))
      .reduce((suma, movimiento) => suma + (Number(movimiento.cantidad) || 0), 0);
    document.getElementById('stat-ventas').textContent = ventasHoy.toLocaleString('es-PE');
    renderComparacionVentas(movimientosDashboard);
    renderVentasSieteDias(movimientosDashboard);
    renderGrafico(movimientosDashboard, document.getElementById('dashboard-period')?.value || '30');
  } else {
    document.getElementById('stat-ventas').textContent = '—';
    renderGrafico(null);
  }
}

function ventasEnDia(movimientos, fechaObjetivo) {
  const clave = claveDia(fechaObjetivo);
  return movimientos.reduce((total, movimiento) => {
    const fecha = fechaMovimiento(movimiento.fecha);
    if (movimiento.tipo !== 'VENTA' || !fecha || claveDia(fecha) !== clave) return total;
    return total + (Number(movimiento.cantidad) || 0);
  }, 0);
}

function renderComparacionVentas(movimientos) {
  const contenedor = document.getElementById('stat-ventas-comparison');
  if (!contenedor) return;

  const hoy = new Date();
  const ayer = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() - 1);
  const ventasAyer = ventasEnDia(movimientos, ayer);
  contenedor.replaceChildren();

  if (ventasAyer === 0) {
    contenedor.textContent = 'Sin ventas ayer';
    contenedor.classList.remove('is-up', 'is-down');
    return;
  }

  const ventasHoy = ventasEnDia(movimientos, hoy);
  const cambio = Math.round(((ventasHoy - ventasAyer) / ventasAyer) * 100);
  const icono = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  icono.setAttribute('viewBox', '0 0 24 24');
  icono.setAttribute('fill', 'none');
  icono.setAttribute('stroke', 'currentColor');
  icono.setAttribute('stroke-width', '2');
  icono.setAttribute('stroke-linecap', 'round');
  icono.setAttribute('stroke-linejoin', 'round');
  const trayectoria = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  trayectoria.setAttribute('d', cambio > 0 ? 'm4 16 6-6 4 4 6-8M14 6h6v6' : cambio < 0 ? 'm4 8 6 6 4-4 6 8M14 18h6v-6' : 'M5 12h14');
  icono.appendChild(trayectoria);
  contenedor.append(icono, document.createTextNode(`${Math.abs(cambio)}% vs ayer`));
  contenedor.classList.toggle('is-up', cambio > 0);
  contenedor.classList.toggle('is-down', cambio < 0);
}

function renderVentasSieteDias(movimientos) {
  const grafico = document.getElementById('dashboard-sales-chart');
  if (!grafico) return;

  const hoy = new Date();
  const dias = Array.from({ length: 7 }, (_, indice) => {
    const fecha = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() - 6 + indice);
    return { fecha, clave: claveDia(fecha), total: 0 };
  });
  const porDia = new Map(dias.map((dia) => [dia.clave, dia]));

  movimientos.forEach((movimiento) => {
    if (movimiento.tipo !== 'VENTA') return;
    const fecha = fechaMovimiento(movimiento.fecha);
    const dia = fecha && porDia.get(claveDia(fecha));
    if (dia) dia.total += Number(movimiento.cantidad) || 0;
  });

  dibujarGraficoSieteDias(grafico, dias);
}

function dibujarGraficoSieteDias(grafico, dias) {
  const ns = 'http://www.w3.org/2000/svg';
  const maximo = Math.max(...dias.map((dia) => dia.total), 1);
  const puntos = dias.map((dia, indice) => ({
    x: 5 + (indice * 170) / 6,
    y: 51 - (dia.total / maximo) * 39,
  }));
  const linea = puntos.map((punto, indice) => `${indice ? 'L' : 'M'}${punto.x} ${punto.y}`).join(' ');
  const area = `${linea} L175 58 L5 58 Z`;

  grafico.replaceChildren();
  const defs = document.createElementNS(ns, 'defs');
  const gradiente = document.createElementNS(ns, 'linearGradient');
  gradiente.setAttribute('id', 'dashboard-sales-gradient');
  gradiente.setAttribute('x1', '0');
  gradiente.setAttribute('y1', '0');
  gradiente.setAttribute('x2', '0');
  gradiente.setAttribute('y2', '1');
  [['0%', '.28'], ['100%', '0']].forEach(([offset, opacity]) => {
    const stop = document.createElementNS(ns, 'stop');
    stop.setAttribute('offset', offset);
    stop.setAttribute('stop-color', '#10B981');
    stop.setAttribute('stop-opacity', opacity);
    gradiente.appendChild(stop);
  });
  defs.appendChild(gradiente);
  grafico.appendChild(defs);

  const areaForma = document.createElementNS(ns, 'path');
  areaForma.setAttribute('d', area);
  areaForma.setAttribute('fill', 'url(#dashboard-sales-gradient)');
  grafico.appendChild(areaForma);

  const lineaForma = document.createElementNS(ns, 'path');
  lineaForma.setAttribute('d', linea);
  lineaForma.setAttribute('fill', 'none');
  lineaForma.setAttribute('stroke', '#10B981');
  lineaForma.setAttribute('stroke-width', '2.5');
  lineaForma.setAttribute('stroke-linecap', 'round');
  lineaForma.setAttribute('stroke-linejoin', 'round');
  grafico.appendChild(lineaForma);

  const totales = dias.map((dia) => dia.total.toLocaleString('es-PE'));
  grafico.setAttribute('aria-label', `Ventas en unidades durante los últimos siete días: ${totales.join(', ')}`);
}

function renderAlertas(alertas) {
  const contenedor = document.getElementById('lista-alertas');
  contenedor.replaceChildren();

  if (!Array.isArray(alertas)) {
    contenedor.appendChild(crearMensajePanel('No se pudieron cargar las alertas. Intenta nuevamente más tarde.', 'error'));
    return;
  }

  if (!alertas.length) {
    contenedor.appendChild(crearMensajePanel('Sin alertas activas. ¡Todo en orden!', 'empty'));
    return;
  }

  alertas.slice(0, 6).forEach((alerta) => {
    const fila = document.createElement('a');
    fila.className = 'dashboard-alert-row';
    fila.href = '/productos.html';
    fila.setAttribute('aria-label', `${alerta.nombre}, stock ${alerta.stockActual}, mínimo ${alerta.stockMinimo}. Ver productos`);

    const miniatura = crearMiniaturaProducto(alerta.productoId, alerta.nombre, 'dashboard-alert-thumb');
    const detalle = document.createElement('span');
    detalle.className = 'dashboard-alert-copy';

    const nombre = document.createElement('span');
    nombre.className = 'dashboard-alert-name';
    nombre.textContent = alerta.nombre || 'Producto';

    const stock = document.createElement('span');
    stock.className = 'dashboard-alert-stock';
    stock.textContent = `Stock: ${Number(alerta.stockActual) || 0} / Mínimo: ${Number(alerta.stockMinimo) || 0}`;
    detalle.append(nombre, stock);

    const estado = document.createElement('span');
    const slugEstado = alerta.estado === 'AGOTADO' ? 'agotado' : 'bajo';
    estado.className = `dashboard-alert-status is-${slugEstado}`;
    const punto = document.createElement('span');
    punto.className = 'dashboard-alert-status-dot';
    estado.append(punto, document.createTextNode(alerta.estado || 'BAJO'));

    const sugerida = document.createElement('span');
    sugerida.className = 'dashboard-alert-suggested';
    sugerida.textContent = `+${Number(alerta.cantidadSugerida) || 0} uds.`;

    const flecha = crearIconoSvg('M5 12h14m-6-6 6 6-6 6', 'dashboard-alert-arrow');
    fila.append(miniatura, detalle, estado, sugerida, flecha);
    contenedor.appendChild(fila);
  });
}

function renderGrafico(movimientos, periodo = '30') {
  const contenedor = document.getElementById('grafico-ventas');
  contenedor.replaceChildren();

  if (!Array.isArray(movimientos)) {
    contenedor.appendChild(crearMensajePanel('No se pudo cargar el ranking. Intenta nuevamente más tarde.', 'error'));
    return;
  }

  const dias = periodo === 'hoy' ? 1 : Number(periodo) || 30;
  const inicio = new Date();
  inicio.setHours(0, 0, 0, 0);
  inicio.setDate(inicio.getDate() - dias + 1);
  const fin = new Date();
  fin.setHours(23, 59, 59, 999);
  const productos = new Map();

  movimientos.forEach((movimiento) => {
    if (movimiento.tipo !== 'VENTA') return;
    const fecha = fechaMovimiento(movimiento.fecha);
    const producto = movimiento.producto;
    if (!fecha || fecha < inicio || fecha > fin || !producto) return;

    const clave = producto.id ?? producto.nombre;
    if (clave == null) return;
    const entrada = productos.get(clave) || { id: producto.id, nombre: producto.nombre || 'Producto', total: 0 };
    entrada.total += Number(movimiento.cantidad) || 0;
    productos.set(clave, entrada);
  });

  const top5 = [...productos.values()].sort((a, b) => b.total - a.total).slice(0, 5);
  if (!top5.length) {
    contenedor.appendChild(crearMensajePanel('Sin ventas en este periodo.', 'empty'));
    return;
  }

  const maximo = top5[0].total;
  const lista = document.createElement('div');
  lista.className = 'dashboard-ranking-list';

  top5.forEach((producto, indice) => {
    const fila = document.createElement('div');
    fila.className = `dashboard-ranking-row rank-${indice + 1}`;

    const posicion = document.createElement('span');
    posicion.className = 'dashboard-ranking-position';
    posicion.textContent = indice + 1;

    const miniatura = crearMiniaturaProducto(producto.id, producto.nombre, 'dashboard-ranking-thumb');

    const nombre = document.createElement('span');
    nombre.className = 'dashboard-ranking-name';
    nombre.title = producto.nombre;
    nombre.textContent = nombreCortoRanking(producto.nombre);

    const riel = document.createElement('span');
    riel.className = 'dashboard-ranking-track';
    const barra = document.createElement('span');
    barra.className = 'dashboard-ranking-fill';
    barra.style.width = `${maximo ? Math.max((producto.total / maximo) * 100, 4) : 0}%`;
    riel.appendChild(barra);

    const total = document.createElement('span');
    total.className = 'dashboard-ranking-total';
    total.textContent = producto.total.toLocaleString('es-PE');

    fila.append(posicion, miniatura, nombre, riel, total);
    lista.appendChild(fila);
  });

  contenedor.appendChild(lista);
}

function nombreCortoRanking(nombre) {
  const palabras = nombre.trim().split(/\s+/);
  return palabras.length > 4 ? palabras.slice(0, 4).join(' ') : nombre;
}

function crearMensajePanel(texto, estado) {
  const mensaje = document.createElement('div');
  mensaje.className = `dashboard-panel-message is-${estado}`;
  mensaje.textContent = texto;
  return mensaje;
}

function crearIconoSvg(trazo, clase) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '1.8');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('class', clase);
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute('d', trazo);
  svg.appendChild(path);
  return svg;
}

function crearMiniaturaProducto(id, nombre, clase) {
  const miniatura = document.createElement('span');
  miniatura.className = clase;

  const mostrarIcono = () => {
    miniatura.classList.add('is-fallback');
    miniatura.replaceChildren(crearIconoPrenda(nombre));
  };

  if (id == null) {
    mostrarIcono();
    return miniatura;
  }

  const imagen = document.createElement('img');
  imagen.alt = '';
  imagen.loading = 'lazy';
  imagen.addEventListener('error', mostrarIcono, { once: true });
  imagen.src = `/img/productos/${encodeURIComponent(id)}.png`;
  miniatura.appendChild(imagen);
  return miniatura;
}

function crearIconoPrenda(nombre) {
  const normalizado = (nombre || '').toLocaleLowerCase('es-PE').normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  const iconos = [
    ['body', 'M8 3 5 5l2 4v11h10V9l2-4-3-2-2 3h-4L8 3Z'],
    ['mameluco', 'M8 3 5 5l2 4-1 5 2 2-1 4h10l-1-4 2-2-1-5 2-4-3-2-2 3h-4L8 3Z'],
    ['pijama', 'M8 3 5 5l2 4-2 3 2 2-1 6h12l-1-6 2-2-2-3 2-4-3-2-2 3h-4L8 3Z'],
    ['vestido', 'M9 3h6l1 4 3 3-2 11H7L5 10l3-3 1-4Z'],
    ['polo', 'M8 3 5 5l2 4 2-1v12h6V8l2 1 2-4-3-2-2 3h-4L8 3Z'],
    ['jean', 'M7 3h10l1 18h-5l-1-8-1 8H6L7 3Z'],
    ['casaca', 'M8 3 5 5l2 4-1 12h5V11h2v10h5L17 9l2-4-3-2-2 3h-4L8 3Z'],
    ['buzo', 'M8 3 5 5l2 4-1 12h5V11h2v10h5L17 9l2-4-3-2-2 3h-4L8 3Z'],
    ['short', 'M7 3h10l1 10-4 1-2-4-2 4-4-1 1-10Z'],
    ['gorro', 'M4 13a8 8 0 0 1 16 0v2H4v-2Zm-1 4h18v3H3z'],
    ['manta', 'M4 4h16v16H4zM8 4v16m8-16v16M4 9h16m-16 6h16'],
    ['medias', 'M7 3h5v10l3 2-1 5H5v-4l2-3V3Zm8 0h4v8l2 2-1 5h-6l1-4V3Z'],
    ['babero', 'M8 4h8l2 4-2 2v10H8V10L6 8l2-4Zm4 4v4'],
  ];
  const definido = iconos.find(([palabra]) => normalizado.includes(palabra));
  return crearIconoSvg(definido?.[1] || 'M8 3 5 5l2 4 2-1v12h6V8l2 1 2-4-3-2-2 3h-4L8 3Z', 'dashboard-garment-icon');
}

inicializarCabecera();
cargarDashboard();
