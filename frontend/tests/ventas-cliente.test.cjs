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

test('el modal de Ventas replica los campos, límites y opciones del registro de clientes', () => {
  const ventas = readStatic('nueva-nota-venta.html');
  const clientes = readStatic('clientes.html');
  const formularioVentas = ventas.match(/<form id="form-nuevo-cliente-venta"[\s\S]*?<\/form>/)?.[0];
  const formularioClientes = clientes.match(/<form id="form-cliente"[\s\S]*?<\/form>/)?.[0];

  assert.ok(formularioVentas);
  assert.ok(formularioClientes);
  assert.match(ventas, /id="btn-nuevo-cliente-venta"[^>]*>\+ Nuevo cliente/);
  assert.match(formularioVentas, /id="venta-cliente-nombre"[^>]*maxlength="120"[^>]*required/);
  assert.match(formularioVentas, /id="venta-cliente-tipo-documento"[^>]*required/);
  assert.match(formularioVentas, /<option value="DNI">DNI<\/option>[\s\S]*<option value="RUC">RUC<\/option>[\s\S]*<option value="OTRO">Otro<\/option>/);
  assert.match(formularioVentas, /id="venta-cliente-numero-documento"[^>]*maxlength="20"[^>]*required/);
  assert.match(formularioVentas, /id="venta-cliente-telefono"[^>]*type="tel"[^>]*maxlength="20"/);
  assert.match(formularioVentas, /id="venta-cliente-direccion"[^>]*maxlength="200"/);
  assert.match(formularioClientes, /id="cliente-nombre"[^>]*maxlength="120"[^>]*required/);
  assert.match(formularioClientes, /id="cliente-numero-documento"[^>]*maxlength="20"[^>]*required/);
  assert.match(formularioClientes, /id="cliente-telefono"[^>]*type="tel"[^>]*maxlength="20"/);
  assert.match(formularioClientes, /id="cliente-direccion"[^>]*maxlength="200"/);
});

test('el selector excluye Cliente general e incorpora y selecciona el nuevo cliente', () => {
  const source = readStatic('js/notas-venta.js');
  const selector = { value: '', innerHTML: '' };
  const contexto = vm.createContext({
    selectClienteVenta: selector,
    clientesVenta: [
      { id: 1, nombre: 'Cliente general', numeroDocumento: 'CLIENTE-GENERAL' },
      { id: 2, nombre: 'Ana', numeroDocumento: '12345' },
    ],
    escapar: (valor) => String(valor),
  });
  vm.runInContext(extraerFuncion(source, 'renderizarSelectorClientesVenta'), contexto);

  vm.runInContext('renderizarSelectorClientesVenta()', contexto);
  assert.match(selector.innerHTML, /Cliente general \(sin datos personales\)/);
  assert.match(selector.innerHTML, /Ana · 12345/);
  assert.equal((selector.innerHTML.match(/<option/g) || []).length, 2);

  vm.runInContext('renderizarSelectorClientesVenta(3)', contexto);
  assert.equal(selector.value, '3');
});

test('guardar cliente reutiliza POST /api/clientes, actualiza el selector y lo selecciona', async () => {
  const source = readStatic('js/notas-venta.js');
  const evento = { preventDefault() { this.prevenido = true; } };
  const campos = {
    'venta-cliente-nombre': { value: '  Ana López  ' },
    'venta-cliente-tipo-documento': { value: 'DNI' },
    'venta-cliente-numero-documento': { value: ' 12345678 ' },
    'venta-cliente-telefono': { value: ' 999123456 ' },
    'venta-cliente-direccion': { value: ' Lima ' },
  };
  const estado = { error: { hidden: true, textContent: '' }, boton: { disabled: false }, seleccionado: '', cerrado: false, toast: '' };
  const contexto = vm.createContext({
    formNuevoClienteVenta: { reportValidity: () => true },
    errorNuevoClienteVenta: estado.error,
    botonGuardarClienteVenta: estado.boton,
    clientesVenta: [{ id: 1, nombre: 'Luis', numeroDocumento: '87654321' }],
    document: { getElementById: (id) => campos[id] },
    apiPost: async (ruta, cuerpo) => {
      estado.ruta = ruta;
      estado.datos = cuerpo;
      return { id: 2, nombre: 'Ana López', numeroDocumento: '12345678' };
    },
    renderizarSelectorClientesVenta: (id) => { estado.seleccionado = String(id); },
    cerrarModalNuevoClienteVenta: () => { estado.cerrado = true; },
    mostrarToast: (mensaje, tipo) => { estado.toast = `${mensaje}:${tipo}`; },
    evento,
  });
  vm.runInContext(extraerFuncion(source, 'guardarNuevoClienteVenta'), contexto);

  await vm.runInContext('guardarNuevoClienteVenta(evento)', contexto);
  assert.equal(evento.prevenido, true);
  assert.equal(estado.ruta, '/api/clientes');
  assert.deepEqual(JSON.parse(JSON.stringify(estado.datos)), {
    nombre: 'Ana López',
    tipoDocumento: 'DNI',
    numeroDocumento: '12345678',
    telefono: '999123456',
    direccion: 'Lima',
  });
  assert.equal(estado.seleccionado, '2');
  assert.equal(estado.cerrado, true);
  assert.equal(estado.toast, 'Cliente registrado correctamente.:exito');
  assert.equal(estado.boton.disabled, false);
});

