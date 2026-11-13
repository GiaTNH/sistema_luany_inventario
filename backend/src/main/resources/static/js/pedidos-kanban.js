iniciarLayout('Tablero Kanban');

const estadosKanban = [
  { clave: 'PARCIAL', columna: 'kanban-por-abastecer', conteo: 'kanban-conteo-abastecer', vacio: 'No hay pedidos pendientes de abastecimiento.' },
  { clave: 'PENDIENTE', columna: 'kanban-pedido', conteo: 'kanban-conteo-pedido', vacio: 'No hay pedidos por recibir.' },
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
  return incluirHora
    ? fecha.toLocaleString('es-PE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
    : fecha.toLocaleDateString('es-PE', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
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
    gestionar.href = `/pedidos.html?pedidoId=${encodeURIComponent(pedido.id)}`;
    gestionar.textContent = 'Gestionar recepción';
    acciones.appendChild(gestionar);
  }

  tarjeta.append(encabezado, proveedor, datos, acciones);
  return tarjeta;
}

function productosBajoStockUnicos(alertas) {
  const productos = new Map();
  alertas.forEach((alerta) => {
    if (!Number.isSafeInteger(Number(alerta.productoId)) || Number(alerta.productoId) < 1) return;
    const clave = String(alerta.productoId);
    if (!productos.has(clave)) productos.set(clave, alerta);
  });
  return [...productos.values()];
}

function pedidosRelacionadosProducto(alerta, pedidosActivos) {
  return pedidosActivos.filter((pedido) =>
    (pedido.estado === 'PENDIENTE' || pedido.estado === 'PARCIAL')
      && Array.isArray(pedido.productoIds)
      && pedido.productoIds.some((productoId) => String(productoId) === String(alerta.productoId)));
}

function crearTarjetaProductoBajoStock(alerta, pedidosActivos) {
  const tarjeta = document.createElement('article');
  tarjeta.className = 'kanban-tarjeta kanban-tarjeta-producto';
  const nombre = document.createElement('h4');
  nombre.textContent = alerta.nombre || 'Producto sin nombre';
  const categoria = document.createElement('p');
  categoria.className = 'kanban-proveedor';
  categoria.textContent = alerta.categoria || 'Categoría no disponible';
  const datos = document.createElement('div');
  datos.className = 'kanban-datos';
  datos.append(
    crearDatoTarjeta('Stock actual', `${alerta.stockActual ?? '—'} uds.`),
    crearDatoTarjeta('Stock mínimo', `${alerta.stockMinimo ?? '—'} uds.`),
    crearDatoTarjeta('Reposición sugerida', `${alerta.cantidadSugerida ?? '—'} uds.`),
  );
  const relacionados = pedidosRelacionadosProducto(alerta, pedidosActivos);
  const acciones = document.createElement('div');
  acciones.className = 'kanban-acciones';
  if (relacionados.length) {
    relacionados.forEach((pedido) => {
      const vinculo = document.createElement('a');
      vinculo.className = 'btn btn-secundario';
      vinculo.href = `/pedidos.html?pedidoId=${encodeURIComponent(pedido.id)}`;
      vinculo.textContent = `Pedido #${pedido.id} · ${pedido.estado === 'PARCIAL' ? 'Parcial' : 'Pendiente'}`;
      acciones.appendChild(vinculo);
    });
  } else {
    const reponer = document.createElement('a');
    reponer.className = 'btn btn-primario';
    reponer.href = `/alertas.html#alerta-producto-${encodeURIComponent(alerta.productoId)}`;
    reponer.textContent = 'Reponer';
    acciones.appendChild(reponer);
  }
  tarjeta.append(nombre, categoria, datos, acciones);
  return tarjeta;
}

function renderizarTablero(pedidos, alertas = [], pedidosActivos = [], actualizado = true) {
  estadosKanban.forEach((estado) => {
    const columna = document.getElementById(estado.columna);
    const pedidosEstado = pedidos.filter((pedido) => pedido.estado === estado.clave);
    columna.replaceChildren();
    const productosBajoStock = estado.clave === 'PARCIAL' ? productosBajoStockUnicos(alertas) : [];
    const termino = filtroBuscarKanban.value.trim().toLocaleLowerCase('es-PE');
    const alertasVisibles = estado.clave === 'PARCIAL'
      ? productosBajoStock.filter((alerta) => !termino
        || String(alerta.nombre || '').toLocaleLowerCase('es-PE').includes(termino)
        || String(alerta.categoria || '').toLocaleLowerCase('es-PE').includes(termino)
        || pedidosActivos.some((pedido) => String(pedido.id).includes(termino)
          && pedido.productoIds?.some((productoId) => String(productoId) === String(alerta.productoId))))
      : [];
    document.getElementById(estado.conteo).textContent =
      String(pedidosEstado.length + alertasVisibles.length);
    if (!pedidosEstado.length && !alertasVisibles.length) {
      const vacio = document.createElement('p');
      vacio.className = 'kanban-vacio';
      vacio.textContent = estado.vacio;
      columna.appendChild(vacio);
      return;
    }
    if (estado.clave === 'PARCIAL') {
      alertasVisibles.forEach((alerta) =>
        columna.appendChild(crearTarjetaProductoBajoStock(alerta, pedidosActivos)));
    }
    pedidosEstado.forEach((pedido) => columna.appendChild(crearTarjetaPedido(pedido)));
  });
  const productosEnSeguimiento = contarProductosSeguimiento(pedidosActivos, alertas);
  document.getElementById('kanban-productos-seguimiento').textContent =
    `${productosEnSeguimiento} producto${productosEnSeguimiento === 1 ? '' : 's'} en seguimiento`;
  if (actualizado) {
    const actualizacion = new Date();
    const horaActualizacion = document.getElementById('kanban-ultima-actualizacion');
    horaActualizacion.dateTime = actualizacion.toISOString();
    horaActualizacion.textContent = `Última actualización: ${actualizacion.toLocaleString('es-PE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })}`;
  }
}

function contarProductosSeguimiento(pedidos, alertas = []) {
  const productoIds = new Set();
  pedidos.forEach((pedido) => {
    if (pedido.estado !== 'PENDIENTE' && pedido.estado !== 'PARCIAL') return;
    (pedido.productoIds || []).forEach((productoId) => productoIds.add(String(productoId)));
  });
  alertas.forEach((alerta) => {
    if (Number.isSafeInteger(Number(alerta.productoId)) && Number(alerta.productoId) > 0) {
      productoIds.add(String(alerta.productoId));
    }
  });
  return productoIds.size;
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
    const [pedidos, pedidosTodos, alertas] = await Promise.all([
      apiGet(`/api/pedidos/tablero${query ? `?${query}` : ''}`),
      apiGet('/api/pedidos/tablero'),
      apiGet('/api/alertas/stock'),
    ]);
    if (numeroSolicitud !== solicitudKanban) return;
    if (!Array.isArray(pedidos) || !Array.isArray(pedidosTodos) || !Array.isArray(alertas)) {
      throw new Error('La respuesta del tablero no tiene el formato esperado.');
    }
    const pedidosActivos = pedidosTodos.filter((pedido) =>
      pedido.estado === 'PENDIENTE' || pedido.estado === 'PARCIAL');
    renderizarTablero(pedidos, alertas, pedidosActivos);
    mensaje.textContent = `${pedidos.length} pedido${pedidos.length === 1 ? '' : 's'} en el tablero`;
  } catch (error) {
    if (numeroSolicitud !== solicitudKanban) return;
    renderizarTablero([], [], [], false);
    document.getElementById('kanban-productos-seguimiento').textContent = 'No disponible';
    mensaje.textContent = 'No se pudieron cargar los pedidos.';
    mostrarToast(error.message, 'error');
  }
}

