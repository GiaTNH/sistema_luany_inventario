const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const staticDirectory = path.resolve(__dirname, '../../backend/src/main/resources/static');
const apiSource = fs.readFileSync(path.join(staticDirectory, 'js/api.js'), 'utf8');

function crearEntorno(usuario) {
  const scripts = [];
  const valoresSesion = new Map();
  const document = {
    addEventListener() {},
    createElement() {
      return { src: '', async: true };
    },
    body: {
      appendChild(script) {
        scripts.push(script);
      },
    },
  };
  const contexto = vm.createContext({
    document,
    fetch: async () => ({
      ok: true,
      status: 200,
      json: async () => usuario,
    }),
    sessionStorage: {
      getItem: (clave) => valoresSesion.get(clave) ?? null,
      setItem: (clave, valor) => valoresSesion.set(clave, valor),
      removeItem: (clave) => valoresSesion.delete(clave),
    },
    window: { location: { replace() {} } },
    console,
  });
  vm.runInContext(apiSource, contexto);
  return {
    cargarPaginaProtegida: vm.runInContext('cargarPaginaProtegida', contexto),
    scripts,
  };
}

function crearEntornoWidget(historialGuardado = '[]', respuestaApi = {
  tipoConsulta: 'STOCK_BAJO',
  productosEnContexto: 2,
  tiempoMs: 120,
  respuesta: 'Hay dos productos con stock bajo.',
}) {
  const valoresSesion = new Map([['luany-asistente-historial:7', historialGuardado]]);
  const elementosWidget = new Map();
  const document = { activeElement: null };
  let widgetMounted = false;
  let widget;

  function elemento(etiqueta) {
    const listeners = new Map();
    const clases = new Set();
    return {
      etiqueta,
      children: [],
      classList: {
        add: (clase) => clases.add(clase),
        remove: (clase) => clases.delete(clase),
        toggle: (clase, activo) => activo ? clases.add(clase) : clases.delete(clase),
        contains: (clase) => clases.has(clase),
      },
      addEventListener: (tipo, listener) => listeners.set(tipo, listener),
      async emitir(tipo, evento = {}) {
        const listener = listeners.get(tipo);
        if (listener) await listener(evento);
      },
      appendChild(hijo) {
        hijo.parentNode = this;
        this.children.push(hijo);
        return hijo;
      },
      append(...hijos) {
        hijos.forEach((hijo) => this.appendChild(hijo));
      },
      setAttribute(nombre, valor) {
        this.atributos ||= new Map();
        this.atributos.set(nombre, valor);
      },
      getAttribute(nombre) {
        return this.atributos?.get(nombre) ?? null;
      },
      focus() {
        document.activeElement = this;
      },
      remove() {
        if (this.parentNode) {
          this.parentNode.children = this.parentNode.children.filter((hijo) => hijo !== this);
        }
      },
      value: '',
      hidden: false,
      disabled: false,
      scrollHeight: 100,
      scrollTop: 0,
      textContent: '',
    };
  }

  [
    '.ai-floating-launcher',
    '.ai-floating-panel',
    '.ai-floating-messages',
    '.ai-floating-form',
    '#luany-asistente-input',
    '.ai-floating-counter',
    '.ai-floating-send',
    '.ai-floating-status',
    '.ai-floating-quick-questions',
    '.ai-floating-minimize',
  ].forEach((selector) => elementosWidget.set(selector, elemento(selector)));
  elementosWidget.get('.ai-floating-panel').hidden = true;
  elementosWidget.get('.ai-floating-panel').setAttribute('aria-hidden', 'true');

  widget = elemento('section');
  widget.id = 'luany-asistente-flotante';
  widget.querySelector = (selector) => elementosWidget.get(selector);
  widget.querySelectorAll = () => [];
  widget.classList = {
    add() {},
    remove() {},
  };
  const documentBody = {
    appendChild(hijo) {
      hijo.parentNode = this;
      if (hijo === widget) widgetMounted = true;
    },
  };
  document.createElement = (etiqueta) => etiqueta === 'section' ? widget : elemento(etiqueta);
  document.getElementById = (id) => widgetMounted && id === widget.id ? widget : null;
  document.querySelector = () => null;
  document.body = documentBody;

  const llamadas = [];
  const contexto = vm.createContext({
    document,
    obtenerSesion: () => ({ id: 7 }),
    sessionStorage: {
      getItem: (clave) => valoresSesion.get(clave) ?? null,
      setItem: (clave, valor) => valoresSesion.set(clave, valor),
    },
    apiPost: async (ruta, cuerpo) => {
      llamadas.push({ ruta, cuerpo });
      if (respuestaApi instanceof Error) throw respuestaApi;
      return respuestaApi;
    },
    console,
  });
  const widgetSource = fs.readFileSync(
    path.join(staticDirectory, 'js/asistente-flotante.js'),
    'utf8',
  );
  vm.runInContext(widgetSource, contexto);
  return { document, elementosWidget, llamadas, valoresSesion, widget };
}

const usuarioEmpleado = {
  id: 7,
  nombre: 'Luany Empleada',
  email: 'empleada@luany.test',
  rol: 'EMPLEADO',
  debeCambiarPassword: false,
};

test('el widget se inyecta tras validar la sesión de páginas internas', async () => {
  const entorno = crearEntorno(usuarioEmpleado);

  await entorno.cargarPaginaProtegida('/js/productos.js');

  assert.deepEqual(entorno.scripts.map((script) => script.src), [
    '/js/asistente-flotante.js',
    '/js/productos.js',
  ]);
  assert.equal(entorno.scripts[0].async, false);
  assert.equal(typeof entorno.scripts[0].onerror, 'function');
});