test('la validación del formulario evita enviar datos incompletos', async () => {
  const source = readStatic('js/notas-venta.js');
  let solicitudes = 0;
  const contexto = vm.createContext({
    formNuevoClienteVenta: { reportValidity: () => false },
    errorNuevoClienteVenta: { hidden: true, textContent: '' },
    botonGuardarClienteVenta: { disabled: false },
    apiPost: async () => { solicitudes += 1; },
  });
  vm.runInContext(extraerFuncion(source, 'guardarNuevoClienteVenta'), contexto);

  await vm.runInContext('guardarNuevoClienteVenta({ preventDefault() {} })', contexto);
  assert.equal(solicitudes, 0);
  assert.equal(contexto.botonGuardarClienteVenta.disabled, false);
});

test('un documento duplicado informa el error y mantiene abierto el formulario', async () => {
  const source = readStatic('js/notas-venta.js');
  const error = { hidden: true, textContent: '' };
  const boton = { disabled: false };
  const contexto = vm.createContext({
    formNuevoClienteVenta: { reportValidity: () => true },
    errorNuevoClienteVenta: error,
    botonGuardarClienteVenta: boton,
    clientesVenta: [],
    document: { getElementById: () => ({ value: 'dato' }) },
    apiPost: async () => { throw new Error('Ya existe un cliente con ese número de documento'); },
    renderizarSelectorClientesVenta: () => assert.fail('no debe actualizar el selector'),
    cerrarModalNuevoClienteVenta: () => assert.fail('no debe cerrar el modal'),
    mostrarToast: () => {},
  });
  vm.runInContext(extraerFuncion(source, 'guardarNuevoClienteVenta'), contexto);

  await vm.runInContext('guardarNuevoClienteVenta({ preventDefault() {} })', contexto);
  assert.equal(error.hidden, false);
  assert.equal(error.textContent, 'Ya existe un cliente con ese número de documento');
  assert.equal(boton.disabled, false);
});

test('cancelar el alta cierra y limpia solo su formulario, preservando la nota en curso', () => {
  const source = readStatic('js/notas-venta.js');
  const venta = { clienteId: '', lineas: [{ productoId: 7, cantidad: 2 }], total: 40 };
  let formularioReiniciado = 0;
  let modalCerrado = 0;
  const contexto = vm.createContext({
    modalNuevoClienteVenta: {},
    formNuevoClienteVenta: { reset: () => { formularioReiniciado += 1; } },
    errorNuevoClienteVenta: { hidden: false },
    botonGuardarClienteVenta: { disabled: true },
    ocultarModal: () => { modalCerrado += 1; },
    venta,
  });
  vm.runInContext(extraerFuncion(source, 'cerrarModalNuevoClienteVenta'), contexto);

  vm.runInContext('cerrarModalNuevoClienteVenta()', contexto);
  assert.equal(formularioReiniciado, 1);
  assert.equal(modalCerrado, 1);
  assert.equal(contexto.errorNuevoClienteVenta.hidden, true);
  assert.equal(contexto.botonGuardarClienteVenta.disabled, false);
  assert.deepEqual(venta, { clienteId: '', lineas: [{ productoId: 7, cantidad: 2 }], total: 40 });
});
