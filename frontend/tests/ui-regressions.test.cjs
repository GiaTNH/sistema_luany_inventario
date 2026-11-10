const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const { TextEncoder } = require('node:util');

const staticDirectory = path.resolve(__dirname, '../../backend/src/main/resources/static');

function elementoFalso() {
  const listeners = new Map();
  const atributos = new Map();
  return {
    hidden: false,
    disabled: false,
    required: false,
    value: '',
    type: 'password',
    textContent: '',
    title: '',
    addEventListener(tipo, listener) { listeners.set(tipo, listener); },
    setAttribute(nombre, valor) { atributos.set(nombre, valor); },
    async emitir(tipo, evento = {}) {
      const listener = listeners.get(tipo);
      if (listener) await listener(evento);
    },
  };
}

function cargarApiConModal(document, modal) {
  const listeners = new Map();
  document.addEventListener = (type, listener) => listeners.set(type, listener);
  const contexto = vm.createContext({
    document,
    requestAnimationFrame: (callback) => callback(),
  });
  const source = fs.readFileSync(path.join(staticDirectory, 'js/api.js'), 'utf8');
  vm.runInContext(source, contexto);
  return {
    abrir: vm.runInContext('abrirModalAccesible', contexto),
    cerrar: vm.runInContext('cerrarModalAccesible', contexto),
    document,
    keydown: listeners.get('keydown'),
    modal,
  };
}

function elementoEnfocable(document) {
  return {
    hidden: false,
    focus() {
      document.activeElement = this;
    },
    getAttribute() {
      return null;
    },
  };
}

function modalFalso(document, elementos) {
  const atributos = new Map();
  const clases = new Set();
  const titulo = { textContent: 'Diálogo de prueba' };
  return {
    hidden: true,
    isConnected: true,
    classList: {
      add: (clase) => clases.add(clase),
      remove: (clase) => clases.delete(clase),
    },
    contains: (elemento) => elementos.includes(elemento),
    focus() {
      document.activeElement = this;
    },
    getAttribute: (nombre) => atributos.get(nombre) ?? null,
    hasAttribute: (nombre) => atributos.has(nombre),
    querySelector: (selector) => selector.startsWith('h1, h2') ? titulo : elementos[0] ?? null,
    querySelectorAll: () => elementos,
    setAttribute: (nombre, valor) => atributos.set(nombre, valor),
    removeAttribute: (nombre) => atributos.delete(nombre),
    atributos,
    clases,
  };
}

test('abrir y cerrar modal gestiona visibilidad, semántica y retorno del foco', () => {
  const opener = { isConnected: true, focus() { this.focused = true; } };
  const document = { activeElement: opener };
  const primerElemento = elementoEnfocable(document);
  const modal = modalFalso(document, [primerElemento]);
  const helpers = cargarApiConModal(document, modal);

  helpers.abrir(modal);

  assert.equal(modal.hidden, false);
  assert.equal(modal.getAttribute('role'), 'dialog');
  assert.equal(modal.getAttribute('aria-modal'), 'true');
  assert.equal(modal.getAttribute('aria-hidden'), 'false');
  assert.equal(document.activeElement, primerElemento);

  helpers.cerrar(modal);

  assert.equal(modal.hidden, true);
  assert.equal(modal.getAttribute('aria-hidden'), 'true');
  assert.equal(opener.focused, true);
});

test('Tab y Shift+Tab permanecen dentro del modal', () => {
  const opener = { isConnected: true, focus() {} };
  const document = { activeElement: null };
  const primero = elementoEnfocable(document);
  const ultimo = elementoEnfocable(document);
  const modal = modalFalso(document, [primero, ultimo]);
  const helpers = cargarApiConModal(document, modal);
  helpers.abrir(modal);

  document.activeElement = ultimo;
  const eventoTab = { key: 'Tab', shiftKey: false, preventDefault() { this.prevenido = true; } };
  helpers.keydown(eventoTab);
  assert.equal(eventoTab.prevenido, true);
  assert.equal(document.activeElement, primero);

  document.activeElement = primero;
  const eventoShiftTab = { key: 'Tab', shiftKey: true, preventDefault() { this.prevenido = true; } };
  helpers.keydown(eventoShiftTab);
  assert.equal(eventoShiftTab.prevenido, true);
  assert.equal(document.activeElement, ultimo);
});

test('las acciones de productos y categorías no interpolan nombres en código inline', () => {
  const source = fs.readFileSync(path.join(staticDirectory, 'js/productos.js'), 'utf8');
  assert.match(source, /data-product-action="history"/);
  assert.match(source, /data-category-action="edit"/);
  assert.doesNotMatch(source, /onclick="[^"]*\$\{nombre\}/);
});