test('el cambio obligatorio de contraseña no carga el widget', async () => {
  const entorno = crearEntorno({
    ...usuarioEmpleado,
    debeCambiarPassword: true,
  });

  await entorno.cargarPaginaProtegida('/js/cambiar-password.js', { gestionarPassword: true });

  assert.deepEqual(entorno.scripts.map((script) => script.src), ['/js/cambiar-password.js']);
});

test('todas las pantallas internas están protegidas; login y contraseña quedan excluidos', () => {
  const pantallasInternas = [
    'asistente.html',
    'clientes.html',
    'dashboard.html',
    'movimientos.html',
    'notas-venta.html',
    'pedidos-kanban.html',
    'pedidos.html',
    'productos.html',
    'proveedores.html',
    'reportes.html',
    'usuarios.html',
  ];

  pantallasInternas.forEach((pantalla) => {
    const html = fs.readFileSync(path.join(staticDirectory, pantalla), 'utf8');
    assert.match(html, /cargarPaginaProtegida\(/, `${pantalla} debe usar el cargador protegido`);
  });

  const login = fs.readFileSync(path.join(staticDirectory, 'index.html'), 'utf8');
  const cambioPassword = fs.readFileSync(path.join(staticDirectory, 'cambiar-password.html'), 'utf8');
  assert.doesNotMatch(login, /cargarPaginaProtegida\(/);
  assert.match(cambioPassword, /gestionarPassword:\s*true/);
});

test('el widget conserva historial por usuario y solo reutiliza el endpoint de consulta', () => {
  const widgetSource = fs.readFileSync(path.join(staticDirectory, 'js/asistente-flotante.js'), 'utf8');
  const assistantServiceDirectory = path.resolve(
    __dirname,
    '../../backend/src/main/java/com/luany/inventory/service',
  );
  const controllersDirectory = path.resolve(
    __dirname,
    '../../backend/src/main/java/com/luany/inventory/controller',
  );
  const services = fs.readdirSync(assistantServiceDirectory)
    .filter((name) => /GeminiClient|AsistenteService/.test(name));
  const assistantEndpoints = fs.readdirSync(controllersDirectory)
    .map((name) => fs.readFileSync(path.join(controllersDirectory, name), 'utf8'))
    .filter((source) => source.includes('@RequestMapping("/api/asistente")'));

  assert.match(widgetSource, /luany-asistente-historial:\$\{usuario\.id\}/);
  assert.match(widgetSource, /sessionStorage\.setItem\(historialKey/);
  assert.match(widgetSource, /apiPost\('\/api\/asistente\/consulta'/);
  assert.equal(services.filter((name) => name === 'AsistenteService.java').length, 1);
  assert.equal(services.filter((name) => name === 'GeminiClient.java').length, 1);
  assert.equal(assistantEndpoints.length, 1);
});

test('el widget envía preguntas, presenta respuesta y restaura el historial en otra pantalla', async () => {
  const entorno = crearEntornoWidget();
  const input = entorno.elementosWidget.get('#luany-asistente-input');
  const formulario = entorno.elementosWidget.get('.ai-floating-form');
  input.value = '¿Qué productos tienen stock bajo?';

  await formulario.emitir('submit', { preventDefault() {} });
  await new Promise((resolve) => setImmediate(resolve));

  assert.deepEqual(JSON.parse(JSON.stringify(entorno.llamadas)), [{
    ruta: '/api/asistente/consulta',
    cuerpo: { pregunta: '¿Qué productos tienen stock bajo?' },
  }]);
  const guardado = JSON.parse(entorno.valoresSesion.get('luany-asistente-historial:7'));
  assert.deepEqual(guardado.map((mensaje) => mensaje.tipo), ['usuario', 'asistente']);
  assert.equal(guardado[1].texto, 'Hay dos productos con stock bajo.');
  assert.equal(entorno.elementosWidget.get('.ai-floating-messages').children.length, 2);

  const otraPantalla = crearEntornoWidget(entorno.valoresSesion.get('luany-asistente-historial:7'));
  assert.equal(otraPantalla.elementosWidget.get('.ai-floating-messages').children.length, 2);
});

test('el widget muestra y conserva el error cuando falla la consulta', async () => {
  const entorno = crearEntornoWidget('[]', new Error('Gemini no está disponible'));
  const input = entorno.elementosWidget.get('#luany-asistente-input');
  input.value = 'Consulta de prueba';

  await entorno.elementosWidget.get('.ai-floating-form').emitir('submit', { preventDefault() {} });
  await new Promise((resolve) => setImmediate(resolve));

  const mensajes = JSON.parse(entorno.valoresSesion.get('luany-asistente-historial:7'));
  assert.equal(mensajes.at(-1).tipo, 'error');
  assert.equal(mensajes.at(-1).texto, 'Gemini no está disponible');
  assert.equal(entorno.elementosWidget.get('.ai-floating-status').textContent,
    'No se pudo completar la consulta.');
});

test('el botón flotante abre el panel y el control del encabezado lo minimiza', async () => {
  const entorno = crearEntornoWidget();
  const launcher = entorno.elementosWidget.get('.ai-floating-launcher');
  const panel = entorno.elementosWidget.get('.ai-floating-panel');
  const minimizar = entorno.elementosWidget.get('.ai-floating-minimize');

  await launcher.emitir('click');
  assert.equal(panel.hidden, false);
  assert.equal(launcher.getAttribute('aria-expanded'), 'true');

  await minimizar.emitir('click');
  assert.equal(panel.hidden, true);
  assert.equal(panel.getAttribute('aria-hidden'), 'true');
  assert.equal(launcher.getAttribute('aria-expanded'), 'false');
  assert.equal(entorno.document.activeElement, launcher);
});
