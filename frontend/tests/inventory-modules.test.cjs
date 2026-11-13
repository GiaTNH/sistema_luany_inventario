const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const staticDirectory = path.resolve(__dirname, '../../backend/src/main/resources/static');
const readStatic = (file) => fs.readFileSync(path.join(staticDirectory, file), 'utf8');

function extraerFuncion(source, name) {
  const normal = source.indexOf(`function ${name}(`);
  const async = source.indexOf(`async function ${name}(`);
  const inicio = async >= 0 && (normal < 0 || async < normal) ? async : normal;
  assert.notEqual(inicio, -1, `debe existir la función ${name}`);
  const apertura = source.indexOf('{', inicio);
  let profundidad = 0;
  for (let indice = apertura; indice < source.length; indice += 1) {
    if (source[indice] === '{') profundidad += 1;
    if (source[indice] === '}') profundidad -= 1;
    if (profundidad === 0) return source.slice(inicio, indice + 1);
  }
  assert.fail(`la función ${name} debe estar completa`);
}

function campo(value = '') {
  return { value, textContent: '', innerHTML: '', disabled: false };
}

test('Productos busca por nombre o código y combina el filtro de proveedor', () => {
  const source = readStatic('js/productos.js');
  const campos = {
    buscador: campo('BD-02'),
    'filtro-categoria': campo(''),
    'filtro-estado': campo(''),
    'filtro-proveedor': campo('8'),
  };
  let renderizados = [];
  const contexto = vm.createContext({
    document: { getElementById: (id) => campos[id] },
    todosProductos: [
      { id: 1, nombre: 'Body verde', codigo: 'BD-01', proveedor: { id: 8 }, stockActual: 5, stockMinimo: 1, stockMaximo: 10 },
      { id: 2, nombre: 'Pijama azul', codigo: 'BD-02', proveedor: { id: 8 }, stockActual: 5, stockMinimo: 1, stockMaximo: 10 },
      { id: 3, nombre: 'Pijama roja', codigo: 'BD-02', proveedor: { id: 9 }, stockActual: 5, stockMinimo: 1, stockMaximo: 10 },
    ],
    calcularEstado: () => 'NORMAL',
    aplicarOrdenProductos: (productos) => productos,
    renderTablaProductos: (productos) => { renderizados = [...productos]; },
  });
  vm.runInContext(extraerFuncion(source, 'aplicarFiltros'), contexto);

  vm.runInContext('aplicarFiltros()', contexto);
  assert.deepEqual(renderizados.map((producto) => producto.id), [2]);

  campos.buscador.value = 'body';
  campos['filtro-proveedor'].value = '';
  vm.runInContext('aplicarFiltros()', contexto);
  assert.deepEqual(renderizados.map((producto) => producto.id), [1]);
});

test('Proveedores filtra por nombre o RUC y combina el estado seleccionado', () => {
  const source = readStatic('js/proveedores.js');
  const campos = {
    'filtro-proveedor-busqueda': campo('20123456789'),
    'filtro-proveedor-estado': campo('ACTIVO'),
  };
  let renderizados = [];
  const contexto = vm.createContext({
    document: { getElementById: (id) => campos[id] },
    todosProveedores: [
      { id: 1, nombre: 'Proveedor Lima', ruc: '20123456789', estado: 'ACTIVO' },
      { id: 2, nombre: 'Proveedor Lima', ruc: '20987654321', estado: 'INACTIVO' },
    ],
    renderizarProveedores: (proveedores) => { renderizados = [...proveedores]; },
  });
  vm.runInContext(extraerFuncion(source, 'aplicarFiltrosProveedores'), contexto);

  vm.runInContext('aplicarFiltrosProveedores()', contexto);
  assert.deepEqual(renderizados.map((proveedor) => proveedor.id), [1]);

  campos['filtro-proveedor-busqueda'].value = 'proveedor lima';
  campos['filtro-proveedor-estado'].value = 'INACTIVO';
  vm.runInContext('aplicarFiltrosProveedores()', contexto);
  assert.deepEqual(renderizados.map((proveedor) => proveedor.id), [2]);
});

