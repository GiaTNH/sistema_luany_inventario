(() => {
    const formulario = document.getElementById('form-cambiar-password');
    const inputNueva = document.getElementById('nueva-password');
    const inputConfirmar = document.getElementById('confirmar-password');
    const inputActual = document.getElementById('password-actual');
    const campoPasswordActual = document.getElementById('password-actual-field');
    const mensajeError = document.getElementById('password-error');
    const boton = document.getElementById('btn-cambiar-password');
    const usuario = obtenerSesion();
    const cambioObligatorio = Boolean(usuario?.debeCambiarPassword);
    const panelExito = document.getElementById('password-success');
    const mensajeExito = document.getElementById('password-success-message');
    const errorCierreSesion = document.getElementById('password-logout-error');
    const enlaceDashboard = document.getElementById('password-dashboard-link');
    const botonCerrarSesion = document.getElementById('password-logout-button');

    campoPasswordActual.hidden = cambioObligatorio;
    inputActual.required = !cambioObligatorio;
    if (!cambioObligatorio) {
        document.getElementById('password-heading').textContent = 'Actualiza tu contraseña';
        document.getElementById('password-subtitle').textContent =
            'Confirma tu contraseña actual y crea una nueva.';
    }

    function configurarOjito(idCampo, idBoton) {
        const campo = document.getElementById(idCampo);
        const botonOjito = document.getElementById(idBoton);

        botonOjito.addEventListener('click', () => {
            const visible = campo.type === 'password';

            campo.type = visible ? 'text' : 'password';

            botonOjito.setAttribute('aria-pressed', String(visible));
            botonOjito.setAttribute(
                'aria-label',
                visible ? 'Ocultar contraseña' : 'Mostrar contraseña'
            );
            botonOjito.title = visible
                ? 'Ocultar contraseña'
                : 'Mostrar contraseña';
        });
    }

    configurarOjito('password-actual', 'password-actual-toggle');
    configurarOjito('nueva-password', 'nueva-password-toggle');
    configurarOjito('confirmar-password', 'confirmar-password-toggle');

    async function cerrarSesionYVolverAlLogin() {
        errorCierreSesion.hidden = true;
        try {
            await apiPost('/api/auth/logout', {});
            sessionStorage.removeItem('usuario');
            window.location.replace(cambioObligatorio
                ? '/index.html?passwordChanged=1'
                : '/index.html');
        } catch (error) {
            errorCierreSesion.textContent =
                error.message || 'No se pudo cerrar la sesión. Inténtalo nuevamente.';
            errorCierreSesion.hidden = false;
        }
    }

    botonCerrarSesion.addEventListener('click', cerrarSesionYVolverAlLogin);

    formulario.addEventListener('submit', async (event) => {
        event.preventDefault();

        const passwordActual = inputActual.value;
        const nuevaPassword = inputNueva.value;
        const confirmarPassword = inputConfirmar.value;

        mensajeError.hidden = true;
        mensajeError.textContent = '';

        if (nuevaPassword.length < 12) {
            mostrarError('La nueva contraseña debe tener al menos 12 caracteres.');
            return;
        }

        if (new TextEncoder().encode(nuevaPassword).length > 72) {
            mostrarError('La nueva contraseña supera el límite permitido de 72 bytes.');
            return;
        }

        if (nuevaPassword !== confirmarPassword) {
            mostrarError('Las contraseñas nuevas no coinciden.');
            return;
        }

        if (!cambioObligatorio && !passwordActual) {
            mostrarError('Ingresa tu contraseña actual.');
            return;
        }

        boton.disabled = true;
        boton.textContent = 'Guardando...';

        try {
            await apiPost('/api/auth/cambiar-password', {
                passwordActual: cambioObligatorio ? null : passwordActual,
                nuevaPassword,
                confirmarPassword,
            });

            formulario.hidden = true;
            panelExito.hidden = false;
            if (cambioObligatorio) {
                enlaceDashboard.hidden = true;
                botonCerrarSesion.hidden = true;
                mensajeExito.textContent =
                    'Tu contraseña se actualizó correctamente. Ya puedes iniciar sesión con tu nueva contraseña.';
                await cerrarSesionYVolverAlLogin();
                if (!errorCierreSesion.hidden) {
                    botonCerrarSesion.hidden = false;
                    botonCerrarSesion.textContent = 'Reintentar cierre de sesión';
                }
            } else {
                mensajeExito.textContent = 'Tu contraseña se actualizó correctamente.';
                enlaceDashboard.hidden = false;
                botonCerrarSesion.hidden = false;
            }

        } catch (error) {
            mostrarError(error.message || 'No se pudo cambiar la contraseña. Inténtalo nuevamente.');
        } finally {
            if (!formulario.hidden) {
                boton.disabled = false;
                boton.textContent = 'Guardar nueva contraseña';
            }
        }
    });

    function mostrarError(mensaje) {
        mensajeError.textContent = mensaje;
        mensajeError.hidden = false;
    }
})();