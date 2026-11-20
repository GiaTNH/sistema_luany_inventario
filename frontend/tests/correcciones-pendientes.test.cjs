const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const staticDirectory = path.resolve(__dirname, '../../backend/src/main/resources/static');
const readStatic = (file) => fs.readFileSync(path.join(staticDirectory, file), 'utf8');

function extraerFuncion(source, name) {
  const inicioRegular = source.indexOf(`function ${name}(`);
  const inicioAsync = source.indexOf(`async function ${name}(`);
  const inicio = inicioAsync >= 0 && (inicioRegular < 0 || inicioAsync < inicioRegular)
    ? inicioAsync : inicioRegular;
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
  assert.match(dashboard, /<option value="15">Últimos 15 días<\/option>/);
  assert.match(dashboard, /<option value="6m">Últimos 6 meses<\/option>/);
  assert.match(dashboard, /<option value="1y">Último año<\/option>/);
});

test('el usuario del navbar abre Perfil por clic y teclado para ambos roles', () => {
  const api = readStatic('js/api.js');
  const habilitar = extraerFuncion(api, 'habilitarAccesoPerfil');
  for (const rol of ['ADMIN', 'EMPLEADO']) {
    const listeners = new Map();
    const usuario = {
      attributes: {},
      setAttribute(nombre, valor) { this.attributes[nombre] = valor; },
      addEventListener(tipo, listener) { listeners.set(tipo, listener); },
    };
    const contexto = vm.createContext({
      document: { querySelectorAll: (selector) => selector === '.sidebar-usuario' ? [usuario] : [] },
      window: { location: { href: '' } },
    });
    vm.runInContext(habilitar, contexto);
    vm.runInContext('habilitarAccesoPerfil()', contexto);

    assert.equal(usuario.attributes.role, 'link', rol);
    assert.equal(usuario.tabIndex, 0, rol);
    const evento = (tipo, extra = {}) => ({
      type: tipo,
      key: extra.key,
      target: { closest: () => extra.interactivo ? {} : null },
      preventDefault() { this.prevenido = true; },
    });
    listeners.get('click')(evento('click'));
    assert.equal(contexto.window.location.href, '/perfil.html', rol);
    contexto.window.location.href = '';
    const enter = evento('keydown', { key: 'Enter' });
    listeners.get('keydown')(enter);
    assert.equal(contexto.window.location.href, '/perfil.html', rol);
    assert.equal(enter.prevenido, true);
    contexto.window.location.href = '';
    listeners.get('click')(evento('click', { interactivo: true }));
    assert.equal(contexto.window.location.href, '', 'no debe interceptar botones o enlaces internos');
  }
});

test('el asistente y Kanban usan enlaces compartidos y Kanban sigue a Dashboard', () => {
  const api = readStatic('js/api.js');
  const dashboard = api.indexOf("label: 'Dashboard'");
  const kanban = api.indexOf("label: 'Tablero Kanban'");
  const productos = api.indexOf("label: 'Productos'");
  assert.ok(dashboard >= 0 && kanban > dashboard && productos > kanban);
  assert.match(api, /label: 'Asistente IA', href: '\/asistente\.html', pagina: 'asistente\.html'/);
  assert.match(api, /link\.className = 'nav-item'/);
  assert.match(api, /nav\.replaceChildren\(titulo, \.\.\.enlaces\)/);
  assert.doesNotMatch(api, /sidebar-assistant-access|cargarAsistenteFlotante/);
});

