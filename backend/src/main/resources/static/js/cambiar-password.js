(() => {
    const formulario = document.getElementById('form-cambiar-password');
    const inputNueva = document.getElementById('nueva-password');
    const inputConfirmar = document.getElementById('confirmar-password');
    const mensajeError = document.getElementById('password-error');
    const boton = document.getElementById('btn-cambiar-password');
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

    configurarOjito('nueva-password', 'nueva-password-toggle');
    configurarOjito('confirmar-password', 'confirmar-password-toggle');

    formulario.addEventListener('submit', async (event) => {
        event.preventDefault();

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

        boton.disabled = true;
        boton.textContent = 'Guardando...';

        try {
            await apiPost('/api/auth/cambiar-password', {
                nuevaPassword
            });

            await apiPost('/api/auth/logout', {});
            sessionStorage.removeItem('usuario');
            window.location.replace('/index.html?passwordChanged=1');

        } catch (error) {
            mostrarError(error.message || 'No se pudo cambiar la contraseña. Inténtalo nuevamente.');
        } finally {
            boton.disabled = false;
            boton.textContent = 'Guardar nueva contraseña';
        }
    });

    function mostrarError(mensaje) {
        mensajeError.textContent = mensaje;
        mensajeError.hidden = false;
    }
})();