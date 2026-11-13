/* ===== login.js ===== */

const cambioPasswordCompletado =
  new URLSearchParams(window.location.search).get('passwordChanged') === '1';
const restablecimientoPasswordCompletado =
  new URLSearchParams(window.location.search).get('passwordReset') === '1';
if (cambioPasswordCompletado || restablecimientoPasswordCompletado) {
  history.replaceState(null, document.title, window.location.pathname + window.location.hash);
}

// Un enlace de recuperación debe poder utilizarse aunque exista una sesión activa.
if (!window.location.hash.startsWith('#restablecer')
    && !cambioPasswordCompletado
    && !restablecimientoPasswordCompletado) {
  redirigirSiHaySesionActiva();
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

if (sessionStorage.getItem('avisoCorreoNoNotificado') === '1') {
  sessionStorage.removeItem('avisoCorreoNoNotificado');
  mostrarAvisoApi(
    'login-api-error',
    'El cambio de correo se guardó, pero no se pudo enviar la notificación.',
  );
}

function configurarOjito(idCampo, idBoton) {
  const campo = document.getElementById(idCampo);
  const boton = document.getElementById(idBoton);
  boton.addEventListener('click', () => {
    const visible = campo.type === 'password';
    campo.type = visible ? 'text' : 'password';
    boton.setAttribute('aria-pressed', String(visible));
    boton.setAttribute('aria-label', visible ? 'Ocultar contraseña' : 'Mostrar contraseña');
    boton.title = visible ? 'Ocultar contraseña' : 'Mostrar contraseña';
  });
}

configurarOjito('login-password', 'login-password-toggle');
configurarOjito('recuperacion-password', 'recuperacion-password-toggle');
configurarOjito(
  'recuperacion-confirmar-password',
  'recuperacion-confirmar-password-toggle',
);

document.getElementById('link-olvidaste').addEventListener('click', (evento) => {
  evento.preventDefault();
  mostrarFormularioSolicitarRecuperacion();
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
const formularioSolicitudRecuperacion = document.getElementById('form-solicitar-recuperacion');
const formularioRestablecerPassword = document.getElementById('form-restablecer-password');
const tituloLogin = document.getElementById('login-bienvenida');
const subtituloLogin = document.getElementById('login-subtitulo');

if (cambioPasswordCompletado || restablecimientoPasswordCompletado) {
  const avisoExito = document.getElementById('login-initial-success');
  avisoExito.textContent = restablecimientoPasswordCompletado
    ? 'Tu contraseña se restableció correctamente. Ya puedes iniciar sesión con tu nueva contraseña.'
    : 'Tu contraseña se actualizó correctamente. Ya puedes iniciar sesión con tu nueva contraseña.';
  avisoExito.hidden = false;
}

function mostrarFormulario(formulario, titulo, subtitulo) {
  [formularioLogin, formularioRegistroInicial, formularioSolicitudRecuperacion, formularioRestablecerPassword]
    .forEach(form => {
      form.classList.remove('activo');
      form.hidden = true;
    });
  formulario.hidden = false;
  formulario.classList.add('activo');
  tituloLogin.textContent = titulo;
  subtituloLogin.textContent = subtitulo;
}

function mostrarFormularioRegistroInicial() {
  mostrarFormulario(
    formularioRegistroInicial,
    'Configura tu administrador',
    'Crea la cuenta que administrará LUANY',
  );
  document.getElementById('login-admin-note').hidden = true;
  document.getElementById('login-initial-success').hidden = true;
}

function mostrarFormularioLogin() {
  mostrarFormulario(formularioLogin, '¡Bienvenida de vuelta!', 'Ingresa a tu cuenta para continuar');
  document.getElementById('login-admin-note').hidden = false;
}

function mostrarFormularioSolicitarRecuperacion() {
  history.replaceState(null, document.title, window.location.pathname + window.location.search);
  mostrarFormulario(
    formularioSolicitudRecuperacion,
    'Recupera tu contraseña',
    'Te enviaremos instrucciones a tu correo',
  );
  document.getElementById('recuperacion-email').value =
    document.getElementById('login-email').value.trim();
  document.getElementById('recuperacion-resultado').hidden = true;
  limpiarAvisoApi('recuperacion-api-error');
  document.getElementById('login-admin-note').hidden = true;
}

function mostrarFormularioRestablecer(token) {
  mostrarFormulario(
    formularioRestablecerPassword,
    'Crea una nueva contraseña',
    'Elige una contraseña segura para tu cuenta',
  );
  document.getElementById('login-admin-note').hidden = true;
  tokenRecuperacion = token;
  if (!tokenRecuperacion) {
    mostrarAvisoApi('restablecer-api-error', 'El enlace no es válido o ha expirado. Solicita uno nuevo.');
  } else {
    limpiarAvisoApi('restablecer-api-error');
  }
}

let tokenRecuperacion = null;

function obtenerBytesUtf8(valor) {
  return new TextEncoder().encode(valor).length;
}

document.getElementById('btn-volver-login').addEventListener('click', mostrarFormularioLogin);
document.getElementById('btn-volver-login-recuperacion').addEventListener('click', mostrarFormularioLogin);
document.getElementById('btn-volver-login-restablecer').addEventListener('click', mostrarFormularioLogin);

formularioSolicitudRecuperacion.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const email = document.getElementById('recuperacion-email').value.trim();
  mostrarError('err-recuperacion-email', !validarEmail(email));
  limpiarAvisoApi('recuperacion-api-error');
  document.getElementById('recuperacion-resultado').hidden = true;
  if (!validarEmail(email)) return;

  const boton = document.getElementById('btn-solicitar-recuperacion');
  const etiqueta = boton.querySelector('.login-button-label');
  boton.disabled = true;
  etiqueta.textContent = 'Enviando...';
  try {
    const respuesta = await apiPost('/api/auth/solicitar-recuperacion', { email });
    const resultado = document.getElementById('recuperacion-resultado');
    resultado.textContent = respuesta.mensaje;
    resultado.classList.toggle(
      'login-recovery-result-error',
      !respuesta.mensaje.startsWith('Enviamos un enlace'),
    );
    resultado.hidden = false;
  } catch (error) {
    mostrarAvisoApi('recuperacion-api-error', error.message);
  } finally {
    boton.disabled = false;
    etiqueta.textContent = 'Enviar enlace';
  }
});

formularioRestablecerPassword.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const nuevaPassword = document.getElementById('recuperacion-password').value;
  const confirmarPassword = document.getElementById('recuperacion-confirmar-password').value;
  const passwordValida = nuevaPassword.length >= 12
    && nuevaPassword.length <= 72
    && obtenerBytesUtf8(nuevaPassword) <= 72;
  const passwordsCoinciden = nuevaPassword === confirmarPassword;

  mostrarError('err-recuperacion-password', !passwordValida);
  mostrarError('err-recuperacion-confirmar', !confirmarPassword || !passwordsCoinciden);
  limpiarAvisoApi('restablecer-api-error');
  document.getElementById('restablecer-resultado').hidden = true;
  if (!passwordValida || !confirmarPassword || !passwordsCoinciden) return;
  if (!tokenRecuperacion) {
    mostrarAvisoApi('restablecer-api-error', 'El enlace no es válido o ha expirado. Solicita uno nuevo.');
    return;
  }

  const boton = document.getElementById('btn-restablecer-password');
  const etiqueta = boton.querySelector('.login-button-label');
  let restablecido = false;
  boton.disabled = true;
  etiqueta.textContent = 'Guardando...';
  try {
    const respuesta = await apiPost('/api/auth/restablecer-password', {
      token: tokenRecuperacion,
      nuevaPassword,
      confirmarPassword,
    });
    const resultado = document.getElementById('restablecer-resultado');
    resultado.textContent = respuesta.mensaje;
    resultado.hidden = false;
    tokenRecuperacion = null;
    restablecido = true;
    formularioRestablecerPassword.reset();
    window.location.replace('/index.html?passwordReset=1');
  } catch (error) {
    mostrarAvisoApi('restablecer-api-error', error.message);
  } finally {
    boton.disabled = restablecido;
    etiqueta.textContent = restablecido ? 'Contraseña actualizada' : 'Guardar contraseña';
  }
});

function cargarFlujoRecuperacion() {
  if (!window.location.hash.startsWith('#restablecer')) return false;
  const separador = window.location.hash.indexOf('?');
  const parametros = separador >= 0
    ? new URLSearchParams(window.location.hash.slice(separador + 1))
    : new URLSearchParams();
  const token = parametros.get('token');
  history.replaceState(null, document.title, window.location.pathname + window.location.search);
  mostrarFormularioRestablecer(token);
  return true;
}

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

if (!cargarFlujoRecuperacion()) {
  verificarRegistroInicial();
}
