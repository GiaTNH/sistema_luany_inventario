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
  document.querySelectorAll('.nav-item').forEach(el => {
    el.classList.toggle('activo', el.dataset.pagina === ruta);
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
  const saludoEl = document.getElementById('nombre-asistente'); if (saludoEl) saludoEl.textContent = usuario.nombre.split(/\s+/)[0];

  // Botón cerrar sesión
  document.querySelectorAll('.btn-cerrar-sesion').forEach(btn => {
    btn.addEventListener('click', cerrarSesion);
  });

  marcarNavActivo();

  if (usuario.rol === 'ADMIN' && !document.querySelector('[data-pagina="usuarios.html"]')) {
    const enlaceUsuarios = document.createElement('a');
    enlaceUsuarios.href = '/usuarios.html';
    enlaceUsuarios.className = 'nav-item';
    enlaceUsuarios.dataset.pagina = 'usuarios.html';
    enlaceUsuarios.setAttribute('aria-label', 'Gestión de usuarios');
    const etiqueta = document.createElement('span');
    etiqueta.className = 'sidebar-nav-label';
    etiqueta.textContent = 'Usuarios';
    enlaceUsuarios.appendChild(etiqueta);
    const nav = document.querySelector('.sidebar-nav');
    if (nav) nav.appendChild(enlaceUsuarios);
  }

  marcarNavActivo();

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
