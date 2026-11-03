/* ===== login.js ===== */

// Si ya hay sesión activa, ir directo al dashboard
if (obtenerSesion()) window.location.href = '/dashboard.html';

/** Alterna entre las pestañas login / registro */
function cambiarTab(tab) {
  document.getElementById('tab-login').classList.toggle('activo', tab === 'login');
  document.getElementById('tab-registro').classList.toggle('activo', tab === 'registro');
  document.getElementById('tab-login').setAttribute('aria-selected', String(tab === 'login'));
  document.getElementById('tab-registro').setAttribute('aria-selected', String(tab === 'registro'));
  document.getElementById('form-login').classList.toggle('activo', tab === 'login');
  document.getElementById('form-registro').classList.toggle('activo', tab === 'registro');
}

function mostrarAvisoApi(id, mensaje) {
  const aviso = document.getElementById(id);
  if (!aviso) return;
  aviso.querySelector('span').textContent = mensaje;
  aviso.hidden = false;
}

function limpiarAvisoApi(id) {
  const aviso = document.getElementById(id);
  if (aviso) aviso.hidden = true;
}

document.getElementById('login-password-toggle').addEventListener('click', () => {
  const campo = document.getElementById('login-password');
  const boton = document.getElementById('login-password-toggle');
  const visible = campo.type === 'password';
  campo.type = visible ? 'text' : 'password';
  boton.setAttribute('aria-pressed', String(visible));
  boton.setAttribute('aria-label', visible ? 'Ocultar contraseña' : 'Mostrar contraseña');
  boton.title = visible ? 'Ocultar contraseña' : 'Mostrar contraseña';
});

document.getElementById('link-olvidaste').addEventListener('click', (evento) => {
  evento.preventDefault();
  const aviso = document.getElementById('forgot-status');
  aviso.hidden = false;
  window.setTimeout(() => { aviso.hidden = true; }, 2500);
});

// ---------- Validaciones ----------

function validarEmail(valor) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(valor.trim());
}

function mostrarError(id, visible) {
  const el = document.getElementById(id);
  if (el) el.classList.toggle('visible', visible);
}

function limpiarErrores(ids) {
  ids.forEach(id => mostrarError(id, false));
}

// ---------- LOGIN ----------

document.getElementById('form-login').addEventListener('submit', async (e) => {
  e.preventDefault();

  const email    = document.getElementById('login-email').value.trim();
  const password = document.getElementById('login-password').value;

  limpiarErrores(['err-login-email', 'err-login-password']);
  limpiarAvisoApi('login-api-error');

  let valido = true;
  if (!validarEmail(email))  { mostrarError('err-login-email', true);    valido = false; }
  if (!password)             { mostrarError('err-login-password', true); valido = false; }
  if (!valido) return;

  const btn = document.getElementById('btn-login');
  const etiqueta = btn.querySelector('.login-button-label');
  const icono = btn.querySelector('.login-submit-arrow');
  btn.disabled = true;
  btn.classList.add('is-loading');
  if (icono) icono.hidden = true;
  etiqueta.textContent = 'Ingresando...';

  try {
    const usuario = await apiPost('/api/auth/login', { email, password });
    guardarSesion(usuario);
    window.location.href = '/dashboard.html';
  } catch (err) {
    mostrarAvisoApi('login-api-error', err.message);
  } finally {
    btn.disabled = false;
    btn.classList.remove('is-loading');
    if (icono) icono.hidden = false;
    etiqueta.textContent = 'Iniciar sesión';
  }
});

// ---------- REGISTRO ----------

document.getElementById('form-registro').addEventListener('submit', async (e) => {
  e.preventDefault();

  const nombre   = document.getElementById('reg-nombre').value.trim();
  const email    = document.getElementById('reg-email').value.trim();
  const password = document.getElementById('reg-password').value;

  limpiarErrores(['err-reg-nombre', 'err-reg-email', 'err-reg-password']);
  limpiarAvisoApi('registro-api-error');

  let valido = true;
  if (!nombre)               { mostrarError('err-reg-nombre', true);    valido = false; }
  if (!validarEmail(email))  { mostrarError('err-reg-email', true);     valido = false; }
  if (password.length < 6)   { mostrarError('err-reg-password', true);  valido = false; }
  if (!valido) return;

  const btn = document.getElementById('btn-registro');
  const etiqueta = btn.querySelector('.login-button-label');
  const icono = btn.querySelector('.login-submit-arrow');
  btn.disabled = true;
  btn.classList.add('is-loading');
  if (icono) icono.hidden = true;
  etiqueta.textContent = 'Creando cuenta...';

  try {
    const usuario = await apiPost('/api/auth/registro', { nombre, email, password });
    guardarSesion(usuario);
    window.location.href = '/dashboard.html';
  } catch (err) {
    mostrarAvisoApi('registro-api-error', err.message);
  } finally {
    btn.disabled = false;
    btn.classList.remove('is-loading');
    if (icono) icono.hidden = false;
    etiqueta.textContent = 'Crear cuenta';
  }
});