function renderizarDetalle(pedido) {
  document.getElementById('kanban-detalle-titulo').textContent = `Detalle del pedido #${pedido.id}`;
  const resumen = document.getElementById('kanban-detalle-resumen');
  resumen.replaceChildren();
  [
    ['Proveedor', pedido.proveedorNombre],
    ['Fecha del pedido', fechaKanban(pedido.fechaPedido, true)],
    ['Entrega estimada', fechaKanban(pedido.fechaEstimadaEntrega)],
    ['Registrado por', pedido.usuarioNombre],
    ['Estado', pedido.estado],
  ].forEach(([etiqueta, valor]) => {
    const bloque = document.createElement('div');
    const titulo = document.createElement('dt');
    titulo.textContent = etiqueta;
    const contenido = document.createElement('dd');
    contenido.textContent = valor || '—';
    bloque.append(titulo, contenido);
    resumen.appendChild(bloque);
  });
  const tbody = document.getElementById('kanban-detalle-lineas');
  tbody.replaceChildren();
  if (!pedido.productos.length) {
    const vacio = document.createElement('tr');
    const celda = document.createElement('td');
    celda.colSpan = 6;
    celda.textContent = 'Este pedido no tiene productos asociados.';
    vacio.appendChild(celda);
    tbody.appendChild(vacio);
  } else {
    pedido.productos.forEach((producto) => {
      const fila = document.createElement('tr');
      [producto.productoNombre, producto.cantidadPedida, producto.cantidadRecibida,
        producto.cantidadPendiente, `S/ ${Number(producto.precio).toFixed(2)}`,
        `S/ ${Number(producto.subtotal).toFixed(2)}`]
        .forEach((valor) => {
          const celda = document.createElement('td');
          celda.textContent = String(valor);
          fila.appendChild(celda);
        });
      tbody.appendChild(fila);
    });
  }
  document.getElementById('kanban-detalle-total').textContent = `S/ ${Number(pedido.total).toFixed(2)}`;

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
    `/pedidos.html?pedidoId=${encodeURIComponent(pedido.id)}`;
}

async function abrirDetalleKanban(pedidoId) {
  try {
    const pedido = await apiGet(`/api/pedidos/${pedidoId}`);
    renderizarDetalle(pedido);
    abrirModalAccesible(modalDetalle);
  } catch (error) {
    mostrarToast(error.message, 'error');
  }
}

function cerrarDetalleKanban() {
  cerrarModalAccesible(modalDetalle);
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
