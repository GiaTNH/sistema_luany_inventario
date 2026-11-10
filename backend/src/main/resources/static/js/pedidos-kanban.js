iniciarLayout('Tablero Kanban');

const estadosKanban = [
  { clave: 'PENDIENTE', columna: 'kanban-pendiente', conteo: 'kanban-conteo-pendiente', vacio: 'No hay pedidos pendientes.' },
  { clave: 'PARCIAL', columna: 'kanban-parcial', conteo: 'kanban-conteo-parcial', vacio: 'No hay pedidos con recepción parcial.' },
  { clave: 'RECIBIDO', columna: 'kanban-recibido', conteo: 'kanban-conteo-recibido', vacio: 'No hay pedidos recibidos.' },
];
const modalDetalle = document.getElementById('kanban-modal-detalle');
const filtroBuscarKanban = document.getElementById('kanban-buscar');
const filtroEstadoKanban = document.getElementById('kanban-estado');
const filtroDesdeKanban = document.getElementById('kanban-desde');
const filtroHastaKanban = document.getElementById('kanban-hasta');
let temporizadorBuscarKanban;
let solicitudKanban = 0;

function fechaKanban(valor, incluirHora = false) {
  if (!valor) return 'No definida';
  const fecha = Array.isArray(valor)
    ? new Date(valor[0], valor[1] - 1, valor[2], valor[3] || 0, valor[4] || 0, valor[5] || 0)
    : new Date(Array.isArray(valor) ? valor : typeof valor === 'string' && valor.length === 10
      ? `${valor}T00:00:00` : valor);
  if (Number.isNaN(fecha.getTime())) return 'No definida';
  return fecha.toLocaleDateString('es-PE', incluirHora
    ? { dateStyle: 'short', timeStyle: 'short' }
    : { dateStyle: 'medium' });
}

function badgeKanban(estado) {
  const etiquetas = { PENDIENTE: 'Pendiente', PARCIAL: 'Recepción parcial', RECIBIDO: 'Recibido' };
  const badge = document.createElement('span');
  badge.className = `kanban-estado-badge badge-${estado.toLowerCase()}`;
  badge.textContent = etiquetas[estado] || estado;
  return badge;
}

function crearDatoTarjeta(etiqueta, valor) {
  const dato = document.createElement('div');
  dato.className = 'kanban-dato';
  const titulo = document.createElement('span');
  titulo.textContent = etiqueta;
  const contenido = document.createElement('strong');
  contenido.textContent = valor;
  dato.append(titulo, contenido);
  return dato;
}

function crearTarjetaPedido(pedido) {
  const tarjeta = document.createElement('article');
  tarjeta.className = 'kanban-tarjeta';

  const encabezado = document.createElement('div');
  encabezado.className = 'kanban-tarjeta-encabezado';
  const numero = document.createElement('h4');
  numero.textContent = `Pedido #${pedido.id}`;
  encabezado.append(numero, badgeKanban(pedido.estado));

  const proveedor = document.createElement('p');
  proveedor.className = 'kanban-proveedor';
  proveedor.textContent = pedido.proveedorNombre || 'Proveedor no disponible';

  const datos = document.createElement('div');
  datos.className = 'kanban-datos';
  datos.append(
    crearDatoTarjeta('Creado', fechaKanban(pedido.fechaPedido, true)),
    crearDatoTarjeta('Entrega estimada', fechaKanban(pedido.fechaEstimadaEntrega)),
    crearDatoTarjeta('Productos', `${pedido.totalProductos} líneas · ${pedido.cantidadTotalSolicitada} uds.`),
    crearDatoTarjeta('Recibido', `${pedido.cantidadRecibida} uds.`),
    crearDatoTarjeta('Pendiente', `${pedido.cantidadPendiente} uds.`),
  );

  const acciones = document.createElement('div');
  acciones.className = 'kanban-acciones';
  const detalle = document.createElement('button');
  detalle.type = 'button';
  detalle.className = 'btn btn-secundario';
  detalle.dataset.pedidoDetalle = pedido.id;
  detalle.textContent = 'Ver detalles';
  acciones.appendChild(detalle);

  if (pedido.estado !== 'RECIBIDO') {
    const gestionar = document.createElement('a');
    gestionar.className = 'btn btn-primario';
    gestionar.href = `/pedidos.html?buscar=${encodeURIComponent(pedido.id)}`;
    gestionar.textContent = 'Gestionar recepción';
    acciones.appendChild(gestionar);
  }

  tarjeta.append(encabezado, proveedor, datos, acciones);
  return tarjeta;
}

function renderizarTablero(pedidos) {
  estadosKanban.forEach((estado) => {
    const columna = document.getElementById(estado.columna);
    const pedidosEstado = pedidos.filter((pedido) => pedido.estado === estado.clave);
    document.getElementById(estado.conteo).textContent = String(pedidosEstado.length);
    columna.replaceChildren();
    if (!pedidosEstado.length) {
      const vacio = document.createElement('p');
      vacio.className = 'kanban-vacio';
      vacio.textContent = estado.vacio;
      columna.appendChild(vacio);
      return;
    }
    pedidosEstado.forEach((pedido) => columna.appendChild(crearTarjetaPedido(pedido)));
  });
}

