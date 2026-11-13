const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const staticDirectory = path.resolve(__dirname, '../../backend/src/main/resources/static');
const readStatic = (file) => fs.readFileSync(path.join(staticDirectory, file), 'utf8');

function extraerFuncion(source, name) {
  const inicio = source.indexOf(`function ${name}(`);
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

test('el Dashboard dirige Alertas y Perfil a pantallas protegidas', () => {
  const dashboard = readStatic('dashboard.html');
  const api = readStatic('js/api.js');
  const perfil = readStatic('perfil.html');
  const dashboardJs = readStatic('js/dashboard.js');

  assert.match(dashboard, /id="dashboard-alerts-link" href="\/alertas\.html"/);
  assert.match(api, /label: 'Alertas', href: '\/alertas\.html', pagina: 'alertas\.html'/);
  assert.match(dashboard, /href="\/perfil\.html"/);
  assert.match(perfil, /cargarPaginaProtegida\('\/js\/perfil\.js'\)/);
  assert.match(dashboardJs, /if \(!Array\.isArray\(alertas\)\)/);
  assert.match(dashboardJs, /contador\.textContent = String\(alertas\.length\)/);
  assert.match(dashboard, /<option value="90">/);
  assert.match(dashboard, /<option value="365">/);
});

test('las fechas y exportaciones utilizan formatos y rutas admitidos', () => {
  const kanban = readStatic('js/pedidos-kanban.js');
  const reportes = readStatic('js/reportes.js');

  assert.doesNotMatch(kanban, /timeStyle|dateStyle/);
  assert.match(kanban, /hour: '2-digit'/);
  assert.match(reportes, /productos: 'productos-mas-vendidos'/);
  assert.match(reportes, /pedidos: 'pedidos-recepciones'/);
  assert.doesNotMatch(reportes, /dateStyle|timeStyle/);
});

test('el formulario de nota es una pantalla protegida y permite Cliente general', () => {
  const listado = readStatic('notas-venta.html');
  const nuevaNota = readStatic('nueva-nota-venta.html');
  const script = readStatic('js/notas-venta.js');

  assert.match(listado, /href="\/nueva-nota-venta\.html"/);
  assert.match(nuevaNota, /cargarPaginaProtegida\('\/js\/notas-venta\.js'\)/);
  assert.match(nuevaNota, /id="venta-cliente"/);
  assert.match(script, /Cliente general \(sin datos personales\)/);
  assert.match(script, /clienteId: selectClienteVenta\.value \? Number\(selectClienteVenta\.value\) : null/);
  assert.match(script, /cliente\.numeroDocumento\?\.trim\(\)\.toUpperCase\(\) !== 'CLIENTE-GENERAL'/);
  assert.equal((script.match(/cuerpoLineasVenta\?\.addEventListener\('click'/g) || []).length, 1);
  assert.equal((script.match(/cuerpoLineasVenta\.addEventListener\('click'/g) || []).length, 0);
  assert.match(script, /window\.print\(\)/);
});

test('el contador del Dashboard refleja el total de productos con alertas', () => {
  const dashboardJs = readStatic('js/dashboard.js');
  const contador = { textContent: '', hidden: false };
  const enlace = { setAttribute(nombre, valor) { this[nombre] = valor; } };
  const contexto = vm.createContext({
    document: {
      getElementById(id) {
        return id === 'dashboard-alert-count' ? contador : enlace;
      },
    },
  });
  vm.runInContext(extraerFuncion(dashboardJs, 'actualizarContadorAlertas'), contexto);
  vm.runInContext('actualizarContadorAlertas([{estado:"BAJO"}, {estado:"AGOTADO"}])', contexto);

  assert.equal(contador.textContent, '2');
  assert.equal(contador.hidden, false);
  assert.equal(enlace['aria-label'], '2 alertas de stock. Ver alertas');
});

test('la API marca la notificación fallida sin alterar el cuerpo de la respuesta', async () => {
  const contexto = vm.createContext({
    document: { addEventListener() {} },
    fetch: async () => ({
      ok: true,
      status: 201,
      headers: { get: (nombre) => nombre === 'X-Notification-Status' ? 'not-sent' : null },
      json: async () => ({ nombreUsuario: 'ana' }),
    }),
  });
  vm.runInContext(readStatic('js/api.js'), contexto);
  const respuesta = await vm.runInContext('apiFetch("/api/usuarios")', contexto);

  assert.equal(respuesta.notificacionFallida, true);
  assert.deepEqual(Object.keys(respuesta), ['nombreUsuario']);
});

test('el selector de venta no duplica Cliente general y conserva la opción sin cliente', async () => {
  const script = readStatic('js/notas-venta.js');
  const selector = { innerHTML: '' };
  const boton = {
    setAttribute() {},
    removeAttribute() {},
  };
  const contexto = vm.createContext({
    apiGet: async (ruta) => ruta === '/api/clientes'
      ? [
          { id: 8, nombre: 'Cliente general', numeroDocumento: 'cliente-general' },
          { id: 9, nombre: 'Ana', numeroDocumento: '12345678' },
        ]
      : [{ id: 11, nombre: 'Producto' }],
    selectClienteVenta: selector,
    document: {
      getElementById(id) {
        return id === 'venta-cliente' ? selector : boton;
      },
    },
    escapar: String,
    mostrarToast() {},
  });
  const cargarCatalogos = script.match(
    /async function cargarCatalogosVenta\(\) \{[\s\S]*?\n\}/,
  )?.[0];
  assert.ok(cargarCatalogos, 'debe existir el cargador de catálogos de venta');
  vm.runInContext(cargarCatalogos, contexto);
  await vm.runInContext('cargarCatalogosVenta()', contexto);

  assert.equal((selector.innerHTML.match(/Cliente general/g) || []).length, 1);
  assert.match(selector.innerHTML, /<option value="">Cliente general/);
  assert.doesNotMatch(selector.innerHTML, /value="8"/);
  assert.match(selector.innerHTML, /value="9"/);
});

test('una respuesta de éxito no afirma ni marca que el correo fue enviado', () => {
  const usuarios = readStatic('js/usuarios.js');
  assert.match(usuarios, /resultado\.notificacionFallida/);
  assert.match(usuarios, /La cuenta se creó correctamente\./);
  assert.doesNotMatch(usuarios, /Invitación enviada correctamente\./);
});

test('agregar, editar y quitar líneas actualiza los importes y libera productos', () => {
  const script = readStatic('js/notas-venta.js');
  const lineas = [];
  const total = { textContent: '' };

  function crearLinea() {
    const listeners = new Map();
    const select = {
      value: '',
      innerHTML: '',
      addEventListener(tipo, listener) { listeners.set(tipo, listener); },
      emitir(tipo) { listeners.get(tipo)?.(); },
    };
    const cantidad = {
      value: '1',
      addEventListener(tipo, listener) { listeners.set(tipo, listener); },
      emitir(tipo) { listeners.get(tipo)?.(); },
    };
    const campos = {
      '.venta-producto': select,
      '.venta-cantidad': cantidad,
      '.venta-stock': { textContent: '' },
      '.venta-precio': { textContent: '' },
      '.venta-subtotal': { textContent: '' },
    };
    return {
      className: '',
      querySelector(selector) { return campos[selector]; },
      remove() { lineas.splice(lineas.indexOf(this), 1); },
      select,
      cantidad,
      campos,
    };
  }

  const cuerpoLineasVenta = {
    appendChild(linea) { lineas.push(linea); },
    querySelectorAll(selector) {
      if (selector === '.venta-linea') return [...lineas];
      if (selector === '.venta-producto') return lineas.map((linea) => linea.select);
      return [];
    },
  };
  const contexto = vm.createContext({
    cuerpoLineasVenta,
    productosVenta: [
      { id: 1, nombre: 'Uno', precio: 10, stockActual: 5 },
      { id: 2, nombre: 'Dos', precio: 2, stockActual: 6 },
    ],
    document: {
      createElement() { return crearLinea(); },
      getElementById() { return total; },
    },
    escapar: String,
    formatoMoneda: (valor) => `S/ ${Number(valor).toFixed(2)}`,
    mostrarToast() {},
  });

  [
    'catalogoProducto',
    'opcionesProductoVenta',
    'actualizarLineaVenta',
    'actualizarTotalVenta',
    'agregarLineaVenta',
    'quitarLineaVenta',
  ].forEach((name) => vm.runInContext(extraerFuncion(script, name), contexto));

  vm.runInContext('agregarLineaVenta()', contexto);
  assert.equal(lineas.length, 1);
  lineas[0].select.value = '1';
  lineas[0].select.emitir('change');
  lineas[0].cantidad.value = '3';
  lineas[0].cantidad.emitir('input');
  assert.equal(lineas[0].campos['.venta-subtotal'].textContent, 'S/ 30.00');
  assert.equal(total.textContent, 'S/ 30.00');

  vm.runInContext('agregarLineaVenta()', contexto);
  lineas[1].select.value = '2';
  lineas[1].select.emitir('change');
  lineas[1].cantidad.value = '2';
  lineas[1].cantidad.emitir('input');
  assert.equal(total.textContent, 'S/ 34.00');

  const botonQuitar = {
    closest() { return lineas[0]; },
  };
  contexto.botonQuitar = botonQuitar;
  vm.runInContext('quitarLineaVenta({target:{closest:()=>botonQuitar}})', contexto);
  assert.equal(lineas.length, 1);
  assert.match(lineas[0].select.innerHTML, /value="1"/);
  assert.equal(total.textContent, 'S/ 4.00');
});

test('el detalle de pedidos ofrece subtotales sin cambiar el flujo de recepciones', () => {
  const html = readStatic('pedidos.html');
  const js = readStatic('js/pedidos.js');
  const service = fs.readFileSync(path.resolve(
    staticDirectory,
    '../../../main/java/com/luany/inventory/service/PedidoService.java',
  ), 'utf8');

  assert.match(html, /id="pedido-detalle-total"/);
  assert.match(js, /producto\.subtotal/);
  assert.match(js, /pedido\.total/);
  assert.match(service, /detalle\.getPrecio\(\)\.multiply/);
});