test('el logo oficial conserva su proporción en acceso y notas, y el navbar usa la bolsa', () => {
  const logo = path.join(staticDirectory, 'img/logo_luany.png');
  assert.ok(fs.existsSync(logo), 'debe estar disponible el recurso oficial');
  const api = readStatic('js/api.js');
  const index = readStatic('index.html');
  const cambiarPassword = readStatic('cambiar-password.html');
  const estilos = readStatic('css/styles.css');
  const nota = readStatic('js/notas-venta.js');
  const etiquetas = [
    ['inicio de sesión, recuperación y registro', index],
    ['cambio de contraseña', cambiarPassword],
  ];
  for (const [pantalla, contenido] of etiquetas) {
    assert.match(contenido, /src="\/img\/logo_luany\.png"/, pantalla);
    assert.match(contenido, /class="login-brand-bag" src="\/img\/logo-bolsa\.png"/, pantalla);
  }
  assert.match(estilos, /\.login-art-logo-oficial[^}]*width:\s*280px[^}]*height:\s*auto[^}]*object-fit:\s*contain/);
  assert.match(estilos, /\.login-mobile-brand \.login-brand-wordmark[^}]*height:\s*auto[^}]*object-fit:\s*contain/);
  assert.match(nota, /src="\/img\/logo_luany\.png"/);
  assert.match(nota, /class="nota-venta-bolsa" src="\/img\/logo-bolsa\.png"/);
  assert.match(readStatic('js/asistente-flotante.js'), /class="ai-floating-brand-bag" src="\/img\/logo-bolsa\.png"/);
  assert.match(estilos, /\.nota-venta-logo[^}]*object-fit:\s*contain/);
  assert.doesNotMatch(api, /logo\.src = '\/img\/logo_luany\.png'/);
  assert.match(api, /nombre\.textContent = 'LUANY'/);
  assert.match(api, /eslogan\.textContent = 'juega\. viste\. crece\.'/);
  assert.match(readStatic('dashboard.html'), /<img src="\/img\/logo-bolsa\.png"/);
  assert.match(readStatic('dashboard.html'), /<span class="sidebar-brand-name">Luany<\/span>/);

  const paginasConSidebar = fs.readdirSync(staticDirectory)
    .filter((archivo) => archivo.endsWith('.html') && archivo !== 'index.html' && archivo !== 'cambiar-password.html')
    .map((archivo) => [archivo, readStatic(archivo)])
    .filter(([, contenido]) => contenido.includes('class="sidebar-brand"'));
  assert.ok(paginasConSidebar.length > 0);
  for (const [pagina, contenido] of paginasConSidebar) {
    assert.match(contenido, /class="sidebar-brand-bag"[\s\S]*?src="\/img\/logo-bolsa\.png"[\s\S]*?class="sidebar-brand-copy"[\s\S]*?class="sidebar-brand-name"/, pagina);
  }

  const nombre = { textContent: '' };
  const tagline = { textContent: '' };
  const location = { hidden: false };
  const contexto = vm.createContext({
    document: {
      querySelectorAll(selector) {
        return selector === '.sidebar-brand-name' ? [nombre]
          : selector === '.sidebar-brand-tagline' ? [tagline]
          : selector === '.sidebar-location' ? [location] : [];
      },
    },
  });
  vm.runInContext(extraerFuncion(api, 'configurarMarcaSidebar'), contexto);
  vm.runInContext('configurarMarcaSidebar()', contexto);
  assert.equal(nombre.textContent, 'LUANY');
  assert.equal(tagline.textContent, 'juega. viste. crece.');
  assert.equal(location.hidden, true);
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
      text: async () => JSON.stringify({ nombreUsuario: 'ana' }),
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

