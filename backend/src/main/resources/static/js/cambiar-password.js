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
            const usuarioActualizado = await apiPost('/api/auth/cambiar-password', {
                passwordActual: cambioObligatorio ? null : passwordActual,
                nuevaPassword,
                confirmarPassword,
            });
            guardarSesion(usuarioActualizado);
            window.location.replace(cambioObligatorio
                ? '/dashboard.html?passwordChanged=1'
                : '/dashboard.html');
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