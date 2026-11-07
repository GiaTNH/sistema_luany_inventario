(() => {
    const formulario = document.getElementById('form-cambiar-password');
    const inputNueva = document.getElementById('nueva-password');
    const inputConfirmar = document.getElementById('confirmar-password');
    const mensajeError = document.getElementById('password-error');
    const boton = document.getElementById('btn-cambiar-password');

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
            const usuario = await apiPost('/api/auth/cambiar-password', {
                nuevaPassword
            });

            guardarSesion(usuario);

            window.location.replace('/dashboard.html');

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