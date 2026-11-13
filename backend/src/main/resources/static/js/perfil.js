iniciarLayout('Perfil');

const formularioPerfil = document.getElementById('form-perfil');
const nombrePerfil = document.getElementById('perfil-nombre');
const correoPerfil = document.getElementById('perfil-email');
const rolPerfil = document.getElementById('perfil-rol');
const errorPerfil = document.getElementById('perfil-error');
const exitoPerfil = document.getElementById('perfil-exito');
const botonGuardarPerfil = document.getElementById('btn-guardar-perfil');

function mostrarDatosPerfil(usuario) {
  nombrePerfil.value = usuario.nombre || '';
  correoPerfil.value = usuario.email || '';
  rolPerfil.value = nombreVisibleRol(usuario.rol);
}

formularioPerfil.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  errorPerfil.hidden = true;
  exitoPerfil.hidden = true;
  if (!formularioPerfil.reportValidity()) return;

  botonGuardarPerfil.disabled = true;
  try {
    const usuario = await apiPut('/api/auth/me/perfil', { nombre: nombrePerfil.value.trim() });
    guardarSesion(usuario);
    mostrarDatosPerfil(usuario);
    const nombreSidebar = document.getElementById('nombre-usuario');
    const avatarSidebar = document.getElementById('avatar-inicial');
    if (nombreSidebar) nombreSidebar.textContent = usuario.nombre;
    if (avatarSidebar) avatarSidebar.textContent = usuario.nombre.trim().charAt(0).toLocaleUpperCase('es-PE');
    exitoPerfil.textContent = 'Tus datos se actualizaron correctamente.';
    exitoPerfil.hidden = false;
  } catch (error) {
    errorPerfil.textContent = error.message || 'No se pudieron guardar los cambios.';
    errorPerfil.hidden = false;
  } finally {
    botonGuardarPerfil.disabled = false;
  }
});

const usuarioPerfil = obtenerSesion();
if (usuarioPerfil) {
  mostrarDatosPerfil(usuarioPerfil);
} else {
  errorPerfil.textContent = 'No se pudo cargar tu perfil. Inicia sesión nuevamente.';
  errorPerfil.hidden = false;
  botonGuardarPerfil.disabled = true;
}
