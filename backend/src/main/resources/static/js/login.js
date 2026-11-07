/* ===== login.js ===== */

// Si ya hay sesión activa, ir directo al dashboard
if (obtenerSesion()) window.location.href = '/dashboard.html';

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
