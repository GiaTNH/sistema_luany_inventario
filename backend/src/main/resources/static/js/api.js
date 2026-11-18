/* ===== api.js — helper de fetch, sesión y utilidades ===== */

// ---------- SESIÓN ----------

/** Guarda el usuario en sessionStorage */
function guardarSesion(usuario) {
  sessionStorage.setItem('usuario', JSON.stringify(usuario));
}

/** Devuelve el usuario guardado o null */
function obtenerSesion() {
  const raw = sessionStorage.getItem('usuario');
  return raw ? JSON.parse(raw) : null;
}

/** Cierra la sesión del servidor y redirige al login */
async function cerrarSesion() {
  const usuario = obtenerSesion();
  try {
    await apiPost('/api/auth/logout', {});
  } catch (error) {
    mostrarToast(`No se pudo cerrar la sesión: ${error.message}`, 'error');
    return;
  }

  sessionStorage.removeItem('usuario');
  if (usuario?.id != null) {
    sessionStorage.removeItem(`luany-asistente-historial:${usuario.id}`);
  }
  window.location.href = '/index.html';
}

/** Verifica sesión activa; si no existe redirige al login */
function requerirSesion() {
  const u = obtenerSesion();
  if (!u) { window.location.href = '/index.html'; return null; }
  return u;
}

// ---------- FETCH ----------

/**
 * Realiza una petición a la API.
 * @param {string} ruta  - p.ej. "/api/productos"
 * @param {object} opciones - opciones fetch (method, body, etc.)
 * @returns {Promise<any>} - datos JSON o lanza Error con mensaje legible
 */
async function apiFetch(ruta, opciones = {}) {
  const metodo = (opciones.method || 'GET').toUpperCase();
  const headers = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    ...(opciones.headers || {}),
  };

  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(metodo)) {
    let tokenCsrf;
    try {
      const respuestaCsrf = await fetch('/api/auth/csrf', { cache: 'no-store' });
      if (!respuestaCsrf.ok) throw new Error(`Error ${respuestaCsrf.status}`);
      tokenCsrf = await respuestaCsrf.json();
    } catch (error) {
      throw new Error(`No se pudo validar la solicitud: ${error.message}`);
    }
    headers[tokenCsrf.headerName] = tokenCsrf.token;
  }

  const config = { ...opciones, method: metodo, headers };

  let respuesta;
  try {
    respuesta = await fetch(ruta, config);
  } catch (_) {
    throw new Error('No se pudo conectar con el servidor. Verifica tu conexión.');
  }

  // Respuestas sin cuerpo (204)
  if (respuesta.status === 204) return null;

  const cuerpo = await respuesta.text();
  let datos = null;
  if (cuerpo.trim()) {
    try {
      datos = JSON.parse(cuerpo);
    } catch (_) {
      throw new Error(`Error inesperado del servidor (${respuesta.status}).`);
    }
  }

  if (!respuesta.ok) {
    // El backend devuelve {"error":"mensaje"}
    throw new Error(datos?.error || `Error ${respuesta.status}`);
  }

  if (respuesta.headers?.get('X-Notification-Status') === 'not-sent'
      && datos && typeof datos === 'object') {
    Object.defineProperty(datos, 'notificacionFallida', { value: true });
  }

  return datos;
}

/** GET */
const apiGet = (ruta) => apiFetch(ruta);

/** POST */
const apiPost = (ruta, cuerpo) =>
  apiFetch(ruta, { method: 'POST', body: JSON.stringify(cuerpo) });

/** PUT */
const apiPut = (ruta, cuerpo) =>
  apiFetch(ruta, { method: 'PUT', body: JSON.stringify(cuerpo) });

/** DELETE */
const apiDelete = (ruta) => apiFetch(ruta, { method: 'DELETE' });

