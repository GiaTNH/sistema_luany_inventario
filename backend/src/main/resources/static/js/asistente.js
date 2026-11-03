/* ===== asistente.js ===== */

iniciarLayout('Asistente IA');

const inputChat    = document.getElementById('chat-input');
const btnEnviar    = document.getElementById('btn-enviar');
const contadorEl   = document.getElementById('chat-contador');
const mensajesEl   = document.getElementById('chat-mensajes');

let esperando = false;

// ---------- Contador de caracteres ----------

inputChat.addEventListener('input', () => {
  const len = inputChat.value.length;
  contadorEl.textContent = `${len} / 500`;
  contadorEl.classList.toggle('limite', len >= 480);
});

// ---------- Enter para enviar ----------

inputChat.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    enviarMensaje();
  }
});

// ---------- Preguntas rápidas ----------

function enviarPreguntaRapida(btn) {
  if (esperando) return;
  inputChat.value = btn.textContent.trim();
  contadorEl.textContent = `${inputChat.value.length} / 500`;
  enviarMensaje();
}

// ---------- Agregar burbuja al chat ----------

function agregarBurbuja(texto, tipo, meta = null) {
  const wrapper = document.createElement('div');
  wrapper.className = `burbuja-wrapper ${tipo}`;

  const burbuja = document.createElement('div');
  burbuja.className = `burbuja ${tipo}`;
  // Respeta saltos de línea; el texto ya está escapado
  burbuja.textContent = texto;
  wrapper.appendChild(burbuja);

  if (meta) {
    const metaEl = document.createElement('div');
    metaEl.className = 'burbuja-meta';
    metaEl.textContent = meta;
    wrapper.appendChild(metaEl);
  }

  mensajesEl.appendChild(wrapper);
  mensajesEl.scrollTop = mensajesEl.scrollHeight;
  return wrapper;
}

function agregarEscribiendo() {
  const wrapper = document.createElement('div');
  wrapper.className = 'burbuja-wrapper asistente';
  wrapper.id = 'burbuja-escribiendo';
  wrapper.innerHTML = `
    <div class="escribiendo">
      <span></span><span></span><span></span>
    </div>`;
  mensajesEl.appendChild(wrapper);
  mensajesEl.scrollTop = mensajesEl.scrollHeight;
}

function quitarEscribiendo() {
  const el = document.getElementById('burbuja-escribiendo');
  if (el) el.remove();
}

// ---------- Enviar mensaje ----------

async function enviarMensaje() {
  if (esperando) return;

  const pregunta = inputChat.value.trim();
  if (!pregunta) return;
  if (pregunta.length > 500) {
    mostrarToast('La consulta no puede superar los 500 caracteres.', 'error');
    return;
  }

  // Mostrar burbuja del usuario
  agregarBurbuja(pregunta, 'usuario');

  // Limpiar input
  inputChat.value = '';
  contadorEl.textContent = '0 / 500';
  contadorEl.classList.remove('limite');

  // Bloquear envío
  esperando = true;
  btnEnviar.disabled = true;
  inputChat.disabled = true;

  // Indicador escribiendo
  agregarEscribiendo();

  try {
    const respuesta = await apiPost('/api/asistente/consulta', { pregunta });
    quitarEscribiendo();

    const meta = `Tipo: ${respuesta.tipoConsulta || '—'} · ${respuesta.productosEnContexto} productos en contexto · ${respuesta.tiempoMs} ms`;
    agregarBurbuja(respuesta.respuesta || '(Sin respuesta)', 'asistente', meta);

  } catch (err) {
    quitarEscribiendo();
    // Mostrar error dentro del chat como burbuja de error
    const wrapper = document.createElement('div');
    wrapper.className = 'burbuja-wrapper asistente';
    const burbuja = document.createElement('div');
    burbuja.className = 'burbuja error-chat';
    burbuja.textContent = err.message;
    wrapper.appendChild(burbuja);
    mensajesEl.appendChild(wrapper);
    mensajesEl.scrollTop = mensajesEl.scrollHeight;
  } finally {
    esperando = false;
    btnEnviar.disabled = false;
    inputChat.disabled = false;
    inputChat.focus();
  }
}