test('el catálogo de venta excluye productos agotados', async () => {
  const script = readStatic('js/notas-venta.js');
  const boton = { setAttribute() {}, removeAttribute() {} };
  const contexto = vm.createContext({
    apiGet: async (ruta) => ruta === '/api/clientes' ? [] : [
      { id: 11, nombre: 'Disponible', stockActual: 2 },
      { id: 12, nombre: 'Agotado', stockActual: 0 },
    ],
    document: { getElementById: () => boton },
    mostrarToast() {},
  });
  const cargarCatalogos = script.match(
    /async function cargarCatalogosVenta\(\) \{[\s\S]*?\n\}/,
  )?.[0];
  assert.ok(cargarCatalogos);
  vm.runInContext(cargarCatalogos, contexto);
  await vm.runInContext('cargarCatalogosVenta()', contexto);

  assert.deepEqual(
    Array.from(vm.runInContext('productosVenta', contexto), (producto) => producto.id),
    [11],
  );
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
    const opciones = {
      innerHTML: '',
      hidden: true,
      querySelectorAll: () => [],
    };
    const input = {
      value: '',
      dataset: {},
      listeners: new Map(),
      addEventListener(tipo, listener) { this.listeners.set(tipo, listener); },
      setAttribute(nombre, valor) { this[nombre] = valor; },
      emitir(tipo) { this.listeners.get(tipo)?.(); },
    };
    const cantidad = {
      value: '1',
      listeners: new Map(),
      addEventListener(tipo, listener) { this.listeners.set(tipo, listener); },
      emitir(tipo) { this.listeners.get(tipo)?.(); },
    };
    const campos = {
      '.venta-producto': input,
      '.venta-product-options': opciones,
      '.venta-cantidad': cantidad,
      '.venta-stock': { textContent: '' },
      '.venta-precio': { textContent: '' },
      '.venta-subtotal': { textContent: '' },
    };
    return {
      className: '',
      dataset: {},
      querySelector(selector) { return campos[selector]; },
      remove() { lineas.splice(lineas.indexOf(this), 1); },
      input,
      cantidad,
      campos,
    };
  }

  const cuerpoLineasVenta = {
    appendChild(linea) { lineas.push(linea); },
    querySelectorAll(selector) {
      if (selector === '.venta-linea') return [...lineas];
      if (selector === '.venta-producto') return lineas.map((linea) => linea.input);
      return [];
    },
  };
  const lista = { innerHTML: '' };
  const contexto = vm.createContext({
    cuerpoLineasVenta,
    lineas,
    productosVenta: [
      { id: 1, nombre: 'Uno', codigo: 'U-01', precio: 10, stockActual: 5 },
      { id: 2, nombre: 'Dos', codigo: 'D-02', precio: 2, stockActual: 6 },
    ],
    document: {
      createElement() { return crearLinea(); },
      getElementById(id) { return id === 'venta-total' ? total : lista; },
    },
    escapar: String,
    formatoMoneda: (valor) => `S/ ${Number(valor).toFixed(2)}`,
    mostrarToast() {},
  });

  [
    'catalogoProducto',
    'etiquetaProductoVenta',
    'productosParaBusquedaVenta',
    'renderizarOpcionesProducto',
    'actualizarListaProductosVenta',
    'buscarProductoPorEtiqueta',
    'actualizarLineaVenta',
    'actualizarTotalVenta',
    'agregarLineaVenta',
    'quitarLineaVenta',
  ].forEach((name) => vm.runInContext(extraerFuncion(script, name), contexto));

  vm.runInContext('agregarLineaVenta()', contexto);
  assert.equal(lineas.length, 1);
  lineas[0].input.value = 'Uno · U-01';
  lineas[0].input.emitir('input');
  assert.equal(lineas[0].dataset.productoId, '1');
  lineas[0].cantidad.value = '3';
  lineas[0].cantidad.emitir('input');
  assert.equal(lineas[0].campos['.venta-subtotal'].textContent, 'S/ 30.00');
  assert.equal(total.textContent, 'S/ 30.00');

  vm.runInContext('agregarLineaVenta()', contexto);
  lineas[1].input.value = 'Dos · D-02';
  lineas[1].input.emitir('input');
  lineas[1].cantidad.value = '2';
  lineas[1].cantidad.emitir('input');
  assert.equal(total.textContent, 'S/ 34.00');

  const botonQuitar = {
    closest() { return lineas[0]; },
  };
  contexto.botonQuitar = botonQuitar;
  vm.runInContext('quitarLineaVenta({target:{closest:()=>botonQuitar}})', contexto);
  assert.equal(lineas.length, 1);
  assert.ok(
    Array.from(vm.runInContext('productosParaBusquedaVenta(lineas[0])', contexto))
      .some((producto) => producto.id === 1),
  );
  assert.equal(total.textContent, 'S/ 4.00');
});