function crearTablasAsistente(tablas) {
  if (!Array.isArray(tablas) || tablas.length === 0) return null;
  const contenedor = document.createElement('div');
  contenedor.className = 'asistente-tablas';

  tablas.forEach((tablaDatos) => {
    if (!tablaDatos || !Array.isArray(tablaDatos.columnas)
        || !Array.isArray(tablaDatos.filas)) return;

    const tarjeta = document.createElement('section');
    tarjeta.className = 'asistente-tabla-card';
    const titulo = document.createElement('h3');
    titulo.className = 'asistente-tabla-titulo';
    titulo.textContent = tablaDatos.titulo || 'Registros';
    tarjeta.appendChild(titulo);

    const cantidad = document.createElement('p');
    cantidad.className = 'asistente-tabla-cantidad';
    const total = Number.isFinite(Number(tablaDatos.total)) ? Number(tablaDatos.total) : tablaDatos.filas.length;
    const mostrados = Number.isFinite(Number(tablaDatos.mostrados))
      ? Number(tablaDatos.mostrados) : tablaDatos.filas.length;
    cantidad.textContent = total > mostrados
      ? `Se encontraron ${total} registros · mostrando ${mostrados}`
      : `${total} ${total === 1 ? 'registro encontrado' : 'registros encontrados'}`;
    tarjeta.appendChild(cantidad);

    if (tablaDatos.filas.length === 0) {
      const vacio = document.createElement('p');
      vacio.className = 'asistente-tabla-vacia';
      vacio.textContent = 'No se encontraron registros.';
      tarjeta.appendChild(vacio);
    } else {
      const desplazamiento = document.createElement('div');
      desplazamiento.className = 'asistente-tabla-scroll';
      desplazamiento.setAttribute('role', 'region');
      desplazamiento.setAttribute('aria-label', `Tabla: ${titulo.textContent}`);
      desplazamiento.tabIndex = 0;
      const tabla = document.createElement('table');
      tabla.className = 'asistente-tabla';
      const encabezado = document.createElement('thead');
      const filaEncabezado = document.createElement('tr');
      tablaDatos.columnas.forEach((columna) => {
        const celda = document.createElement('th');
        celda.scope = 'col';
        celda.textContent = String(columna);
        filaEncabezado.appendChild(celda);
      });
      encabezado.appendChild(filaEncabezado);
      tabla.appendChild(encabezado);

      const cuerpo = document.createElement('tbody');
      tablaDatos.filas.forEach((fila) => {
        const tr = document.createElement('tr');
        tablaDatos.columnas.forEach((columna, indice) => {
          const td = document.createElement('td');
          td.textContent = formatearCeldaAsistente(columna, Array.isArray(fila) ? fila[indice] : '');
          tr.appendChild(td);
        });
        cuerpo.appendChild(tr);
      });
      tabla.appendChild(cuerpo);
      desplazamiento.appendChild(tabla);
      tarjeta.appendChild(desplazamiento);
    }
    contenedor.appendChild(tarjeta);
  });

  return contenedor.childElementCount ? contenedor : null;
}

function formatearCeldaAsistente(columna, valor) {
  if (valor == null) return '—';
  if (/fecha/i.test(String(columna))) {
    const fechaHora = String(valor).match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
    if (fechaHora) return `${fechaHora[3]}/${fechaHora[2]}/${fechaHora[1]} ${fechaHora[4]}:${fechaHora[5]}`;
    const fecha = String(valor).match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (fecha) return `${fecha[3]}/${fecha[2]}/${fecha[1]}`;
  }
  return String(valor);
}

// ---------- TOAST ----------

const modalesConFocoPrevio = new WeakMap();
const modalesAbiertos = new Set();
const selectorEnfocableModal =
  'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

function abrirModalAccesible(modal) {
  if (!modal) return;
  if (!modalesAbiertos.has(modal)) modalesConFocoPrevio.set(modal, document.activeElement);
  modal.hidden = false;
  modal.setAttribute('role', modal.getAttribute('role') || 'dialog');
  modal.setAttribute('aria-modal', 'true');
  modal.setAttribute('aria-hidden', 'false');
  if (!modal.hasAttribute('aria-label') && !modal.hasAttribute('aria-labelledby')) {
    const titulo = modal.querySelector('h1, h2, h3, h4');
    if (titulo) modal.setAttribute('aria-label', titulo.textContent.trim());
  }
  modal.classList.add('visible');
  modalesAbiertos.add(modal);
  requestAnimationFrame(() => {
    if (!modalesAbiertos.has(modal)) return;
    const primerElemento = modal.querySelector(selectorEnfocableModal);
    if (primerElemento) primerElemento.focus();
    else {
      modal.setAttribute('tabindex', '-1');
      modal.focus();
    }
  });
}

function cerrarModalAccesible(modal) {
  if (!modal) return;
  modal.classList.remove('visible');
  modal.hidden = true;
  modal.setAttribute('aria-hidden', 'true');
  modalesAbiertos.delete(modal);
  const focoPrevio = modalesConFocoPrevio.get(modal);
  modalesConFocoPrevio.delete(modal);
  if (focoPrevio?.isConnected) focoPrevio.focus();
}

