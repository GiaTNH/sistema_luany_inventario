const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const staticDirectory = path.resolve(__dirname, '../../backend/src/main/resources/static');

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
