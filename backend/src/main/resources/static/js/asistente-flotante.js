(() => {
  const usuario = obtenerSesion();
  if (!usuario?.id || document.getElementById('luany-asistente-flotante')) return;

  const historialKey = `luany-asistente-historial:${usuario.id}`;
  const preguntasRapidas = [
    '¿Qué productos tienen stock bajo?',
    '¿Qué productos debo reponer y cuántas unidades?',
    '¿Cuáles son los productos con mayor rotación?',
  ];

  let esperando = false;
  let historial = cargarHistorial();

  const widget = document.createElement('section');
  widget.id = 'luany-asistente-flotante';
  widget.className = 'ai-floating-widget';
  if (document.querySelector('.ai-header')) {
    widget.classList.add('ai-floating-over-full-assistant');
  }
  widget.innerHTML = `
    <button class="ai-floating-launcher" type="button" aria-expanded="false"
      aria-controls="luany-asistente-panel" aria-label="Abrir asistente IA de LUANY">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
        stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M12 3a7 7 0 0 0-7 7v3a3 3 0 0 0 3 3h1v-5H7v-1a5 5 0 0 1 10 0v1h-2v5h1a3 3 0 0 0 3-3v-3a7 7 0 0 0-7-7Z"/>
        <path d="M9 19h6"/>
      </svg>
      <span>Asistente IA</span>
    </button>
    <section class="ai-floating-panel" id="luany-asistente-panel" role="dialog"
      aria-labelledby="luany-asistente-titulo" aria-hidden="true" hidden>
      <header class="ai-floating-header">
        <span class="ai-floating-avatar" aria-hidden="true">✦</span>
        <span class="ai-floating-heading">
          <strong id="luany-asistente-titulo"><img class="ai-floating-brand-bag" src="/img/logo-bolsa.png" alt="" />Asistente LUANY</strong>
          <small>Consultas de inventario</small>
        </span>
        <button class="ai-floating-minimize" type="button"
          aria-label="Minimizar asistente" title="Minimizar">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" aria-hidden="true"><path d="M5 12h14"/></svg>
        </button>
      </header>
      <div class="ai-floating-messages" role="log" aria-live="polite" aria-relevant="additions">
        <div class="ai-floating-greeting">¡Hola! Puedo ayudarte a consultar el stock, las alertas,
          la reposición y la rotación de productos.</div>
      </div>
      <div class="ai-floating-quick-questions" aria-label="Preguntas sugeridas"></div>
      <form class="ai-floating-form">
        <label class="ai-floating-input-label" for="luany-asistente-input">Escribe tu consulta</label>
        <textarea id="luany-asistente-input" maxlength="500" rows="2"
          placeholder="Escribe tu consulta..." aria-describedby="luany-asistente-contador"></textarea>
        <span class="ai-floating-counter" id="luany-asistente-contador">0 / 500</span>
        <button class="ai-floating-send" type="submit" aria-label="Enviar pregunta">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m22 2-7 20-4-9-9-4 20-7Z"/><path d="M22 2 11 13"/>
          </svg>
          <span>Enviar</span>
        </button>
        <p class="ai-floating-status" role="status" aria-live="polite"></p>
      </form>
    </section>`;

  document.body.appendChild(widget);

  const launcher = widget.querySelector('.ai-floating-launcher');
  const panel = widget.querySelector('.ai-floating-panel');
  const messages = widget.querySelector('.ai-floating-messages');
  const form = widget.querySelector('.ai-floating-form');
  const input = widget.querySelector('#luany-asistente-input');
  const counter = widget.querySelector('.ai-floating-counter');
  const sendButton = widget.querySelector('.ai-floating-send');
  const status = widget.querySelector('.ai-floating-status');
  const quickQuestions = widget.querySelector('.ai-floating-quick-questions');
  const minimize = widget.querySelector('.ai-floating-minimize');

  function cargarHistorial() {
    try {
      const guardado = JSON.parse(sessionStorage.getItem(historialKey) || '[]');
      return Array.isArray(guardado)
        ? guardado.filter((item) => item && ['usuario', 'asistente', 'error'].includes(item.tipo)
          && typeof item.texto === 'string').slice(-80)
        : [];
    } catch (error) {
      console.warn('No se pudo restaurar el historial del asistente.', error);
      return [];
    }
  }

  function guardarHistorial() {
    try {
      sessionStorage.setItem(historialKey, JSON.stringify(historial.slice(-80)));
    } catch (error) {
      status.textContent = 'No se pudo guardar el historial en esta sesión.';
      console.warn('No se pudo guardar el historial del asistente.', error);
    }
  }

  function agregarMensaje(texto, tipo, meta = '') {
    const mensaje = document.createElement('article');
    mensaje.className = `ai-floating-message ai-floating-message-${tipo}`;
    const burbuja = document.createElement('p');
    burbuja.textContent = texto;
    mensaje.appendChild(burbuja);
    if (meta) {
      const detalle = document.createElement('small');
      detalle.textContent = meta;
      mensaje.appendChild(detalle);
    }
    messages.appendChild(mensaje);
    messages.scrollTop = messages.scrollHeight;
  }

  function renderizarHistorial() {
    historial.forEach((item) => agregarMensaje(item.texto, item.tipo, item.meta || ''));
  }

  function abrir() {
    panel.hidden = false;
    panel.setAttribute('aria-hidden', 'false');
    launcher.setAttribute('aria-expanded', 'true');
    widget.classList.add('ai-floating-open');
    input.focus();
  }

  function minimizar() {
    panel.hidden = true;
    panel.setAttribute('aria-hidden', 'true');
    launcher.setAttribute('aria-expanded', 'false');
    widget.classList.remove('ai-floating-open');
    launcher.focus();
  }

  function agregarPreguntasRapidas() {
    preguntasRapidas.forEach((pregunta) => {
      const boton = document.createElement('button');
      boton.type = 'button';
      boton.className = 'ai-floating-suggestion';
      boton.textContent = pregunta;
      boton.addEventListener('click', () => {
        input.value = pregunta;
        enviarPregunta();
      });
      quickQuestions.appendChild(boton);
    });
  }

  function actualizarContador() {
    counter.textContent = `${input.value.length} / 500`;
    counter.classList.toggle('ai-floating-counter-limit', input.value.length >= 480);
  }

  async function enviarPregunta() {
    if (esperando) return;
    const pregunta = input.value.trim();
    if (!pregunta) {
      input.focus();
      return;
    }
    if (pregunta.length > 500) {
      status.textContent = 'La consulta no puede superar los 500 caracteres.';
      return;
    }

    historial.push({ tipo: 'usuario', texto: pregunta });
    agregarMensaje(pregunta, 'usuario');
    guardarHistorial();
    input.value = '';
    actualizarContador();
    esperando = true;
    input.disabled = true;
    sendButton.disabled = true;
    status.textContent = 'Consultando el inventario...';

    const indicador = document.createElement('p');
    indicador.className = 'ai-floating-typing';
    indicador.textContent = 'LUANY está preparando una respuesta...';
    indicador.setAttribute('role', 'status');
    messages.appendChild(indicador);
    messages.scrollTop = messages.scrollHeight;

    try {
      const respuesta = await apiPost('/api/asistente/consulta', { pregunta });
      const meta = `Tipo: ${respuesta.tipoConsulta || '—'} · ${respuesta.productosEnContexto} productos · ${respuesta.tiempoMs} ms`;
      const texto = respuesta.respuesta || '(Sin respuesta)';
      historial.push({ tipo: 'asistente', texto, meta });
      agregarMensaje(texto, 'asistente', meta);
      guardarHistorial();
      status.textContent = '';
    } catch (error) {
      const mensaje = error.message || 'No se pudo consultar al asistente. Intenta nuevamente.';
      historial.push({ tipo: 'error', texto: mensaje });
      agregarMensaje(mensaje, 'error');
      guardarHistorial();
      status.textContent = 'No se pudo completar la consulta.';
    } finally {
      indicador.remove();
      esperando = false;
      input.disabled = false;
      sendButton.disabled = false;
      input.focus();
    }
  }

  launcher.addEventListener('click', () => (panel.hidden ? abrir() : minimizar()));
  minimize.addEventListener('click', minimizar);
  input.addEventListener('input', actualizarContador);
  input.addEventListener('keydown', (evento) => {
    if (evento.key === 'Enter' && !evento.shiftKey) {
      evento.preventDefault();
      enviarPregunta();
    }
  });
  form.addEventListener('submit', (evento) => {
    evento.preventDefault();
    enviarPregunta();
  });

  agregarPreguntasRapidas();
  renderizarHistorial();
})();