document.addEventListener('keydown', (evento) => {
  if (evento.key !== 'Tab' || modalesAbiertos.size === 0) return;
  const modal = [...modalesAbiertos].at(-1);
  const elementos = [...modal.querySelectorAll(selectorEnfocableModal)]
    .filter((elemento) => !elemento.hidden && elemento.getAttribute('aria-hidden') !== 'true');
  if (elementos.length === 0) {
    evento.preventDefault();
    modal.focus();
    return;
  }

  const primero = elementos[0];
  const ultimo = elementos[elementos.length - 1];
  if (evento.shiftKey && (document.activeElement === primero || !modal.contains(document.activeElement))) {
    evento.preventDefault();
    ultimo.focus();
  } else if (!evento.shiftKey && (document.activeElement === ultimo || !modal.contains(document.activeElement))) {
    evento.preventDefault();
    primero.focus();
  }
});

/** Muestra un toast. tipo: 'exito' | 'error' | 'info' */
function mostrarToast(mensaje, tipo = 'info', duracion = 4000) {
  let contenedor = document.getElementById('toast-container');
  if (!contenedor) {
    contenedor = document.createElement('div');
    contenedor.id = 'toast-container';
    document.body.appendChild(contenedor);
  }

  const toast = document.createElement('div');
  toast.className = `toast toast-${tipo}`;
  toast.textContent = mensaje;
  contenedor.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transition = 'opacity .3s';
    setTimeout(() => toast.remove(), 300);
  }, duracion);
}

// ---------- ESCAPE HTML ----------

