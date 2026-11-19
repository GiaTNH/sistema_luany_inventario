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
      text: async () => JSON.stringify(usuario),
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
  const accesoAsistente = elemento('.sidebar-assistant-access');
  accesoAsistente.setAttribute('aria-expanded', 'false');
  const document = { activeElement: null };
  document.addEventListener = () => {};
  document.createTextNode = (texto) => ({ nodeType: 3, textContent: texto });
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
      get childElementCount() {
        return this.children.filter((hijo) => hijo.nodeType !== 3).length;
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
  document.querySelector = (selector) => (
    selector === '.sidebar-assistant-access' ? accesoAsistente : null
  );
  document.body = documentBody;

  const llamadas = [];
  const documentMarkdown = {
    addEventListener() {},
    createElement: (etiqueta) => elemento(etiqueta),
    createTextNode: (texto) => ({ nodeType: 3, textContent: texto }),
  };
  const utilidadesContexto = vm.createContext({
    document: documentMarkdown,
    window: { addEventListener() {} },
  });
  vm.runInContext(apiSource, utilidadesContexto);
  const contexto = vm.createContext({
    document,
    window: { location: { pathname: '/' }, addEventListener() {} },
    crearTablasAsistente: vm.runInContext('crearTablasAsistente', utilidadesContexto),
    renderizarMarkdownAsistente: vm.runInContext('renderizarMarkdownAsistente', utilidadesContexto),
    obtenerSesion: () => ({ id: 7 }),
    sessionStorage: {
      getItem: (clave) => valoresSesion.get(clave) ?? null,
      setItem: (clave, valor) => valoresSesion.set(clave, valor),
    },
    apiPost: async (ruta, cuerpo) => {
      llamadas.push({ ruta, cuerpo });
      if (respuestaApi instanceof Error) throw respuestaApi;
      if (typeof respuestaApi === 'function') return respuestaApi(ruta, cuerpo);
      return respuestaApi;
    },
    console,
  });
  const widgetSource = fs.readFileSync(
    path.join(staticDirectory, 'js/asistente-flotante.js'),
    'utf8',
  );
  vm.runInContext(widgetSource, contexto);
  return { document, elementosWidget, llamadas, valoresSesion, widget, accesoAsistente };
}

const usuarioEmpleado = {
  id: 7,
  nombre: 'Luany Empleada',
  email: 'empleada@luany.test',
  rol: 'EMPLEADO',
  debeCambiarPassword: false,
};