test('Movimientos consulta filtros de producto, tipo, fechas y página al backend', async () => {
  const source = readStatic('js/movimientos.js');
  const campos = {
    'tabla-movimientos-body': campo(),
    'movimientos-mensaje': campo(),
    'filtro-movimiento-producto': campo('12'),
    'filtro-movimiento-tipo': campo('VENTA'),
    'filtro-movimiento-desde': campo('2026-11-01'),
    'filtro-movimiento-hasta': campo('2026-11-09'),
    'btn-movimientos-anterior': campo(),
    'btn-movimientos-siguiente': campo(),
    'movimientos-pagina-info': campo(),
  };
  let urlSolicitada = '';
  const contexto = vm.createContext({
    document: { getElementById: (id) => campos[id] },
    URLSearchParams,
    apiGet: async (url) => {
      urlSolicitada = url;
      return { content: [], number: 2, totalPages: 4, totalElements: 67 };
    },
    mostrarToast: () => {},
  });
  vm.runInContext(`
    let movimientos = [];
    let paginaMovimientos = 2;
    let totalPaginasMovimientos = 0;
    let totalElementosMovimientos = 0;
    let solicitudMovimientos = 0;
    const tamanoPaginaMovimientos = 20;
  `, contexto);
  vm.runInContext(extraerFuncion(source, 'actualizarPaginacionMovimientos'), contexto);
  vm.runInContext(extraerFuncion(source, 'renderizarMovimientos'), contexto);
  vm.runInContext(extraerFuncion(source, 'cargarMovimientos'), contexto);

  await vm.runInContext('cargarMovimientos()', contexto);
  const url = new URL(urlSolicitada, 'http://localhost');
  assert.equal(url.pathname, '/api/movimientos/pagina');
  assert.equal(url.searchParams.get('productoId'), '12');
  assert.equal(url.searchParams.get('tipo'), 'VENTA');
  assert.equal(url.searchParams.get('desde'), '2026-11-01');
  assert.equal(url.searchParams.get('hasta'), '2026-11-09');
  assert.equal(url.searchParams.get('pagina'), '2');
  assert.equal(url.searchParams.get('tamano'), '20');
  assert.equal(campos['movimientos-pagina-info'].textContent, 'Página 3 de 4');
  assert.equal(campos['btn-movimientos-anterior'].disabled, false);
  assert.equal(campos['btn-movimientos-siguiente'].disabled, false);
});

test('Movimientos invalida el rango y limpia datos e indicadores de paginación anteriores', async () => {
  const source = readStatic('js/movimientos.js');
  const campos = {
    'tabla-movimientos-body': campo(),
    'movimientos-mensaje': campo(),
    'filtro-movimiento-producto': campo(''),
    'filtro-movimiento-tipo': campo(''),
    'filtro-movimiento-desde': campo('2026-11-10'),
    'filtro-movimiento-hasta': campo('2026-11-01'),
    'btn-movimientos-anterior': campo(),
    'btn-movimientos-siguiente': campo(),
    'movimientos-pagina-info': campo(),
  };
  let consultas = 0;
  const contexto = vm.createContext({
    document: { getElementById: (id) => campos[id] },
    apiGet: async () => { consultas += 1; return {}; },
    mostrarToast: () => {},
  });
  vm.runInContext(`
    let movimientos = [{ id: 99 }];
    let paginaMovimientos = 3;
    let totalPaginasMovimientos = 8;
    let totalElementosMovimientos = 151;
    let solicitudMovimientos = 0;
    const tamanoPaginaMovimientos = 20;
  `, contexto);
  vm.runInContext(extraerFuncion(source, 'actualizarPaginacionMovimientos'), contexto);
  vm.runInContext(extraerFuncion(source, 'cargarMovimientos'), contexto);

  await vm.runInContext('cargarMovimientos()', contexto);
  assert.equal(consultas, 0);
  assert.equal(campos['movimientos-mensaje'].textContent, 'La fecha inicial no puede ser posterior a la fecha final.');
  assert.match(campos['tabla-movimientos-body'].innerHTML, /Ajusta el rango de fechas/);
  assert.equal(vm.runInContext('paginaMovimientos', contexto), 0);
  assert.equal(vm.runInContext('totalPaginasMovimientos', contexto), 0);
  assert.equal(vm.runInContext('totalElementosMovimientos', contexto), 0);
  assert.deepEqual(Array.from(vm.runInContext('movimientos', contexto)), []);
  assert.equal(campos['movimientos-pagina-info'].textContent, 'Página 1 de 1');
  assert.equal(campos['btn-movimientos-anterior'].disabled, true);
  assert.equal(campos['btn-movimientos-siguiente'].disabled, true);
});