test('el cambio obligatorio conserva la sesión y redirige al dashboard sin almacenar la contraseña', async () => {
  const ids = [
    'form-cambiar-password',
    'nueva-password',
    'confirmar-password',
    'password-actual',
    'password-actual-field',
    'password-error',
    'btn-cambiar-password',
    'password-actual-toggle',
    'nueva-password-toggle',
    'confirmar-password-toggle',
  ];
  const elementos = new Map(ids.map((id) => [id, elementoFalso()]));
  elementos.get('nueva-password').value = 'NuevaClaveSegura2026!';
  elementos.get('confirmar-password').value = 'NuevaClaveSegura2026!';
  const respuestaSesion = {
    id: 8,
    nombre: 'Almacén',
    email: 'almacen@luany.test',
    rol: 'EMPLEADO',
    debeCambiarPassword: false,
  };
  const peticiones = [];
  const sesionesGuardadas = [];
  const redirecciones = [];
  const contexto = vm.createContext({
    document: { getElementById: (id) => elementos.get(id) },
    obtenerSesion: () => ({ debeCambiarPassword: true }),
    apiPost: async (ruta, cuerpo) => {
      peticiones.push({ ruta, cuerpo });
      return respuestaSesion;
    },
    guardarSesion: (sesion) => sesionesGuardadas.push(sesion),
    window: { location: { replace: (destino) => redirecciones.push(destino) } },
    TextEncoder,
  });
  const fuente = fs.readFileSync(path.join(staticDirectory, 'js/cambiar-password.js'), 'utf8');
  vm.runInContext(fuente, contexto);
  await elementos.get('form-cambiar-password').emitir('submit', { preventDefault() {} });

  assert.deepEqual(JSON.parse(JSON.stringify(peticiones)), [{
    ruta: '/api/auth/cambiar-password',
    cuerpo: {
      passwordActual: null,
      nuevaPassword: 'NuevaClaveSegura2026!',
      confirmarPassword: 'NuevaClaveSegura2026!',
    },
  }]);
  assert.deepEqual(sesionesGuardadas, [respuestaSesion]);
  assert.equal(JSON.stringify(sesionesGuardadas).includes('NuevaClaveSegura2026!'), false);
  assert.deepEqual(redirecciones, ['/dashboard.html?passwordChanged=1']);
});

test('el dashboard consume el aviso de contraseña una sola vez y elimina su indicador de la URL', () => {
  const source = fs.readFileSync(path.join(staticDirectory, 'js/dashboard.js'), 'utf8');
  const funcion = source.match(/function notificarCambioPassword\(\) \{[\s\S]*?\n\}/)?.[0];
  assert.ok(funcion, 'debe existir el handler del aviso de contraseña');
  let urlActual = 'https://luany.test/dashboard.html?passwordChanged=1&vista=general#alertas';
  const notificaciones = [];
  const contexto = vm.createContext({
    URL,
    window: {
      location: { get href() { return urlActual; } },
      history: {
        state: null,
        replaceState(_state, _title, url) {
          urlActual = new URL(url, urlActual).toString();
        },
      },
    },
    mostrarToast: (...argumentos) => notificaciones.push(argumentos),
  });
  vm.runInContext(funcion, contexto);
  const notificar = vm.runInContext('notificarCambioPassword', contexto);
  notificar();
  notificar();

  assert.equal(urlActual, 'https://luany.test/dashboard.html?vista=general#alertas');
  assert.deepEqual(JSON.parse(JSON.stringify(notificaciones)), [[
    'Tu contraseña se actualizó correctamente. Recuerda utilizar tu nueva contraseña en tus próximos inicios de sesión.',
    'exito',
    7000,
  ]]);
});

test('la interfaz muestra Almacén sin alterar el valor interno EMPLEADO', () => {
  const apiSource = fs.readFileSync(path.join(staticDirectory, 'js/api.js'), 'utf8');
  const usersSource = fs.readFileSync(path.join(staticDirectory, 'js/usuarios.js'), 'utf8');
  const usersHtml = fs.readFileSync(path.join(staticDirectory, 'usuarios.html'), 'utf8');
  assert.match(apiSource, /EMPLEADO:\s*'Almacén'/);
  assert.match(usersSource, /nombreVisibleRol\(usuario\.rol\)/);
  assert.match(usersHtml, /<option value="EMPLEADO">Almacén<\/option>/);
  assert.match(usersHtml, /<option value="EMPLEADO">Personal de almacén<\/option>/);
  assert.doesNotMatch(usersHtml, /<option value="ALMACEN">/);
});