test('el selector de productos de venta busca nombre y código e informa detalles de cada opción', () => {
  const script = readStatic('js/notas-venta.js');
  const lineas = [
    { dataset: { productoId: '99' } },
    { dataset: {} },
  ];
  const contexto = vm.createContext({
    cuerpoLineasVenta: { querySelectorAll: () => lineas },
    productosVenta: [
      { id: 4, nombre: 'Body algodón', codigo: 'BD-04', stockActual: 2 },
      { id: 5, nombre: 'Pijama', codigo: 'PJ-05', stockActual: 7 },
      { id: 6, nombre: 'Agotado', codigo: 'AG-06', stockActual: 0 },
    ],
  });
  vm.runInContext(extraerFuncion(script, 'productosParaBusquedaVenta'), contexto);
  contexto.fila = lineas[1];
  assert.deepEqual(
    Array.from(vm.runInContext("productosParaBusquedaVenta(fila, 'algodon')", contexto), (p) => p.id),
    [4],
  );
  assert.deepEqual(
    Array.from(vm.runInContext("productosParaBusquedaVenta(fila, 'PJ-05')", contexto), (p) => p.id),
    [5],
  );
  assert.match(script, /venta-product-option-code/);
  assert.match(script, /venta-product-option-price/);
  assert.match(script, /venta-product-option-stock/);
  assert.doesNotMatch(readStatic('nueva-nota-venta.html'), /<datalist/);
});