test('Modal de eliminación de proveedor abre con foco, expone semántica y cierra restaurando el foco', () => {
  const source = readStatic('js/proveedores.js');
  const html = readStatic('proveedores.html');
  assert.match(html, /id="modal-confirmar-proveedor"[^>]*role="alertdialog"/);
  assert.match(html, /aria-labelledby="titulo-confirmar-proveedor"/);
  assert.match(html, /aria-describedby="confirmar-mensaje-proveedor"/);

  const attributes = new Map([['role', 'alertdialog']]);
  const classes = new Set();
  const opener = { isConnected: true, focused: false, focus() { this.focused = true; } };
  const document = { activeElement: opener };
  const button = {
    disabled: false,
    focus() { document.activeElement = this; },
    getAttribute: () => null,
  };
  const modal = {
    hidden: true,
    classList: {
      add: (value) => classes.add(value),
      remove: (value) => classes.delete(value),
      contains: (value) => classes.has(value),
    },
    getAttribute: (name) => attributes.get(name) ?? null,
    hasAttribute: (name) => attributes.has(name),
    setAttribute: (name, value) => attributes.set(name, value),
    querySelector: () => ({ textContent: 'Eliminar proveedor' }),
    querySelectorAll: () => [button],
    contains: (element) => element === button,
    focus() { document.activeElement = this; },
  };
  const mensaje = campo();
  const elements = {
    'confirmar-mensaje-proveedor': mensaje,
    'btn-confirmar-proveedor': button,
    'modal-confirmar-proveedor': modal,
  };
  const contexto = vm.createContext({
    document: {
      get activeElement() { return document.activeElement; },
      set activeElement(value) { document.activeElement = value; },
      getElementById: (id) => elements[id],
    },
    requestAnimationFrame: (callback) => callback(),
    todosProveedores: [{ id: 4, nombre: 'Proveedor de prueba' }],
    abrirModalAccesible(elemento) {
      elemento.hidden = false;
      elemento.setAttribute('aria-modal', 'true');
      elemento.setAttribute('aria-hidden', 'false');
      elemento.classList.add('visible');
      button.focus();
    },
    cerrarModalAccesible(elemento) {
      elemento.hidden = true;
      elemento.setAttribute('aria-hidden', 'true');
      elemento.classList.remove('visible');
      opener.focus();
    },
  });
  vm.runInContext(extraerFuncion(source, 'confirmarEliminarProveedor'), contexto);
  vm.runInContext(extraerFuncion(source, 'cerrarModalProveedor'), contexto);

  vm.runInContext('confirmarEliminarProveedor(4)', contexto);
  assert.match(mensaje.textContent, /Proveedor de prueba/);
  assert.equal(modal.hidden, false);
  assert.equal(attributes.get('aria-modal'), 'true');
  assert.equal(attributes.get('aria-hidden'), 'false');
  assert.equal(document.activeElement, button);

  vm.runInContext("cerrarModalProveedor('modal-confirmar-proveedor')", contexto);
  assert.equal(modal.hidden, true);
  assert.equal(attributes.get('aria-hidden'), 'true');
  assert.equal(opener.focused, true);
});

test('Las tablas de productos, proveedores y movimientos definen su presentación móvil', () => {
  const estilos = readStatic('css/styles.css');
  assert.match(estilos, /@media \(max-width: 760px\)[\s\S]*?productos-product-table thead \{ display: none; \}/);
  assert.match(estilos, /proveedores-page \.productos-provider-table thead \{ display: none; \}/);
  assert.match(estilos, /movimientos-module-page \.movimientos-table thead \{ display: none; \}/);
  assert.match(readStatic('productos.html'), /id="filtro-proveedor"/);
  assert.match(readStatic('proveedores.html'), /id="filtro-proveedor-busqueda"/);
  assert.match(readStatic('proveedores.html'), /id="filtro-proveedor-estado"/);
  assert.match(readStatic('movimientos.html'), /id="filtro-movimiento-tipo"/);
});
