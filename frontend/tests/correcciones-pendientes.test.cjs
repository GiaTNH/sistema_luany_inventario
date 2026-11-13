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
  assert.match(dashboard, /<option value="15">Últimos 15 días<\/option>/);
  assert.match(dashboard, /<option value="6m">Últimos 6 meses<\/option>/);
  assert.match(dashboard, /<option value="1y">Último año<\/option>/);
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
    const input = {
      value: '',
      dataset: {},
      listeners: new Map(),
      addEventListener(tipo, listener) { this.listeners.set(tipo, listener); },
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
  assert.match(lista.innerHTML, /Uno · U-01/);
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

test('el filtro de rotación aplica días, seis meses y un año al conjunto de ventas', () => {
  const dashboard = readStatic('js/dashboard.js');
  const contexto = vm.createContext({ fechaMovimiento: (valor) => new Date(valor) });
  ['fechaInicioPeriodo', 'filtrarVentasPeriodo'].forEach((nombre) =>
    vm.runInContext(extraerFuncion(dashboard, nombre), contexto));
  const referencia = new Date(2026, 6, 15, 12);
  contexto.referencia = referencia;

  const inicio7 = vm.runInContext("fechaInicioPeriodo('7', referencia)", contexto);
  const inicio30 = vm.runInContext("fechaInicioPeriodo('30', referencia)", contexto);
  const inicio6m = vm.runInContext("fechaInicioPeriodo('6m', referencia)", contexto);
  const inicio1y = vm.runInContext("fechaInicioPeriodo('1y', referencia)", contexto);
  assert.deepEqual([inicio7.getFullYear(), inicio7.getMonth(), inicio7.getDate()], [2026, 6, 9]);
  assert.deepEqual([inicio30.getFullYear(), inicio30.getMonth(), inicio30.getDate()], [2026, 5, 16]);
  assert.deepEqual([inicio6m.getFullYear(), inicio6m.getMonth(), inicio6m.getDate()], [2026, 0, 15]);
  assert.deepEqual([inicio1y.getFullYear(), inicio1y.getMonth(), inicio1y.getDate()], [2025, 6, 15]);

  const movimientos = [
    { id: 1, tipo: 'VENTA', fecha: '2026-07-01T10:00:00', producto: { id: 1 } },
    { id: 2, tipo: 'VENTA', fecha: '2026-06-30T10:00:00', producto: { id: 2 } },
    { id: 3, tipo: 'ENTRADA', fecha: '2026-07-10T10:00:00', producto: { id: 3 } },
  ];
  contexto.movimientos = movimientos;
  const filtrados = vm.runInContext("filtrarVentasPeriodo(movimientos, '15', referencia)", contexto);
  assert.deepEqual(JSON.parse(JSON.stringify(filtrados.map((movimiento) => movimiento.id))), [1]);
});

test('la reposición desde Alertas usa cantidades sugeridas y agrupa por proveedor sin crear pedidos', () => {
  const alertas = readStatic('js/alertas.js');
  const pedidosHtml = readStatic('pedidos.html');
  const pedidos = readStatic('js/pedidos.js');
  const alertaContexto = vm.createContext({});
  vm.runInContext(extraerFuncion(alertas, 'productosParaReposicion'), alertaContexto);
  const sugeridos = vm.runInContext(`productosParaReposicion(${JSON.stringify([
    { productoId: 7, cantidadSugerida: 12 },
    { productoId: 8, cantidadSugerida: 0 },
    { productoId: 0, cantidadSugerida: 3 },
  ])})`, alertaContexto);
  assert.deepEqual(JSON.parse(JSON.stringify(sugeridos)), [{ productoId: 7, cantidad: 12 }]);

  const pedidoContexto = vm.createContext({
    proveedores: [
      { id: 3, nombre: 'Proveedor A', estado: 'ACTIVO' },
      { id: 4, nombre: 'Proveedor B', estado: 'INACTIVO' },
      { id: 5, nombre: 'Proveedor C', estado: 'ACTIVO' },
    ],
    productos: [
      { id: 7, nombre: 'Body', precio: 18.5, proveedor: { id: 3 } },
      { id: 8, nombre: 'Manta', precio: 25, proveedor: { id: 4 } },
      { id: 9, nombre: 'Gorro', precio: 8, proveedor: { id: 5 } },
    ],
  });
  vm.runInContext(extraerFuncion(pedidos, 'opcionesProveedoresActivos'), pedidoContexto);
  vm.runInContext(extraerFuncion(pedidos, 'agruparReposiciones'), pedidoContexto);
  const grupos = vm.runInContext(
    'agruparReposiciones([{productoId:7,cantidad:12},{productoId:8,cantidad:4},{productoId:9,cantidad:6}])',
    pedidoContexto,
  );
  assert.equal(grupos.length, 2);
  assert.equal(grupos[0].proveedorId, 3);
  assert.equal(grupos[0].productos[0].cantidad, 12);

  const lineas = [];
  function filaPedido() {
    const campos = {
      '.pedido-producto': { value: '' },
      '.pedido-cantidad': { value: '' },
      '.pedido-precio': { value: '' },
    };
    return {
      querySelector: (selector) => campos[selector],
      campos,
    };
  }
  const cuerpo = {
    appendChild: (fila) => lineas.push(fila),
    replaceChildren: () => { lineas.length = 0; },
    get lastElementChild() { return lineas.at(-1); },
  };
  const selectProveedor = { value: '' };
  const ayuda = { textContent: '', hidden: true };
  const errorPedido = { hidden: true };
  const formulario = { reset() {} };
  pedidoContexto.formPedido = formulario;
  pedidoContexto.cuerpoLineasPedido = cuerpo;
  pedidoContexto.selectProveedor = selectProveedor;
  pedidoContexto.reposicionesPendientes = grupos;
  pedidoContexto.document = {
    createElement: () => filaPedido(),
    getElementById: (id) => id === 'pedido-error' ? errorPedido : ayuda,
  };
  pedidoContexto.escapar = String;
  [
    'productosDeProveedor',
    'opcionesProducto',
    'agregarLineaPedido',
    'mostrarGrupoReposicion',
  ].forEach((nombre) => vm.runInContext(extraerFuncion(pedidos, nombre), pedidoContexto));
  assert.equal(vm.runInContext('mostrarGrupoReposicion(3)', pedidoContexto), true);
  assert.equal(selectProveedor.value, '3');
  assert.equal(lineas.length, 1);
  assert.equal(lineas[0].campos['.pedido-producto'].value, '7');
  assert.equal(lineas[0].campos['.pedido-cantidad'].value, '12');
  assert.equal(lineas[0].campos['.pedido-precio'].value, '18.50');
  assert.equal(ayuda.hidden, false);

  assert.match(readStatic('alertas.html'), /id="btn-reponer-alertas" hidden disabled/);
  assert.match(alertas, /usuarioAlertas\?\.rol === 'ADMIN'/);
  assert.match(alertas, /window\.location\.href = '\/pedidos\.html\?reponer=1'/);
  assert.doesNotMatch(alertas, /apiPost\(/);
  assert.match(pedidosHtml, /id="form-pedido"/);
  assert.match(pedidos, /apiPost\('\/api\/pedidos', datos\)/);
  assert.match(pedidos, /if \(guardandoPedido\) return/);
  assert.match(pedidos, /el stock solo cambiará al registrar la recepción/);
});

test('la nota imprimible usa la numeración real, logo local y datos existentes del cliente', () => {
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
  assert.match(documento, /src="\/img\/logo-bolsa\.png"/);
  assert.match(documento, /DOCUMENTO NO VÁLIDO COMO COMPROBANTE DE PAGO/);
  assert.match(documento, /¡Gracias por tu compra!/);
  assert.match(documento, /Acompañamos cada etapa de tu bebé/);
  assert.match(documento, /Body algodón/);
  assert.match(documento, /S\/ 37\.00/);
  assert.doesNotMatch(documento, /RUC|Dirección|teléfono/i);
  assert.doesNotMatch(script, /window\.location\.replace\('\/notas-venta\.html'\)/);
  const ventana = {
    document: {
      open() { this.abierto = true; },
      write(html) { this.html = html; },
      close() { this.cerrado = true; },
    },
    opener: {},
  };
  contexto.nota = contexto.nota;
  contexto.ventana = ventana;
  vm.runInContext(extraerFuncion(script, 'abrirNotaImprimible'), contexto);
  vm.runInContext('abrirNotaImprimible(nota, ventana)', contexto);
  assert.equal(ventana.opener, null);
  assert.equal(ventana.document.abierto, true);
  assert.equal(ventana.document.cerrado, true);
  assert.match(ventana.document.html, /Nota de Venta Nº 009/);
});

test('el Kanban conserva estados reales y agrupa el seguimiento de pedidos pendientes o parciales', () => {
  const script = readStatic('js/pedidos-kanban.js');
  const html = readStatic('pedidos-kanban.html');
  const contexto = vm.createContext({});
  vm.runInContext(extraerFuncion(script, 'contarProductosSeguimiento'), contexto);
  const cantidad = vm.runInContext(`contarProductosSeguimiento(${JSON.stringify([
    { estado: 'PENDIENTE', totalProductos: 2 },
    { estado: 'PARCIAL', totalProductos: 3 },
    { estado: 'RECIBIDO', totalProductos: 8 },
  ])})`, contexto);
  assert.equal(cantidad, 5);
  assert.match(html, /id="kanban-titulo-abastecer">Por abastecer/);
  assert.match(html, /id="kanban-titulo-pedido">Pedido/);
  assert.match(html, /id="kanban-titulo-recibido">Recibido/);
  assert.match(script, /clave: 'PARCIAL', columna: 'kanban-por-abastecer'/);
  assert.match(script, /clave: 'PENDIENTE', columna: 'kanban-pedido'/);
  assert.match(script, /clave: 'RECIBIDO', columna: 'kanban-recibido'/);
  assert.match(html, /id="kanban-ultima-actualizacion"/);
  assert.match(html, /id="kanban-detalle-total"/);
});
