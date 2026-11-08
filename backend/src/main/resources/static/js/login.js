/* ===== login.js ===== */

// Si ya hay sesión activa
redirigirSiHaySesionActiva();

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
    window.location.href = usuario.debeCambiarPassword
      ? '/cambiar-password.html'
      : '/dashboard.html';
  } catch (err) {
    mostrarAvisoApi('login-api-error', err.message);
  } finally {
    btn.disabled = false;
    btn.classList.remove('is-loading');
    if (icono) icono.hidden = false;
    etiqueta.textContent = 'Iniciar sesión';
  }
});

const formularioRegistroInicial = document.getElementById('form-registro-inicial');
const formularioLogin = document.getElementById('form-login');
const tituloLogin = document.getElementById('login-bienvenida');
const subtituloLogin = document.getElementById('login-subtitulo');

function mostrarFormularioRegistroInicial() {
  formularioLogin.classList.remove('activo');
  formularioLogin.hidden = true;
  formularioRegistroInicial.hidden = false;
  formularioRegistroInicial.classList.add('activo');
  tituloLogin.textContent = 'Configura tu administrador';
  subtituloLogin.textContent = 'Crea la cuenta que administrará LUANY';
  document.getElementById('login-admin-note').hidden = true;
  document.getElementById('login-initial-success').hidden = true;
}

function mostrarFormularioLogin() {
  formularioRegistroInicial.classList.remove('activo');
  formularioRegistroInicial.hidden = true;
  formularioLogin.hidden = false;
  formularioLogin.classList.add('activo');
  tituloLogin.textContent = '¡Bienvenida de vuelta!';
  subtituloLogin.textContent = 'Ingresa a tu cuenta para continuar';
  document.getElementById('login-admin-note').hidden = false;
}

function obtenerBytesUtf8(valor) {
  return new TextEncoder().encode(valor).length;
}

document.getElementById('btn-volver-login').addEventListener('click', mostrarFormularioLogin);

formularioRegistroInicial.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const nombre = document.getElementById('registro-nombre').value.trim();
  const email = document.getElementById('registro-email').value.trim();
  const password = document.getElementById('registro-password').value;
  const confirmarPassword = document.getElementById('registro-confirmar-password').value;

  const emailValido = validarEmail(email);
  const passwordValida = password.length >= 12
    && password.length <= 72
    && obtenerBytesUtf8(password) <= 72;
  const passwordsCoinciden = password === confirmarPassword;

  mostrarError('err-registro-nombre', !nombre);
  mostrarError('err-registro-email', !emailValido);
  mostrarError('err-registro-password', !passwordValida);
  mostrarError('err-registro-confirmar', !passwordsCoinciden || !confirmarPassword);
  limpiarAvisoApi('registro-api-error');
  if (!nombre || !emailValido || !passwordValida || !passwordsCoinciden || !confirmarPassword) return;

  const boton = document.getElementById('btn-registro-inicial');
  const etiqueta = boton.querySelector('.login-button-label');
  boton.disabled = true;
  etiqueta.textContent = 'Creando administrador...';

  try {
    await apiPost('/api/auth/registro-inicial', {
      nombre,
      email,
      password,
      confirmarPassword,
    });
    mostrarFormularioLogin();
    document.getElementById('login-email').value = email;
    const avisoExito = document.getElementById('login-initial-success');
    avisoExito.textContent = 'Administrador creado. Ya puedes iniciar sesión.';
    avisoExito.hidden = false;
    formularioRegistroInicial.reset();
  } catch (error) {
    mostrarAvisoApi('registro-api-error', error.message);
    try {
      const estado = await apiGet('/api/auth/registro-inicial/disponible');
      if (!estado.disponible) {
        mostrarFormularioLogin();
        mostrarAvisoApi('login-api-error', error.message);
      }
    } catch (errorEstado) {
      const aviso = document.getElementById('login-initial-status');
      aviso.textContent = `No se pudo verificar el registro inicial: ${errorEstado.message}`;
      aviso.hidden = false;
    }
  } finally {
    boton.disabled = false;
    etiqueta.textContent = 'Crear administrador';
  }
});

async function verificarRegistroInicial() {
  try {
    const estado = await apiGet('/api/auth/registro-inicial/disponible');
    if (estado.disponible) {
      mostrarFormularioRegistroInicial();
    }
  } catch (error) {
    const aviso = document.getElementById('login-initial-status');
    aviso.textContent = `No se pudo verificar el registro inicial: ${error.message}`;
    aviso.hidden = false;
  }
}

verificarRegistroInicial();
