/* ===== asistente.js ===== */

iniciarLayout('Asistente IA');

const inputChat    = document.getElementById('chat-input');
const btnEnviar    = document.getElementById('btn-enviar');
const contadorEl   = document.getElementById('chat-contador');
const mensajesEl   = document.getElementById('chat-mensajes');

let esperando = false;
let sugerenciaPendiente = null;

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

function agregarRespuestaAsistente(texto, meta, tablas = [], opcionesProducto = []) {
  const wrapper = document.createElement('div');
  wrapper.className = 'burbuja-wrapper asistente';

  const burbuja = document.createElement('div');
  burbuja.className = 'burbuja asistente';
  renderizarMarkdownAsistente(burbuja, texto || '(Sin respuesta)');
  wrapper.appendChild(burbuja);

  const tablasEl = crearTablasAsistente(tablas);
  if (tablasEl) {
    wrapper.appendChild(tablasEl);
  }

  if (meta) {
    const metaEl = document.createElement('div');
    metaEl.className = 'burbuja-meta';
    metaEl.textContent = meta;
    wrapper.appendChild(metaEl);
  }

  agregarOpcionesProducto(wrapper, opcionesProducto);
  mensajesEl.appendChild(wrapper);
  mensajesEl.scrollTop = mensajesEl.scrollHeight;
}

function agregarOpcionesProducto(wrapper, opciones) {
  if (!opciones.length) return;
  const sugerencia = sugerenciaPendiente;
  const acciones = document.createElement('div');
  acciones.className = 'asistente-opciones-producto';
  opciones.forEach((opcion) => {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.textContent = opciones.length === 1 ? 'Sí, consultar' : opcion.nombre;
    boton.addEventListener('click', () => enviarMensaje({
      texto: opciones.length === 1 ? 'Sí, consultar' : `Consultar ${opcion.nombre}`,
      productoIdConfirmado: opcion.id,
      sugerencia,
    }));
    acciones.appendChild(boton);
  });
  const cancelar = document.createElement('button');
  cancelar.type = 'button';
  cancelar.textContent = 'No, cancelar';
  cancelar.addEventListener('click', () => enviarMensaje({
    texto: 'No, cancelar',
    cancelarConfirmacion: true,
    sugerencia,
  }));
  acciones.appendChild(cancelar);
  wrapper.appendChild(acciones);
}

function interpretarConfirmacion(texto, opciones) {
  const normalizado = texto.normalize('NFD').replace(/\p{M}/gu, '').trim().toLowerCase();
  if (/^(no|cancelar|ninguno|ninguna)\b/.test(normalizado)) return { cancelarConfirmacion: true };
  if (/^(si|claro|correcto|ese|esa)\b/.test(normalizado) && opciones.length === 1) {
    return { productoIdConfirmado: opciones[0].id };
  }
  const indice = Number(normalizado);
  if (Number.isInteger(indice) && indice > 0 && indice <= opciones.length) {
    return { productoIdConfirmado: opciones[indice - 1].id };
  }
  const coincidencia = opciones.find((opcion) => opcion.nombre.normalize('NFD')
    .replace(/\p{M}/gu, '').toLowerCase() === normalizado);
  return coincidencia ? { productoIdConfirmado: coincidencia.id } : null;
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

async function enviarMensaje(accion = null) {
  if (esperando) return;

  const pregunta = accion ? accion.texto : inputChat.value.trim();
  if (!pregunta) return;
  if (pregunta.length > 500) {
    mostrarToast('La consulta no puede superar los 500 caracteres.', 'error');
    return;
  }

  let cuerpo = { pregunta };
  if (accion && accion.sugerencia) {
    cuerpo = {
      pregunta: accion.sugerencia.pregunta,
      productoIdConfirmado: accion.productoIdConfirmado,
      cancelarConfirmacion: accion.cancelarConfirmacion || false,
    };
    if (sugerenciaPendiente === accion.sugerencia) sugerenciaPendiente = null;
  } else if (sugerenciaPendiente) {
    const confirmacion = interpretarConfirmacion(pregunta, sugerenciaPendiente.opciones);
    if (confirmacion) {
      cuerpo = { pregunta: sugerenciaPendiente.pregunta, ...confirmacion };
    }
    sugerenciaPendiente = null;
  }

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
    const respuesta = await apiPost('/api/asistente/consulta', cuerpo);
    quitarEscribiendo();

    const meta = `Tipo: ${respuesta.tipoConsulta || '—'} · ${respuesta.productosEnContexto} productos en contexto · ${respuesta.tiempoMs} ms`;
    const opcionesProducto = Array.isArray(respuesta.opcionesProducto) ? respuesta.opcionesProducto : [];
    if (opcionesProducto.length) sugerenciaPendiente = { pregunta, opciones: opcionesProducto };
    agregarRespuestaAsistente(respuesta.respuesta || '(Sin respuesta)', meta,
      respuesta.tablas, opcionesProducto);

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