test('el selector refresca opciones al seleccionar y eliminar una fila', () => {
  const script = readStatic('js/notas-venta.js');
  const filas = [];
  const total = { textContent: '' };
  const productos = [
    { id: 1, nombre: 'Uno', codigo: 'U-01', precio: 10, stockActual: 5 },
    { id: 2, nombre: 'Dos', codigo: 'D-02', precio: 2, stockActual: 6 },
  ];

  function crearFila() {
    const lista = { innerHTML: '', hidden: true, querySelectorAll: () => [] };
    const input = {
      value: '',
      setAttribute(nombre, valor) { this[nombre] = valor; },
    };
    const campos = {
      '.venta-producto': input,
      '.venta-product-options': lista,
      '.venta-cantidad': { value: '1', max: '' },
      '.venta-stock': { textContent: '' },
      '.venta-precio': { textContent: '' },
      '.venta-subtotal': { textContent: '' },
    };
    return {
      dataset: {},
      querySelector: (selector) => campos[selector],
      remove() { filas.splice(filas.indexOf(this), 1); },
      campos,
    };
  }

  const cuerpoLineasVenta = {
    querySelectorAll: () => filas,
  };
  const contexto = vm.createContext({
    cuerpoLineasVenta,
    productosVenta: productos,
    document: { getElementById: () => total },
    escapar: String,
    formatoMoneda: (valor) => `S/ ${Number(valor).toFixed(2)}`,
  });
  [
    'catalogoProducto',
    'etiquetaProductoVenta',
    'productosParaBusquedaVenta',
    'renderizarOpcionesProducto',
    'actualizarListaProductosVenta',
    'actualizarLineaVenta',
    'actualizarTotalVenta',
    'seleccionarProductoVenta',
    'quitarLineaVenta',
  ].forEach((nombre) => vm.runInContext(extraerFuncion(script, nombre), contexto));

  const filaSeleccionada = crearFila();
  const filaRestante = crearFila();
  filas.push(filaSeleccionada, filaRestante);
  filaRestante.campos['.venta-product-options'].hidden = false;
  contexto.filaSeleccionada = filaSeleccionada;
  contexto.filaRestante = filaRestante;
  vm.runInContext('seleccionarProductoVenta(filaSeleccionada, productosVenta[0])', contexto);

  assert.doesNotMatch(filaRestante.campos['.venta-product-options'].innerHTML, /data-producto-id="1"/);
  assert.match(filaRestante.campos['.venta-product-options'].innerHTML, /data-producto-id="2"/);
  assert.equal(filaRestante.campos['.venta-product-options'].hidden, false);

  contexto.botonQuitar = { closest: () => filaSeleccionada };
  vm.runInContext('quitarLineaVenta({target:{closest:()=>botonQuitar}})', contexto);
  assert.match(filaRestante.campos['.venta-product-options'].innerHTML, /data-producto-id="1"/);
  assert.equal(filaRestante.campos['.venta-product-options'].hidden, false);
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

test('el filtro de rotación aplica días, seis meses y un año al conjunto de ventas', () => {
  const dashboard = readStatic('js/dashboard.js');
  const contexto = vm.createContext({ fechaMovimiento: (valor) => new Date(valor) });
  ['fechaInicioPeriodo', 'filtrarVentasPeriodo'].forEach((nombre) =>
    vm.runInContext(extraerFuncion(dashboard, nombre), contexto));
  const referencia = new Date(Date.UTC(2026, 6, 15, 12));
  contexto.referencia = referencia;

  const inicio7 = vm.runInContext("fechaInicioPeriodo('7', referencia)", contexto);
  const inicio30 = vm.runInContext("fechaInicioPeriodo('30', referencia)", contexto);
  const inicio6m = vm.runInContext("fechaInicioPeriodo('6m', referencia)", contexto);
  const inicio1y = vm.runInContext("fechaInicioPeriodo('1y', referencia)", contexto);
  assert.deepEqual([inicio7.getUTCFullYear(), inicio7.getUTCMonth(), inicio7.getUTCDate()], [2026, 6, 9]);
  assert.deepEqual([inicio30.getUTCFullYear(), inicio30.getUTCMonth(), inicio30.getUTCDate()], [2026, 5, 16]);
  assert.deepEqual([inicio6m.getUTCFullYear(), inicio6m.getUTCMonth(), inicio6m.getUTCDate()], [2026, 0, 15]);
  assert.deepEqual([inicio1y.getUTCFullYear(), inicio1y.getUTCMonth(), inicio1y.getUTCDate()], [2025, 6, 15]);

  const movimientos = [
    { id: 1, tipo: 'VENTA', fecha: '2026-07-01T10:00:00', producto: { id: 1 } },
    { id: 2, tipo: 'VENTA', fecha: '2026-06-30T10:00:00', producto: { id: 2 } },
    { id: 3, tipo: 'ENTRADA', fecha: '2026-07-10T10:00:00', producto: { id: 3 } },
  ];
  contexto.movimientos = movimientos;
  const filtrados = vm.runInContext("filtrarVentasPeriodo(movimientos, '15', referencia)", contexto);
  assert.deepEqual(JSON.parse(JSON.stringify(filtrados.map((movimiento) => movimiento.id))), [1]);
});

test('Pedidos conserva la creación manual y Alertas ya no prepara reposiciones', () => {
  const alertas = readStatic('js/alertas.js');
  const alertasHtml = readStatic('alertas.html');
  const pedidosHtml = readStatic('pedidos.html');
  const pedidos = readStatic('js/pedidos.js');
  const pedidosKanban = readStatic('js/pedidos-kanban.js');

  assert.match(pedidosHtml, /id="form-pedido"/);
  assert.match(pedidos, /document\.getElementById\('btn-nuevo-pedido'\)\.addEventListener\('click', abrirNuevoPedido\)/);
  assert.match(pedidos, /apiPost\('\/api\/pedidos', datos\)/);
  assert.match(pedidos, /if \(guardandoPedido\) return/);
  assert.match(pedidos, /Pedido registrado\. El stock se actualizará al confirmar la recepción/);
  assert.doesNotMatch(pedidos, /luany-reposicion-pendiente|prepararReposicionDesdeAlertas|reponer=1/);
  assert.doesNotMatch(pedidosHtml, /pedido-reposicion-ayuda/);
  assert.doesNotMatch(alertasHtml, /btn-reponer-alertas|modal-reponer-producto|data-cerrar-reposicion|<th>Acción<\/th>/);
  assert.match(alertasHtml, /id="alertas-tabla"[\s\S]*?colspan="6"/);
  assert.doesNotMatch(alertas, /abrirModalReposicion|btn-reponer-alertas|apiPost\('\/api\/pedidos'/);
  assert.doesNotMatch(pedidosKanban, /textContent = 'Reponer'|alerta-producto-/);
});

test('Seleccionar todos marca y desmarca únicamente administradores ofertados', () => {
  const alertas = readStatic('js/alertas.js');
  const destinatarios = [{ checked: false }, { checked: false }, { checked: false }];
  const control = { checked: false, indeterminate: false };
  const contexto = vm.createContext({
    listaDestinatarios: {
      querySelectorAll: (selector) => selector === 'input[name="destinatario"]'
        ? destinatarios : [],
    },
    seleccionarTodosDestinatarios: control,
  });
  vm.runInContext(extraerFuncion(alertas, 'actualizarSeleccionTodos'), contexto);
  vm.runInContext(extraerFuncion(alertas, 'seleccionarDestinatarios'), contexto);

  vm.runInContext('seleccionarDestinatarios(true)', contexto);
  assert.deepEqual(destinatarios.map((item) => item.checked), [true, true, true]);
  assert.equal(control.checked, true);
  assert.equal(control.indeterminate, false);

  destinatarios[1].checked = false;
  vm.runInContext('actualizarSeleccionTodos()', contexto);
  assert.equal(control.checked, false);
  assert.equal(control.indeterminate, true);

  vm.runInContext('seleccionarDestinatarios(false)', contexto);
  assert.deepEqual(destinatarios.map((item) => item.checked), [false, false, false]);
  assert.equal(control.indeterminate, false);
});

test('Alertas conserva destinatarios para ADMIN y ALMACÉN con validación del servidor', () => {
  const html = readStatic('alertas.html');
  const alertas = readStatic('js/alertas.js');
  const seguridad = fs.readFileSync(path.resolve(
    staticDirectory,
    '../../../main/java/com/luany/inventory/controller/ConfiguracionSeguridad.java',
  ), 'utf8');

  assert.match(html, /id="notificacion-seleccionar-todos"/);
  assert.match(alertas, /apiGet\('\/api\/alertas\/destinatarios'\)/);
  assert.match(alertas, /apiPost\('\/api\/alertas\/notificar-stock', \{ destinatarioIds \}\)/);
  assert.match(alertas, /Selecciona al menos un destinatario/);
  assert.match(seguridad, /requestMatchers\(HttpMethod\.POST, "\/api\/alertas\/notificar-stock"\)\s+\.hasAnyRole\("ADMIN", "EMPLEADO"\)/);
});

test('el detalle y la nota recién registrada usan la misma vista imprimible dentro de la página', () => {
  const script = readStatic('js/notas-venta.js');
  const contexto = vm.createContext({
    productosVenta: [{ id: 4, nombre: 'Body algodón', codigo: 'BD-04' }],
    escapar: (texto) => String(texto).replaceAll('&', '&amp;').replaceAll('<', '&lt;'),
    formatoMoneda: (valor) => `S/ ${Number(valor).toFixed(2)}`,
    normalizarFecha: () => '15/07/2026, 10:30',
    documentoClienteVisible: (documento) => documento || '—',
  });
  ['etiquetaProductoVenta', 'buscarProductoPorEtiqueta', 'generarNotaImprimible']
    .forEach((nombre) => vm.runInContext(extraerFuncion(script, nombre), contexto));
  assert.equal(
    vm.runInContext("buscarProductoPorEtiqueta('Body algodón · BD-04').id", contexto),
    4,
  );
  contexto.nota = {
    numeroNota: 'Nota-009',
    fechaEmision: '2026-07-15T10:30:00',
    clienteNombre: 'Cliente general',
    clienteDocumento: 'CLIENTE-GENERAL',
    usuarioNombre: 'Ana',
    total: 37,
    productos: [{ productoNombre: 'Body algodón', cantidad: 2, precioUnitario: 18.5, subtotal: 37 }],
  };
  const documento = vm.runInContext('generarNotaImprimible(nota)', contexto);
  assert.match(documento, /Nota de Venta Nº 009/);
  assert.match(documento, /src="\/img\/logo_luany\.png"/);
  assert.match(documento, /alt="LUANY - Juega, Viste, Crece"/);
  assert.match(documento, /Luany - tienda para bebés/);
  assert.match(documento, /DOCUMENTO NO VÁLIDO COMO COMPROBANTE DE PAGO/);
  assert.match(documento, /¡Gracias por tu compra!/);
  assert.match(documento, /Acompañamos cada etapa de tu bebé/);
  assert.match(documento, /Body algodón/);
  assert.match(documento, /S\/ 37\.00/);
  assert.doesNotMatch(documento, /RUC|Dirección|teléfono/i);
  assert.doesNotMatch(script, /window\.location\.replace\('\/notas-venta\.html'\)/);
  assert.doesNotMatch(script, /window\.open|document\.write/);
  assert.match(documento, /<th>CANTIDAD<\/th><th>PRECIO<br \/>UNITARIO<\/th><th>IMPORTE<\/th>/);
  const estilos = readStatic('css/styles.css');
  assert.match(estilos, /@page \{ size: 80mm auto; margin: 0; \}/);
  assert.match(estilos, /\.nota-venta-imprimible \{[\s\S]*?width: 80mm;[\s\S]*?max-width: 80mm;/);
  assert.match(estilos, /nota-venta-imprimible th:nth-child\(2\)[\s\S]*?width: 14%; text-align: center;/);
  const titulo = { textContent: '' };
  const contenido = { innerHTML: '' };
  const modal = { hidden: true };
  const documentMock = {
    getElementById(id) {
      return id === 'modal-detalle-venta-titulo' ? titulo
        : id === 'venta-documento-imprimible' ? contenido : null;
    },
  };
  contexto.document = documentMock;
  contexto.modalDetalleVenta = modal;
  contexto.mostrarModal = (elemento) => { elemento.hidden = false; };
  vm.runInContext(extraerFuncion(script, 'abrirNotaImprimible'), contexto);
  vm.runInContext('abrirNotaImprimible(nota)', contexto);
  assert.equal(titulo.textContent, 'Nota Nota-009');
  assert.equal(modal.hidden, false);
  assert.match(contenido.innerHTML, /Nota de Venta Nº 009/);
  assert.match(readStatic('notas-venta.html'), /id="venta-documento-imprimible"/);
  assert.match(readStatic('nueva-nota-venta.html'), /id="venta-documento-imprimible"/);
  assert.match(readStatic('css/styles.css'), /@media print/);
});

test('el Kanban conserva estados reales y agrupa el seguimiento de pedidos pendientes o parciales', () => {
  const script = readStatic('js/pedidos-kanban.js');
  const html = readStatic('pedidos-kanban.html');
  const contexto = vm.createContext({});
  vm.runInContext(extraerFuncion(script, 'contarProductosSeguimiento'), contexto);
  vm.runInContext(extraerFuncion(script, 'productosBajoStockUnicos'), contexto);
  vm.runInContext(extraerFuncion(script, 'pedidosRelacionadosProducto'), contexto);
  const pedidosActivos = [
    { estado: 'PENDIENTE', productoIds: [1, 2] },
    { estado: 'PARCIAL', productoIds: [2, 3, 4] },
    { estado: 'RECIBIDO', productoIds: [8] },
  ];
  const alertas = [{ productoId: 5 }, { productoId: 1 }];
  const cantidad = vm.runInContext(
    `contarProductosSeguimiento(${JSON.stringify(pedidosActivos)}, ${JSON.stringify(alertas)})`,
    contexto,
  );
  assert.equal(cantidad, 5);
  const unicos = vm.runInContext(`productosBajoStockUnicos([
    { productoId: 5, nombre: 'Body' },
    { productoId: 5, nombre: 'Body duplicado' },
    { productoId: 0, nombre: 'Inválido' },
  ])`, contexto);
  assert.equal(unicos.length, 1);
  assert.equal(unicos[0].nombre, 'Body');
  contexto.alerta = { productoId: 5 };
  const relacionados = vm.runInContext(`pedidosRelacionadosProducto(alerta, [
    { id: 2, estado: 'PARCIAL', productoIds: [5, 6] },
    { id: 3, estado: 'PENDIENTE', productoIds: [7] },
    { id: 4, estado: 'RECIBIDO', productoIds: [5] },
  ])`, contexto);
  assert.deepEqual(JSON.parse(JSON.stringify(relacionados.map((pedido) => pedido.id))), [2]);
  assert.match(html, /id="kanban-titulo-abastecer">Por abastecer/);
  assert.match(html, /id="kanban-titulo-pedido">Pedido/);
  assert.match(html, /id="kanban-titulo-recibido">Recibido/);
  assert.match(script, /clave: 'PARCIAL', columna: 'kanban-por-abastecer'/);
  assert.match(script, /clave: 'PENDIENTE', columna: 'kanban-pedido'/);
  assert.match(script, /clave: 'RECIBIDO', columna: 'kanban-recibido'/);
  assert.match(script, /apiGet\('\/api\/alertas\/stock'\)/);
  assert.doesNotMatch(script, /alerta-producto-|textContent = 'Reponer'/);
  assert.match(script, /productoIds/);
  assert.match(html, /id="kanban-ultima-actualizacion"/);
  assert.match(html, /id="kanban-detalle-total"/);
});
