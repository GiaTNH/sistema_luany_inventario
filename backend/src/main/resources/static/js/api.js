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
  try {
    await apiPost('/api/auth/logout', {});
  } catch (error) {
    mostrarToast(`No se pudo cerrar la sesión: ${error.message}`, 'error');
    return;
  }

  sessionStorage.removeItem('usuario');
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

  let datos;
  try {
    datos = await respuesta.json();
  } catch (_) {
    throw new Error(`Error inesperado del servidor (${respuesta.status}).`);
  }

  if (!respuesta.ok) {
    // El backend devuelve {"error":"mensaje"}
    throw new Error(datos.error || `Error ${respuesta.status}`);
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

// ---------- TOAST ----------

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

/**
 * Normaliza la fecha que puede venir como string ISO o como arreglo
 * [año, mes, día, hora, min, seg] del backend Java.
 */
function normalizarFecha(fecha) {
  if (!fecha) return '—';
  if (Array.isArray(fecha)) {
    // [2024, 6, 15, 10, 30, 0] → mes es 1-based en Java
    const [y, m, d, h = 0, min = 0] = fecha;
    return new Date(y, m - 1, d, h, min).toLocaleString('es-PE');
  }
  return new Date(fecha).toLocaleString('es-PE');
}

// ---------- SIDEBAR ACTIVO ----------

/** Marca el enlace activo en la barra lateral según la URL actual */
function marcarNavActivo() {
  const ruta = window.location.pathname.split('/').pop() || 'index.html';
  const seccion = window.location.hash.slice(1);
  document.querySelectorAll('.nav-item').forEach(el => {
    const esProveedor = el.dataset.tab === 'proveedores';
    const esAlerta = el.dataset.seccion === 'alertas';
    const activo = esProveedor
      ? ruta === 'productos.html' && seccion === 'proveedores'
      : esAlerta
        ? ruta === 'dashboard.html' && seccion === 'dashboard-alertas'
        : el.dataset.pagina === ruta
          && !(ruta === 'productos.html' && seccion === 'proveedores')
          && !(ruta === 'dashboard.html' && seccion === 'dashboard-alertas');
    el.classList.toggle('activo', activo);
  });
}

const ICONOS_SIDEBAR = {
  dashboard: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/>',
  productos: '<path d="m12 2 9 5-9 5-9-5 9-5Z"/><path d="m3 7 9 5 9-5M3 7v10l9 5 9-5V7M12 12v10"/>',
  alertas: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/>',
  proveedores: '<circle cx="9" cy="8" r="3"/><path d="M3 20v-1a6 6 0 0 1 12 0v1M16 4.5a3 3 0 0 1 0 6M18 14a5 5 0 0 1 3 4.6V20"/>',
  movimientos: '<path d="M3 12a9 9 0 1 0 2.64-6.36L3 8"/><path d="M3 3v5h5m4-1v5l3 2"/>',
  usuarios: '<circle cx="9" cy="8" r="3"/><path d="M3 20v-1a6 6 0 0 1 12 0v1M16 11a3 3 0 1 0 0-6M18 14a5 5 0 0 1 3 4.6V20"/>',
  asistente: '<path d="M20 11.5a7.5 7.5 0 0 1-8 7.5 8.8 8.8 0 0 1-3.4-.7L4 20l1.5-3.5A7.2 7.2 0 0 1 4 11.5 7.5 7.5 0 0 1 12 4a7.5 7.5 0 0 1 8 7.5Z"/><path d="M9 10h6M9 14h4"/>',
  chevron: '<path d="m9 18 6-6-6-6"/>',
};

const ENLACES_SIDEBAR = [
  { label: 'Dashboard', href: '/dashboard.html', pagina: 'dashboard.html', icono: 'dashboard' },
  { label: 'Productos', href: '/productos.html', pagina: 'productos.html', icono: 'productos' },
  { label: 'Alertas', href: '/dashboard.html#dashboard-alertas', pagina: 'dashboard.html', seccion: 'alertas', icono: 'alertas' },
  { label: 'Proveedores', href: '/productos.html#proveedores', pagina: 'productos.html', tab: 'proveedores', icono: 'proveedores' },
  { label: 'Movimientos', href: '/movimientos.html', pagina: 'movimientos.html', icono: 'movimientos' },
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
  if (rolEl) rolEl.textContent = usuario.rol === 'ADMIN' ? 'Administrador' : 'Empleado';
  const saludoEl = document.getElementById('nombre-asistente'); if (saludoEl) saludoEl.textContent = usuario.nombre.split(/\s+/)[0];

  renderizarNavegacionSidebar(usuario.rol);

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