/** Escapa texto para insertar de forma segura en el DOM */
function escapar(texto) {
  if (texto == null) return '';
  return String(texto)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// ---------- ESTADO DE STOCK ----------

/**
 * Calcula el estado de stock en el cliente (igual que el backend).
 * @returns {'AGOTADO'|'BAJO'|'EXCESO'|'NORMAL'}
 */
function calcularEstado(stockActual, stockMinimo, stockMaximo) {
  if (stockActual <= 0)           return 'AGOTADO';
  if (stockActual <= stockMinimo) return 'BAJO';
  if (stockActual >= stockMaximo) return 'EXCESO';
  return 'NORMAL';
}

/** Devuelve el HTML de una insignia de estado */
function badgeEstado(estado) {
  const mapa = {
    AGOTADO: 'badge-agotado',
    BAJO:    'badge-bajo',
    EXCESO:  'badge-exceso',
    NORMAL:  'badge-normal',
  };
  const cls = mapa[estado] || 'badge-normal';
  return `<span class="badge ${cls}">${escapar(estado)}</span>`;
}

// ---------- FECHA ----------

const ZONA_HORARIA_LUANY = 'America/Lima';
const formatoFechaHoraLuany = new Intl.DateTimeFormat('es-PE', {
  timeZone: 'UTC',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
const formatoFechaLuany = new Intl.DateTimeFormat('es-PE', {
  timeZone: 'UTC',
  day: '2-digit',
  month: 'short',
  year: 'numeric',
});
const partesFechaLimaLuany = new Intl.DateTimeFormat('en-CA', {
  timeZone: ZONA_HORARIA_LUANY,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

function camposFechaLuany(fecha) {
  if (!fecha) return null;
  if (Array.isArray(fecha)) {
    const [anio, mes, dia, hora = 0, minuto = 0, segundo = 0, nano = 0] = fecha;
    return [anio, mes, dia, hora, minuto, segundo, Math.floor(nano / 1_000_000)];
  }
  if (typeof fecha === 'string') {
    const local = fecha.match(
      /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d+))?)?)?$/,
    );
    if (local) {
      const [, anio, mes, dia, hora = '0', minuto = '0', segundo = '0', fraccion = '0'] = local;
      return [
        Number(anio), Number(mes), Number(dia), Number(hora), Number(minuto),
        Number(segundo), Number(fraccion.padEnd(3, '0').slice(0, 3)),
      ];
    }
  }

  const instante = fecha instanceof Date ? fecha : new Date(fecha);
  if (Number.isNaN(instante.getTime())) return null;
  const partes = Object.fromEntries(partesFechaLimaLuany.formatToParts(instante)
    .map(({ type, value }) => [type, Number(value)]));
  return [partes.year, partes.month, partes.day, partes.hour, partes.minute, partes.second, 0];
}

/** Devuelve una fecha de cálculo que representa los componentes de pared de Lima en UTC. */
function fechaComoDateLuany(fecha) {
  const campos = camposFechaLuany(fecha);
  if (!campos) return null;
  const [anio, mes, dia, hora, minuto, segundo, milisegundo] = campos;
  const resultado = new Date(Date.UTC(anio, mes - 1, dia, hora, minuto, segundo, milisegundo));
  return Number.isNaN(resultado.getTime()) ? null : resultado;
}

function fechaActualLuany() {
  return fechaComoDateLuany(new Date());
}

function fechaIsoLuany(fecha = fechaActualLuany()) {
  if (!fecha) return '';
  return `${fecha.getUTCFullYear()}-${String(fecha.getUTCMonth() + 1).padStart(2, '0')}-${String(fecha.getUTCDate()).padStart(2, '0')}`;
}

function formatearFechaHoraLuany(fecha) {
  const date = fechaComoDateLuany(fecha);
  return date ? formatoFechaHoraLuany.format(date) : '—';
}

function formatearFechaLuany(fecha) {
  const date = fechaComoDateLuany(fecha);
  return date ? formatoFechaLuany.format(date) : '—';
}

/** Normaliza fechas locales del backend sin aplicar una segunda conversión de zona. */
function normalizarFecha(fecha) {
  return formatearFechaHoraLuany(fecha);
}

// ---------- SIDEBAR ACTIVO ----------

/** Marca el enlace activo en la barra lateral según la URL actual */
function marcarNavActivo() {
  const ruta = window.location.pathname.split('/').pop() || 'index.html';
  document.querySelectorAll('.nav-item').forEach(el => {
    el.classList.toggle('activo', el.dataset.pagina === ruta);
  });
}

const ICONOS_SIDEBAR = {
  dashboard: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/>',
  productos: '<path d="m12 2 9 5-9 5-9-5 9-5Z"/><path d="m3 7 9 5 9-5M3 7v10l9 5 9-5V7M12 12v10"/>',
  alertas: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/>',
  proveedores: '<circle cx="9" cy="8" r="3"/><path d="M3 20v-1a6 6 0 0 1 12 0v1M16 4.5a3 3 0 0 1 0 6M18 14a5 5 0 0 1 3 4.6V20"/>',
  clientes: '<circle cx="9" cy="8" r="3"/><path d="M3 20v-1a6 6 0 0 1 12 0v1M17 8h4m-2-2v4"/>',
  ventas: '<path d="M5 3h14v18l-3-2-4 2-4-2-3 2V3Z"/><path d="M8 8h8M8 12h8M8 16h4"/>',
  pedidos: '<path d="M6 3h12l3 5v13H3V8l3-5Z"/><path d="M3 8h18M8 12h8M8 16h5"/>',
  kanban: '<rect x="3" y="4" width="5" height="16" rx="1"/><rect x="10" y="4" width="5" height="10" rx="1"/><rect x="17" y="4" width="5" height="13" rx="1"/>',
  movimientos: '<path d="M3 12a9 9 0 1 0 2.64-6.36L3 8"/><path d="M3 3v5h5m4-1v5l3 2"/>',
  reportes: '<path d="M4 19V5m0 14h17"/><path d="M8 15v-4m5 4V7m5 8v-6"/>',
  usuarios: '<circle cx="9" cy="8" r="3"/><path d="M3 20v-1a6 6 0 0 1 12 0v1M16 11a3 3 0 1 0 0-6M18 14a5 5 0 0 1 3 4.6V20"/>',
  asistente: '<path d="M20 11.5a7.5 7.5 0 0 1-8 7.5 8.8 8.8 0 0 1-3.4-.7L4 20l1.5-3.5A7.2 7.2 0 0 1 4 11.5 7.5 7.5 0 0 1 12 4a7.5 7.5 0 0 1 8 7.5Z"/><path d="M9 10h6M9 14h4"/>',
  chevron: '<path d="m9 18 6-6-6-6"/>',
};

const ENLACES_SIDEBAR = [
  { label: 'Dashboard', href: '/dashboard.html', pagina: 'dashboard.html', icono: 'dashboard' },
  { label: 'Productos', href: '/productos.html', pagina: 'productos.html', icono: 'productos' },
  { label: 'Alertas', href: '/alertas.html', pagina: 'alertas.html', icono: 'alertas' },
  { label: 'Proveedores', href: '/proveedores.html', pagina: 'proveedores.html', icono: 'proveedores' },
  { label: 'Clientes', href: '/clientes.html', pagina: 'clientes.html', icono: 'clientes' },
  { label: 'Notas de venta', href: '/notas-venta.html', pagina: 'notas-venta.html', icono: 'ventas' },
  { label: 'Pedidos', href: '/pedidos.html', pagina: 'pedidos.html', icono: 'pedidos' },
  { label: 'Tablero Kanban', href: '/pedidos-kanban.html', pagina: 'pedidos-kanban.html', icono: 'kanban' },
  { label: 'Movimientos', href: '/movimientos.html', pagina: 'movimientos.html', icono: 'movimientos' },
  { label: 'Reportes', href: '/reportes.html', pagina: 'reportes.html', icono: 'reportes' },
  { label: 'Usuarios', href: '/usuarios.html', pagina: 'usuarios.html', admin: true, icono: 'usuarios' },
  { label: 'Asistente IA', href: '/asistente.html', pagina: 'asistente.html', icono: 'asistente' },
];

function crearIconoSidebar(contenido, clase) {
  const icono = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  icono.setAttribute('viewBox', '0 0 24 24');
  icono.setAttribute('fill', 'none');
  icono.setAttribute('stroke', 'currentColor');
  icono.setAttribute('stroke-width', '1.8');
  icono.setAttribute('stroke-linecap', 'round');
  icono.setAttribute('stroke-linejoin', 'round');
  icono.setAttribute('aria-hidden', 'true');
  if (clase) icono.setAttribute('class', clase);
  icono.innerHTML = contenido;
  return icono;
}

function renderizarNavegacionSidebar(rol) {
  const nav = document.querySelector('.sidebar-nav');
  if (!nav) return;

  const titulo = document.createElement('div');
  titulo.className = 'sidebar-seccion';
  const rayosIzq = document.createElement('span');
  rayosIzq.className = 'sidebar-section-rays';
  rayosIzq.setAttribute('aria-hidden', 'true');
  const rayosDer = rayosIzq.cloneNode();
  titulo.append(rayosIzq, document.createTextNode('MENÚ PRINCIPAL'), rayosDer);

  const enlaces = ENLACES_SIDEBAR
    .filter(enlace => !enlace.admin || rol === 'ADMIN')
    .map(enlace => {
      const link = document.createElement('a');
      link.href = enlace.href;
      link.className = 'nav-item';
      link.dataset.pagina = enlace.pagina;
      if (enlace.seccion) link.dataset.seccion = enlace.seccion;
      if (enlace.tab) link.dataset.tab = enlace.tab;
      link.setAttribute('aria-label', enlace.label);
      link.title = enlace.label;

      const iconoContenedor = document.createElement('span');
      iconoContenedor.className = 'sidebar-nav-icon';
      iconoContenedor.appendChild(crearIconoSidebar(ICONOS_SIDEBAR[enlace.icono]));
      const etiqueta = document.createElement('span');
      etiqueta.className = 'sidebar-nav-label';
      etiqueta.textContent = enlace.label;
      link.append(
        iconoContenedor,
        etiqueta,
        crearIconoSidebar(ICONOS_SIDEBAR.chevron, 'sidebar-chevron'),
      );
      return link;
    });

  nav.replaceChildren(titulo, ...enlaces);
  nav.classList.remove('sidebar-nav-pending');
}

function nombreVisibleRol(rol) {
  const etiquetas = {
    ADMIN: 'Administrador',
    EMPLEADO: 'Almacén',
  };
  return etiquetas[rol] || rol || '';
}

function habilitarAccesoPerfil() {
  document.querySelectorAll('.sidebar-usuario').forEach(elemento => {
    elemento.setAttribute('role', 'link');
    elemento.tabIndex = 0;
    elemento.setAttribute('aria-label', 'Abrir perfil');

    const navegarAlPerfil = evento => {
      if (evento.target.closest('a, button')) return;
      if (evento.type === 'keydown' && evento.key !== 'Enter' && evento.key !== ' ') return;
      evento.preventDefault();
      window.location.href = '/perfil.html';
    };

    elemento.addEventListener('click', navegarAlPerfil);
    elemento.addEventListener('keydown', navegarAlPerfil);
  });
}

function configurarMarcaSidebar() {
  document.querySelectorAll('.sidebar-brand-name').forEach(nombre => {
    nombre.textContent = 'LUANY';
  });
  document.querySelectorAll('.sidebar-brand-tagline').forEach(eslogan => {
    eslogan.textContent = 'juega. viste. crece.';
  });
  document.querySelectorAll('.sidebar-location').forEach(elemento => {
    elemento.hidden = true;
  });
}

/** Inicializa el botón de cerrar sesión y el avatar del topbar */
function iniciarLayout(tituloPagina) {
  const usuario = requerirSesion();
  if (!usuario) return;

  // Título topbar
  const tituloEl = document.getElementById('titulo-pagina');
  if (tituloEl) tituloEl.textContent = tituloPagina;

  // Avatar e inicial
  const avatarEl = document.getElementById('avatar-inicial');
  if (avatarEl) avatarEl.textContent = usuario.nombre.charAt(0).toUpperCase();

  const nombreEl = document.getElementById('nombre-usuario');
  if (nombreEl) nombreEl.textContent = usuario.nombre;
  const rolEl = document.getElementById('rol-usuario');
  if (rolEl) rolEl.textContent = nombreVisibleRol(usuario.rol);
  const saludoEl = document.getElementById('nombre-asistente'); if (saludoEl) saludoEl.textContent = usuario.nombre.split(/\s+/)[0];

  renderizarNavegacionSidebar(usuario.rol);
  configurarMarcaSidebar();
  habilitarAccesoPerfil();

  // Botón cerrar sesión
  document.querySelectorAll('.btn-cerrar-sesion').forEach(btn => {
    btn.addEventListener('click', cerrarSesion);
  });

  marcarNavActivo();
  window.addEventListener('hashchange', marcarNavActivo);

  // Menú móvil
  const btnMenu = document.getElementById('btn-menu-movil');
  const sidebar = document.querySelector('.sidebar');
  if (btnMenu && sidebar) {
    btnMenu.addEventListener('click', () => sidebar.classList.toggle('abierto'));
    document.addEventListener('click', e => {
      if (!sidebar.contains(e.target) && !btnMenu.contains(e.target)) {
        sidebar.classList.remove('abierto');
      }
    });
  }
}

// ---------- PROTECCIÓN DE PÁGINAS ----------

/**
 * Consulta al backend para obtener los datos reales de la sesión.
 */
async function consultarUsuarioActual() {
  try {
    const usuario = await apiGet('/api/auth/me');
    guardarSesion(usuario);
    return usuario;
  } catch (error) {
    sessionStorage.removeItem('usuario');
    return null;
  }
}

/**
 * Comprueba si el usuario puede entrar a una página.
 */
async function protegerPagina(opciones = {}) {
  const usuario = await consultarUsuarioActual();

  if (!usuario) {
    window.location.replace('/index.html');
    return null;
  }

  // La pantalla de contraseña sirve tanto para el primer acceso como para la gestión de cuenta.
  if (opciones.gestionarPassword) {
    return usuario;
  }

  // Los usuarios pendientes deben cambiar su contraseña.
  if (usuario.debeCambiarPassword) {
    window.location.replace('/cambiar-password.html');
    return null;
  }

  // Gestión de usuarios: solamente ADMIN.
  if (opciones.soloAdmin && usuario.rol !== 'ADMIN') {
    window.location.replace('/dashboard.html');
    return null;
  }

  return usuario;
}

/**
 * Carga el JavaScript de una pantalla después de validar el acceso.
 */
async function cargarPaginaProtegida(archivo, opciones = {}) {
  const usuario = await protegerPagina(opciones);

  if (!usuario) return;

  if (!opciones.gestionarPassword) {
    const widget = document.createElement('script');
    widget.src = '/js/asistente-flotante.js';
    widget.async = false;
    widget.onerror = () => {
      console.error('No se pudo cargar el asistente flotante desde /js/asistente-flotante.js');
      mostrarToast('No se pudo cargar el asistente IA. Recarga la página para intentarlo nuevamente.', 'error');
    };
    document.body.appendChild(widget);
  }

  const script = document.createElement('script');
  script.src = archivo;
  document.body.appendChild(script);
}

/**
 * Si ya existe una sesión, redirige según el estado de la contraseña.
 */
async function redirigirSiHaySesionActiva() {
  const usuario = await consultarUsuarioActual();

  if (!usuario) return;

  const destino = usuario.debeCambiarPassword
    ? '/cambiar-password.html'
    : '/dashboard.html';

  window.location.replace(destino);
}