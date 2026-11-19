(() => {
  const usuario = obtenerSesion();
  if (!usuario?.id || document.getElementById('luany-asistente-flotante')) return;
  const launcher = document.querySelector('.sidebar-assistant-access');
  if (!launcher) return;

  const historialKey = `luany-asistente-historial:${usuario.id}`;
  const preguntasRapidas = [
    '¿Qué productos tienen stock bajo?',
    '¿Qué productos debo reponer y cuántas unidades?',
    '¿Cuáles son los productos con mayor rotación?',
  ];

  let esperando = false;
  let sugerenciaPendiente = null;
  let historial = cargarHistorial();

  const widget = document.createElement('section');
  widget.id = 'luany-asistente-flotante';
  widget.className = 'ai-floating-widget';
  if (document.querySelector('.ai-header')) {
    widget.classList.add('ai-floating-over-full-assistant');
  }
  widget.innerHTML = `
    <section class="ai-floating-panel" id="luany-asistente-panel" role="dialog"
      aria-labelledby="luany-asistente-titulo" aria-hidden="true" hidden>
      <header class="ai-floating-header">
        <span class="ai-floating-avatar" aria-hidden="true">✦</span>
        <span class="ai-floating-heading">
          <strong id="luany-asistente-titulo"><img class="ai-floating-brand-bag" src="/img/logo-bolsa.png" alt="" />Asistente LUANY</strong>
          <small>Consultas de inventario</small>
        </span>
        <a class="ai-floating-full-link" href="/asistente.html">Página completa</a>
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

  function agregarMensaje(texto, tipo, meta = '', tablas = [], opcionesProducto = []) {
    const mensaje = document.createElement('article');
    mensaje.className = `ai-floating-message ai-floating-message-${tipo}`;
    if (texto) {
      const burbuja = document.createElement('p');
      renderizarMarkdownAsistente(burbuja, texto);
      mensaje.appendChild(burbuja);
    }
    const tablasEl = crearTablasAsistente(tablas);
    if (tablasEl) mensaje.appendChild(tablasEl);
    if (meta) {
      const detalle = document.createElement('small');
      detalle.textContent = meta;
      mensaje.appendChild(detalle);
    }
    if (tipo === 'asistente' && opcionesProducto.length) {
      const sugerencia = sugerenciaPendiente;
      const acciones = document.createElement('div');
      acciones.className = 'ai-floating-product-options';
      opcionesProducto.forEach((opcion) => {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'ai-floating-suggestion';
        boton.textContent = opcionesProducto.length === 1 ? 'Sí, consultar' : opcion.nombre;
        boton.addEventListener('click', () => enviarPregunta({
          texto: opcionesProducto.length === 1 ? 'Sí, consultar' : `Consultar ${opcion.nombre}`,
          productoIdConfirmado: opcion.id,
          sugerencia,
        }));
        acciones.appendChild(boton);
      });
      const cancelar = document.createElement('button');
      cancelar.type = 'button';
      cancelar.className = 'ai-floating-suggestion';
      cancelar.textContent = 'No, cancelar';
      cancelar.addEventListener('click', () => enviarPregunta({
        texto: 'No, cancelar',
        cancelarConfirmacion: true,
        sugerencia,
      }));
      acciones.appendChild(cancelar);
      mensaje.appendChild(acciones);
    }
    messages.appendChild(mensaje);
    messages.scrollTop = messages.scrollHeight;
  }

  function renderizarHistorial() {
    historial.forEach((item) => {
      const tablas = item.tablas || [];
      const texto = item.tipo === 'asistente' && tablas.length
        ? `Encontré información en ${tablas.length} tablas. Te muestro los resultados.`
        : item.texto;
      if (item.tipo === 'asistente' && item.opcionesProducto?.length) {
        sugerenciaPendiente = {
          pregunta: item.preguntaOriginal,
          opciones: item.opcionesProducto,
        };
      }
      agregarMensaje(texto, item.tipo, item.meta || '', tablas, item.opcionesProducto || []);
    });
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

  function interpretarConfirmacion(texto, opciones) {
    const normalizado = texto.normalize('NFD').replace(/\p{M}/gu, '').trim().toLowerCase();
    if (/^(no|cancelar|ninguno|ninguna)\b/.test(normalizado)) {
      return { cancelarConfirmacion: true };
    }
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

  async function enviarPregunta(accion = null) {
    if (esperando) return;
    const pregunta = accion ? accion.texto : input.value.trim();
    if (!pregunta) {
      input.focus();
      return;
    }
    if (pregunta.length > 500) {
      status.textContent = 'La consulta no puede superar los 500 caracteres.';
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
      const respuesta = await apiPost('/api/asistente/consulta', cuerpo);
      const meta = `Tipo: ${respuesta.tipoConsulta || '—'} · ${respuesta.productosEnContexto} productos · ${respuesta.tiempoMs} ms`;
      const texto = respuesta.respuesta || '(Sin respuesta)';
      const tablas = Array.isArray(respuesta.tablas) ? respuesta.tablas : [];
      const opcionesProducto = Array.isArray(respuesta.opcionesProducto) ? respuesta.opcionesProducto : [];
      if (opcionesProducto.length) sugerenciaPendiente = { pregunta, opciones: opcionesProducto };
      historial.push({ tipo: 'asistente', texto, meta, tablas,
        preguntaOriginal: pregunta, opcionesProducto });
      agregarMensaje(texto, 'asistente', meta, tablas, opcionesProducto);
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