test('el javascript de la página se carga solo después de validar la sesión', async () => {
  const entorno = crearEntorno(usuarioEmpleado);

  await entorno.cargarPaginaProtegida('/js/productos.js');

  assert.deepEqual(entorno.scripts.map((script) => script.src), ['/js/productos.js']);
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
    'alertas.html',
    'asistente.html',
    'clientes.html',
    'dashboard.html',
    'movimientos.html',
    'nueva-nota-venta.html',
    'notas-venta.html',
    'pedidos-kanban.html',
    'pedidos.html',
    'productos.html',
    'perfil.html',
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
  assert.match(widgetSource, /document\.querySelector\('\.sidebar-assistant-access'\)/);
  assert.doesNotMatch(widgetSource, /class="ai-floating-launcher"/);
  assert.match(widgetSource, /href="\/asistente\.html"/);
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

test('el widget confirma una sugerencia enviando el ID del producto y la pregunta original', async () => {
  const entorno = crearEntornoWidget('[]', async (ruta, cuerpo) => cuerpo.productoIdConfirmado
    ? { tipoConsulta: 'MOVIMIENTOS', productosEnContexto: 1, tiempoMs: 8, respuesta: 'Movimientos consultados.' }
    : {
      tipoConsulta: 'MOVIMIENTOS',
      productosEnContexto: 0,
      tiempoMs: 4,
      respuesta: '¿Te refieres a Biberón?',
      opcionesProducto: [{ id: 21, nombre: 'Biberón' }],
    });
  const input = entorno.elementosWidget.get('#luany-asistente-input');
  input.value = '¿Qué movimientos ha tenido bibern?';

  await entorno.elementosWidget.get('.ai-floating-form').emitir('submit', { preventDefault() {} });
  await new Promise((resolve) => setImmediate(resolve));
  const mensajeSugerencia = entorno.elementosWidget.get('.ai-floating-messages').children[1];
  await mensajeSugerencia.children.at(-1).children[0].emitir('click');
  await new Promise((resolve) => setImmediate(resolve));

  assert.deepEqual(JSON.parse(JSON.stringify(entorno.llamadas[1].cuerpo)), {
    pregunta: '¿Qué movimientos ha tenido bibern?',
    productoIdConfirmado: 21,
    cancelarConfirmacion: false,
  });
});

test('el widget cancela una sugerencia sin enviar una consulta de movimientos', async () => {
  const entorno = crearEntornoWidget('[]', async (ruta, cuerpo) => ({
    tipoConsulta: 'MOVIMIENTOS',
    productosEnContexto: 0,
    tiempoMs: 4,
    respuesta: cuerpo.cancelarConfirmacion ? 'Entendido.' : '¿Te refieres a Biberón?',
    opcionesProducto: cuerpo.cancelarConfirmacion ? [] : [{ id: 21, nombre: 'Biberón' }],
  }));
  const input = entorno.elementosWidget.get('#luany-asistente-input');
  input.value = '¿Qué movimientos ha tenido bibern?';
  await entorno.elementosWidget.get('.ai-floating-form').emitir('submit', { preventDefault() {} });
  await new Promise((resolve) => setImmediate(resolve));
  const mensajeSugerencia = entorno.elementosWidget.get('.ai-floating-messages').children[1];
  await mensajeSugerencia.children.at(-1).children[1].emitir('click');
  await new Promise((resolve) => setImmediate(resolve));

  assert.deepEqual(JSON.parse(JSON.stringify(entorno.llamadas[1].cuerpo)), {
    pregunta: '¿Qué movimientos ha tenido bibern?',
    cancelarConfirmacion: true,
  });
});

test('el widget evita repetir respuestas tabulares guardadas antes de la corrección', () => {
  const historial = JSON.stringify([
    { tipo: 'usuario', texto: '¿Qué productos tienen stock bajo?' },
    {
      tipo: 'asistente',
      texto: 'Blusa Luna tiene stock bajo.',
      tablas: [{ titulo: 'Productos con stock bajo', columnas: ['Producto'], filas: [['Blusa Luna']] }],
    },
  ]);
  const entorno = crearEntornoWidget(historial);
  const respuesta = entorno.elementosWidget.get('.ai-floating-messages').children[1].children[0];

  assert.equal(respuesta.children[0].textContent,
    'Encontré información en 1 tablas. Te muestro los resultados.');
});

test('el widget conserva todas las tablas recibidas y renderiza Markdown como elementos seguros', async () => {
  const respuestaApi = {
    tipoConsulta: 'STOCK_BAJO',
    productosEnContexto: 1,
    tiempoMs: 120,
    respuesta: '**Encontré 1 registro.** <img src=x onerror=alert(1)>',
    tablas: [{
      tipo: 'STOCK_BAJO',
      titulo: 'Productos con stock bajo',
      total: 1,
      mostrados: 1,
      columnas: ['Producto', 'Stock actual'],
      filas: [['Blusa Luna', 2]],
    }, {
      tipo: 'MOVIMIENTOS',
      titulo: 'Movimientos registrados',
      total: 0,
      mostrados: 0,
      columnas: ['Fecha y hora'],
      filas: [],
    }],
  };
  const entorno = crearEntornoWidget('[]', respuestaApi);
  const input = entorno.elementosWidget.get('#luany-asistente-input');
  input.value = 'Consulta con varias tablas';

  await entorno.elementosWidget.get('.ai-floating-form').emitir('submit', { preventDefault() {} });
  await new Promise((resolve) => setImmediate(resolve));

  const mensaje = entorno.elementosWidget.get('.ai-floating-messages').children[1];
  const burbuja = mensaje.children[0];
  assert.equal(burbuja.children[0].etiqueta, 'strong');
  assert.equal(burbuja.children[0].textContent, 'Encontré 1 registro.');
  assert.equal(burbuja.children[1].textContent, ' <img src=x onerror=alert(1)>');
  assert.equal(mensaje.children[1].children.length, 2);
  assert.equal(JSON.parse(entorno.valoresSesion.get('luany-asistente-historial:7'))[1].tablas.length, 2);
});

test('el renderizador muestra Markdown en negrita, cursiva y código sin interpretar HTML', () => {
  const nodos = [];
  function crearElemento(etiqueta) {
    return {
      etiqueta,
      children: [],
      appendChild(nodo) {
        this.children.push(nodo);
      },
      set textContent(texto) {
        this.texto = texto;
      },
    };
  }
  const contexto = vm.createContext({
    document: {
      addEventListener() {},
      createElement: crearElemento,
      createTextNode: (texto) => ({ nodeType: 3, textContent: texto }),
    },
    window: { addEventListener() {} },
  });
  vm.runInContext(apiSource, contexto);
  const contenedor = crearElemento('div');
  const renderizar = vm.runInContext('renderizarMarkdownAsistente', contexto);

  renderizar(contenedor, '**Negrita** y *cursiva* con `código` <img src=x onerror=alert(1)>');
  nodos.push(...contenedor.children);

  assert.deepEqual(nodos.filter((nodo) => nodo.etiqueta).map((nodo) => nodo.etiqueta),
    ['strong', 'em', 'code']);
  assert.equal(nodos.at(-1).textContent, ' <img src=x onerror=alert(1)>');
});

test('el asistente principal usa el renderizador Markdown compartido', () => {
  const source = fs.readFileSync(path.join(staticDirectory, 'js/asistente.js'), 'utf8');

  assert.match(source, /renderizarMarkdownAsistente\(burbuja,/);
  assert.match(source, /crearTablasAsistente\(tablas\)/);
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

test('el acceso discreto del sidebar abre el panel y el control del encabezado lo minimiza', async () => {
  const entorno = crearEntornoWidget();
  const launcher = entorno.accesoAsistente;
  const panel = entorno.elementosWidget.get('.ai-floating-panel');
  const minimizar = entorno.elementosWidget.get('.ai-floating-minimize');

  assert.equal(entorno.elementosWidget.has('.ai-floating-launcher'), false);
  await launcher.emitir('click');
  assert.equal(panel.hidden, false);
  assert.equal(launcher.getAttribute('aria-expanded'), 'true');

  await minimizar.emitir('click');
  assert.equal(panel.hidden, true);
  assert.equal(panel.getAttribute('aria-hidden'), 'true');
  assert.equal(launcher.getAttribute('aria-expanded'), 'false');
  assert.equal(entorno.document.activeElement, launcher);
});