async function cargarTablero() {
  const numeroSolicitud = ++solicitudKanban;
  const mensaje = document.getElementById('kanban-mensaje');
  mensaje.textContent = 'Cargando pedidos...';
  const parametros = new URLSearchParams();
  if (filtroBuscarKanban.value.trim()) parametros.set('buscar', filtroBuscarKanban.value.trim());
  if (filtroEstadoKanban.value) parametros.set('estado', filtroEstadoKanban.value);
  if (filtroDesdeKanban.value) parametros.set('desde', filtroDesdeKanban.value);
  if (filtroHastaKanban.value) parametros.set('hasta', filtroHastaKanban.value);

  try {
    const query = parametros.toString();
    const pedidos = await apiGet(`/api/pedidos/tablero${query ? `?${query}` : ''}`);
    if (numeroSolicitud !== solicitudKanban) return;
    renderizarTablero(pedidos);
    mensaje.textContent = `${pedidos.length} pedido${pedidos.length === 1 ? '' : 's'} en el tablero`;
  } catch (error) {
    if (numeroSolicitud !== solicitudKanban) return;
    renderizarTablero([]);
    mensaje.textContent = 'No se pudieron cargar los pedidos.';
    mostrarToast(error.message, 'error');
  }
}

function renderizarDetalle(pedido) {
  document.getElementById('kanban-detalle-titulo').textContent = `Detalle del pedido #${pedido.id}`;
  document.getElementById('kanban-detalle-resumen').textContent =
    `Proveedor: ${pedido.proveedorNombre} · Creado: ${fechaKanban(pedido.fechaPedido, true)}`
    + ` · Entrega estimada: ${fechaKanban(pedido.fechaEstimadaEntrega)} · Estado: ${pedido.estado}`;
  const tbody = document.getElementById('kanban-detalle-lineas');
  tbody.replaceChildren();
  if (!pedido.productos.length) {
    const vacio = document.createElement('tr');
    const celda = document.createElement('td');
    celda.colSpan = 4;
    celda.textContent = 'Este pedido no tiene productos asociados.';
    vacio.appendChild(celda);
    tbody.appendChild(vacio);
  } else {
    pedido.productos.forEach((producto) => {
      const fila = document.createElement('tr');
      [producto.productoNombre, producto.cantidadPedida, producto.cantidadRecibida, producto.cantidadPendiente]
        .forEach((valor) => {
          const celda = document.createElement('td');
          celda.textContent = String(valor);
          fila.appendChild(celda);
        });
      tbody.appendChild(fila);
    });
  }

  const historial = document.getElementById('kanban-historial-recepciones');
  historial.replaceChildren();
  if (!pedido.recepciones.length) {
    const vacio = document.createElement('p');
    vacio.className = 'text-suave';
    vacio.textContent = 'Todavía no hay recepciones registradas.';
    historial.appendChild(vacio);
  } else {
    pedido.recepciones.forEach((recepcion) => {
      const bloque = document.createElement('div');
      bloque.className = 'pedido-recepcion-historica';
      const titulo = document.createElement('p');
      titulo.textContent = `Recepción #${recepcion.id} · ${fechaKanban(recepcion.fecha, true)} · ${recepcion.usuarioNombre}`;
      const lista = document.createElement('ul');
      recepcion.productos.forEach((producto) => {
        const linea = document.createElement('li');
        linea.textContent = `${producto.productoNombre}: ${producto.cantidad} recibidas`;
        lista.appendChild(linea);
      });
      bloque.append(titulo, lista);
      historial.appendChild(bloque);
    });
  }
  document.getElementById('kanban-gestionar-pedido').href =
    `/pedidos.html?buscar=${encodeURIComponent(pedido.id)}`;
}

async function abrirDetalleKanban(pedidoId) {
  try {
    const pedido = await apiGet(`/api/pedidos/${pedidoId}`);
    renderizarDetalle(pedido);
    modalDetalle.hidden = false;
    modalDetalle.setAttribute('aria-hidden', 'false');
    requestAnimationFrame(() => modalDetalle.classList.add('visible'));
  } catch (error) {
    mostrarToast(error.message, 'error');
  }
}

function cerrarDetalleKanban() {
  modalDetalle.classList.remove('visible');
  modalDetalle.hidden = true;
  modalDetalle.setAttribute('aria-hidden', 'true');
}

document.getElementById('kanban-columnas').addEventListener('click', (evento) => {
  const boton = evento.target.closest('[data-pedido-detalle]');
  if (boton) abrirDetalleKanban(boton.dataset.pedidoDetalle);
});
document.querySelectorAll('[data-cerrar-kanban-modal]').forEach((boton) => {
  boton.addEventListener('click', cerrarDetalleKanban);
});
modalDetalle.addEventListener('click', (evento) => {
  if (evento.target === modalDetalle) cerrarDetalleKanban();
});
document.addEventListener('keydown', (evento) => {
  if (evento.key === 'Escape' && !modalDetalle.hidden) cerrarDetalleKanban();
});
document.getElementById('kanban-actualizar').addEventListener('click', cargarTablero);
document.getElementById('kanban-estado').addEventListener('change', cargarTablero);
[filtroDesdeKanban, filtroHastaKanban].forEach((filtro) => {
  filtro.addEventListener('change', cargarTablero);
});
filtroBuscarKanban.addEventListener('input', () => {
  window.clearTimeout(temporizadorBuscarKanban);
  temporizadorBuscarKanban = window.setTimeout(cargarTablero, 250);
});
window.addEventListener('pageshow', (evento) => {
  if (evento.persisted) cargarTablero();
});

cargarTablero();
