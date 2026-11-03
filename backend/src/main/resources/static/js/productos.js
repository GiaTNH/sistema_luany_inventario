/* ===== productos.js ===== */

iniciarLayout('Productos');

// Estado local
let todosProductos  = [];
let todasCategorias = [];
let todosProveedores = [];
let ordenProductos = { campo: null, direccion: 1 };

// ---------- PESTAÑAS ----------

function cambiarTab(nombre) {
  document.querySelectorAll('.tab-btn').forEach((btn, i) => {
    const tabs = ['productos', 'categorias', 'proveedores'];
    btn.classList.toggle('activo', tabs[i] === nombre);
  });
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('activo'));
  document.getElementById(`tab-${nombre}`).classList.add('activo');

  document.body.dataset.tabActiva = nombre;
  document.querySelectorAll('.productos-nav-tab').forEach(enlace => {
    enlace.classList.toggle('activo', enlace.dataset.productTab === nombre);
  });
  const enlaceProductos = document.querySelector('.productos-nav-main');
  if (enlaceProductos) enlaceProductos.classList.toggle('activo', nombre === 'productos');
  if (nombre === 'productos') history.replaceState(null, '', `${location.pathname}${location.search}`);
  else history.replaceState(null, '', `#${nombre}`);

  if (nombre === 'categorias')  cargarCategorias();
  if (nombre === 'proveedores') cargarProveedores();
}

// ---------- MODALES ----------

function abrirModal(id) {
  document.getElementById(id).classList.add('visible');
}

function cerrarModal(id) {
  document.getElementById(id).classList.remove('visible');
}

// Cierra modal al hacer clic en el overlay
document.querySelectorAll('.modal-overlay').forEach(overlay => {
  overlay.addEventListener('click', e => {
    if (e.target === overlay) overlay.classList.remove('visible');
  });
});

document.addEventListener('click', evento => {
  if (evento.target.closest('.productos-entry-menu-popover') || !evento.target.closest('.productos-entry-menu')) cerrarMenuEntradaSalida();
});

document.addEventListener('keydown', evento => {
  if (evento.key === 'Escape') cerrarMenuEntradaSalida();
});

// ---------- UTILIDADES FORMULARIO ----------

function mostrarErrForm(id, visible) {
  const el = document.getElementById(id);
  if (el) el.classList.toggle('visible', visible);
}

function limpiarErrs(ids) { ids.forEach(id => mostrarErrForm(id, false)); }

// ---------- CARGA INICIAL ----------

async function cargarTodo() {
  try {
    [todosProductos, todasCategorias, todosProveedores] = await Promise.all([
      apiGet('/api/productos'),
      apiGet('/api/categorias'),
      apiGet('/api/proveedores'),
    ]);
    renderTablaProductos(todosProductos);
    poblarFiltroCategoria();
  } catch (err) {
    mostrarToast(err.message, 'error');
    document.getElementById('tabla-productos-body').innerHTML =
      `<tr><td colspan="9" style="text-align:center;color:var(--rojo);padding:24px;">${escapar(err.message)}</td></tr>`;
  }
}

function poblarFiltroCategoria() {
  const sel = document.getElementById('filtro-categoria');
  const actual = sel.value;
  sel.innerHTML = '<option value="">Todas las categorías</option>' +
    todasCategorias.map(c => `<option value="${c.id}">${escapar(c.nombre)}</option>`).join('');
  sel.value = actual;
}

// ---------- TABLA PRODUCTOS ----------

function renderTablaProductos(lista) {
  const tbody = document.getElementById('tabla-productos-body');
  if (!lista.length) {
    tbody.innerHTML = `<tr><td colspan="9"><div class="productos-empty-state"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m16 16 4 4M8 11h6"/></svg><p>No se encontraron productos con estos filtros.</p></div></td></tr>`;
    return;
  }

  tbody.innerHTML = lista.map(p => {
    const estado = calcularEstado(p.stockActual, p.stockMinimo, p.stockMaximo);
    const nombre = escapar(p.nombre);
    const categoriaClase = claseCategoria(p.categoria);
    const iconoCategoria = iconoCategoriaSvg(p.categoria);
    const iconoPrenda = iconoPrendaSvg(p.nombre);
    return `<tr class="productos-card-row">
      <td><div class="producto-table-name">
        <span class="producto-table-thumb">
          <span class="producto-thumb-fallback" aria-hidden="true">${iconoPrenda}</span>
          <img src="/img/productos/${encodeURIComponent(p.id)}.png" alt="" loading="lazy" onerror="this.hidden=true" />
        </span>
        <span class="producto-table-copy"><strong>${nombre}</strong></span>
      </div></td>
      <td><span class="producto-category-tag ${categoriaClase}">${iconoCategoria}${escapar(p.categoria?.nombre || '—')}</span></td>
      <td><span class="producto-provider-name">${escapar(p.proveedor?.nombre || '—')}</span></td>
      <td class="producto-stock-current"><strong>${Number(p.stockActual) || 0}</strong></td>
      <td class="producto-stock-muted">${Number(p.stockMinimo) || 0}</td>
      <td class="producto-stock-muted">${Number(p.stockMaximo) || 0}</td>
      <td>S/ ${Number(p.precio).toFixed(2)}</td>
      <td><span class="producto-status producto-status-${estado.toLowerCase()}"><i></i>${estado}</span></td>
      <td><div class="productos-row-actions">
        <button type="button" class="productos-action-button productos-row-history" title="Ver movimientos" aria-label="Ver movimientos de ${nombre}" onclick="verHistorial(${p.id},'${nombre}')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="2.5"/></svg>
        </button>
        <button type="button" class="productos-action-button productos-row-edit" title="Editar" aria-label="Editar ${nombre}" onclick="abrirModalProducto(${p.id})">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4 11.5-11.5Z"/></svg>
        </button>
        <div class="productos-entry-menu">
          <button type="button" class="productos-action-button productos-row-entry" title="Registrar entrada o salida" aria-label="Registrar entrada o salida para ${nombre}" aria-expanded="false" onclick="toggleMenuEntradaSalida(this,event)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 19V5m-6 6 6-6 6 6"/></svg>
          </button>
          <div class="productos-entry-menu-popover" role="menu">
            <button type="button" role="menuitem" title="Registrar entrada" aria-label="Registrar entrada para ${nombre}" onclick="abrirModalMovimiento(${p.id},'${nombre}','ENTRADA')"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 15v4h16v-4M12 3v12m-5-5 5 5 5-5"/></svg>Registrar entrada</button>
            <button type="button" role="menuitem" title="Registrar salida" aria-label="Registrar salida para ${nombre}" onclick="abrirModalMovimiento(${p.id},'${nombre}','SALIDA')"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 19V5m-6 6 6-6 6 6"/></svg>Registrar salida</button>
          </div>
        </div>
        <button type="button" class="productos-action-button productos-row-delete" title="Eliminar" aria-label="Eliminar ${nombre}" onclick="confirmarEliminarProducto(${p.id},'${nombre}')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h16M10 11v6m4-6v6M6 7l1 14h10l1-14M9 7V4h6v3"/></svg>
        </button>
      </div></td>
    </tr>`;
  }).join('');
}

function ordenarProductos(campo) {
  if (ordenProductos.campo === campo) ordenProductos.direccion *= -1;
  else ordenProductos = { campo, direccion: 1 };
  actualizarIndicadorOrden();
  aplicarFiltros();
}

function actualizarIndicadorOrden() {
  document.querySelectorAll('.productos-sort-button').forEach(boton => {
    const activo = boton.dataset.sort === ordenProductos.campo;
    boton.classList.toggle('orden-activo', activo);
    boton.setAttribute('aria-sort', activo ? (ordenProductos.direccion === 1 ? 'ascending' : 'descending') : 'none');
    const icono = boton.querySelector('svg');
    if (icono) icono.classList.toggle('orden-descendente', activo && ordenProductos.direccion === -1);
  });
}

function aplicarOrdenProductos(lista) {
  if (!ordenProductos.campo) return lista;
  const gravedad = { AGOTADO: 0, BAJO: 1, NORMAL: 2, EXCESO: 3 };
  const valor = (producto) => {
    switch (ordenProductos.campo) {
      case 'nombre': return producto.nombre || '';
      case 'categoria': return producto.categoria?.nombre || '';
      case 'proveedor': return producto.proveedor?.nombre || '';
      case 'stockActual': return Number(producto.stockActual) || 0;
      case 'stockMinimo': return Number(producto.stockMinimo) || 0;
      case 'stockMaximo': return Number(producto.stockMaximo) || 0;
      case 'precio': return Number(producto.precio) || 0;
      case 'estado': return gravedad[calcularEstado(producto.stockActual, producto.stockMinimo, producto.stockMaximo)];
      default: return '';
    }
  };
  return [...lista].sort((a, b) => {
    const primero = valor(a);
    const segundo = valor(b);
    const comparacion = typeof primero === 'number'
      ? primero - segundo
      : String(primero).localeCompare(String(segundo), 'es', { sensitivity: 'base' });
    return comparacion * ordenProductos.direccion;
  });
}

function toggleMenuEntradaSalida(boton, evento) {
  evento.stopPropagation();
  const menu = boton.closest('.productos-entry-menu');
  const abrir = !menu.classList.contains('abierto');
  cerrarMenuEntradaSalida();
  if (abrir) {
    menu.classList.add('abierto');
    boton.setAttribute('aria-expanded', 'true');
  }
}

function cerrarMenuEntradaSalida() {
  document.querySelectorAll('.productos-entry-menu.abierto').forEach(menu => {
    menu.classList.remove('abierto');
    menu.querySelector('.productos-row-entry')?.setAttribute('aria-expanded', 'false');
  });
}

function claseCategoria(categoria) {
  const nombre = (categoria?.nombre || '').toLocaleLowerCase('es-PE').normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  if (nombre.includes('bebe nina')) return 'category-color-baby-girl';
  if (nombre.includes('bebe nino')) return 'category-color-baby-boy';
  if (nombre.includes('unisex')) return 'category-color-unisex';
  if (nombre.includes('accesorio')) return 'category-color-accessories';
  if (nombre.includes('nina')) return 'category-color-girl';
  if (nombre.includes('nino')) return 'category-color-boy';
  const paleta = ((Number(categoria?.id) || 1) - 1) % 6 + 1;
  return `category-palette-${paleta}`;
}

function iconoCategoriaSvg(categoria) {
  const nombre = (categoria?.nombre || '').toLocaleLowerCase('es-PE').normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  if (nombre.includes('bebe')) {
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 9a5 5 0 0 1 10 0v1a7 7 0 0 1-14 0V9a2 2 0 0 1 4 0M17 9a2 2 0 0 1 4 0v1a7 7 0 0 1-2 5"/><circle cx="9" cy="12" r=".7" fill="currentColor"/><circle cx="15" cy="12" r=".7" fill="currentColor"/><path d="M10 15h4"/></svg>';
  }
  return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 13V6a2 2 0 0 0-2-2H7L3 8l8 8 4-4Z"/><circle cx="8" cy="8" r="1"/></svg>';
}

function iconoPrendaSvg(nombre) {
  const texto = (nombre || '').toLocaleLowerCase('es-PE').normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  const iconos = [
    ['body', 'M8 3 5 5l2 4-1 12h12L17 9l2-4-3-2-2 3h-4L8 3Z'],
    ['mameluco', 'M8 3 5 5l2 4-1 5 2 2-1 5h10l-1-5 2-2-1-5 2-4-3-2-2 3h-4L8 3Z'],
    ['pijama', 'M8 3 5 5l2 4-2 3 2 2-1 6h12l-1-6 2-2-2-3 2-4-3-2-2 3h-4L8 3Z'],
    ['vestido', 'M9 3h6l1 4 3 3-2 11H7L5 10l3-3 1-4Z'],
    ['polo', 'M8 3 5 5l2 4 2-1v12h6V6l2 1 2-4-3-2-2 3h-4L8 3Z'],
    ['jean', 'M7 3h10l1 18h-5l-1-8-1 8H6L7 3Z'],
    ['casaca', 'M8 3 5 5l2 4-1 12h5V11h2v8h5L17 7l2-2-3-2-2 3h-4L8 3Z'],
    ['buzo', 'M8 3 5 5l2 4-1 12h5V11h2v8h5L17 7l2-2-3-2-2 3h-4L8 3Z'],
    ['short', 'M7 3h10l1 10-4 1-2-4-2 4-4-1 1-10Z'],
    ['gorro', 'M4 13a8 8 0 0 1 16 0v2H4v-2Zm-1 4h18v3H3z'],
    ['manta', 'M4 4h16v16H4zM8 4v16m8-16v16M4 9h16m-16 6h16'],
    ['medias', 'M7 3h5v10l3 2-1 5H5v-4l2-3V3Zm8 0h4v8l2 2-1 5h-6l1-4V3Z'],
    ['babero', 'M8 4h8l2 4-2 2v10H8V10L6 8l2-4Zm4 4v4'],
  ];
  const trazo = iconos.find(([palabra]) => texto.includes(palabra))?.[1] || 'M8 3 5 5l2 4-1 12h12L17 9l2-4-3-2-2 3h-4L8 3Z';
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="${trazo}"/></svg>`;
}


// ---------- FILTROS ----------

function aplicarFiltros() {
  const texto    = document.getElementById('buscador').value.toLowerCase();
  const catId    = document.getElementById('filtro-categoria').value;
  const estadoF  = document.getElementById('filtro-estado').value;

  const filtrados = todosProductos.filter(p => {
    const nombre = (p.nombre || '').toLocaleLowerCase('es-PE');
    const categoria = (p.categoria?.nombre || '').toLocaleLowerCase('es-PE');
    const coincideNombre = nombre.includes(texto);
    const coincideCat    = !catId || String(p.categoria?.id) === catId;
    const estado         = calcularEstado(p.stockActual, p.stockMinimo, p.stockMaximo);
    const coincideEstado = !estadoF || estado === estadoF;
    return coincideNombre && coincideCat && coincideEstado;
  });

  renderTablaProductos(aplicarOrdenProductos(filtrados));
}

document.getElementById('buscador').addEventListener('input', aplicarFiltros);
document.getElementById('filtro-categoria').addEventListener('change', aplicarFiltros);
document.getElementById('filtro-estado').addEventListener('change', aplicarFiltros);

// ---------- MODAL PRODUCTO ----------

function poblarSelectsCatalogos() {
  const selCat  = document.getElementById('prod-categoria');
  const selProv = document.getElementById('prod-proveedor');
  const catActual  = selCat.value;
  const provActual = selProv.value;

  selCat.innerHTML  = '<option value="">Seleccionar...</option>' +
    todasCategorias.map(c => `<option value="${c.id}">${escapar(c.nombre)}</option>`).join('');
  selProv.innerHTML = '<option value="">Seleccionar...</option>' +
    todosProveedores.map(p => `<option value="${p.id}">${escapar(p.nombre)}</option>`).join('');

  selCat.value  = catActual;
  selProv.value = provActual;
}

function abrirModalProducto(id = null) {
  limpiarErrs(['err-prod-nombre','err-prod-categoria','err-prod-proveedor',
               'err-prod-stock-actual','err-prod-precio','err-prod-stock-min','err-prod-stock-max']);

  document.getElementById('prod-id').value           = '';
  document.getElementById('prod-nombre').value       = '';
  document.getElementById('prod-categoria').value    = '';
  document.getElementById('prod-proveedor').value    = '';
  document.getElementById('prod-stock-actual').value = '';
  document.getElementById('prod-precio').value       = '';
  document.getElementById('prod-stock-min').value    = '';
  document.getElementById('prod-stock-max').value    = '';

  poblarSelectsCatalogos();

  if (id) {
    const p = todosProductos.find(x => x.id === id);
    if (p) {
      document.getElementById('modal-producto-titulo').textContent = 'Editar producto';
      document.getElementById('prod-id').value           = p.id;
      document.getElementById('prod-nombre').value       = p.nombre;
      document.getElementById('prod-categoria').value    = p.categoria?.id || '';
      document.getElementById('prod-proveedor').value    = p.proveedor?.id || '';
      document.getElementById('prod-stock-actual').value = p.stockActual;
      document.getElementById('prod-precio').value       = p.precio;
      document.getElementById('prod-stock-min').value    = p.stockMinimo;
      document.getElementById('prod-stock-max').value    = p.stockMaximo;
    }
  } else {
    document.getElementById('modal-producto-titulo').textContent = 'Nuevo producto';
  }

  abrirModal('modal-producto');
}

async function guardarProducto() {
  limpiarErrs(['err-prod-nombre','err-prod-categoria','err-prod-proveedor',
               'err-prod-stock-actual','err-prod-precio','err-prod-stock-min','err-prod-stock-max']);

  const nombre      = document.getElementById('prod-nombre').value.trim();
  const categoriaId = Number(document.getElementById('prod-categoria').value);
  const proveedorId = Number(document.getElementById('prod-proveedor').value);
  const stockActual = Number(document.getElementById('prod-stock-actual').value);
  const precio      = Number(document.getElementById('prod-precio').value);
  const stockMinimo = Number(document.getElementById('prod-stock-min').value);
  const stockMaximo = Number(document.getElementById('prod-stock-max').value);
  const id          = document.getElementById('prod-id').value;

  let valido = true;
  if (!nombre)                          { mostrarErrForm('err-prod-nombre', true);       valido = false; }
  if (!categoriaId)                     { mostrarErrForm('err-prod-categoria', true);    valido = false; }
  if (!proveedorId)                     { mostrarErrForm('err-prod-proveedor', true);    valido = false; }
  if (isNaN(stockActual) || stockActual < 0) { mostrarErrForm('err-prod-stock-actual', true); valido = false; }
  if (isNaN(precio) || precio < 0)      { mostrarErrForm('err-prod-precio', true);       valido = false; }
  if (isNaN(stockMinimo) || stockMinimo < 0) { mostrarErrForm('err-prod-stock-min', true); valido = false; }
  if (isNaN(stockMaximo) || stockMaximo < stockMinimo) { mostrarErrForm('err-prod-stock-max', true); valido = false; }
  if (!valido) return;

  const cuerpo = { nombre, categoriaId, proveedorId, stockActual, stockMinimo, stockMaximo, precio };
  const btn = document.getElementById('btn-guardar-producto');
  btn.disabled = true;

  try {
    if (id) {
      await apiPut(`/api/productos/${id}`, cuerpo);
      mostrarToast('Producto actualizado correctamente.', 'exito');
    } else {
      await apiPost('/api/productos', cuerpo);
      mostrarToast('Producto creado correctamente.', 'exito');
    }
    cerrarModal('modal-producto');
    await cargarTodo();
  } catch (err) {
    mostrarToast(err.message, 'error');
  } finally {
    btn.disabled = false;
  }
}

// ---------- ELIMINAR PRODUCTO ----------

function confirmarEliminarProducto(id, nombre) {
  document.getElementById('confirmar-mensaje').textContent =
    `¿Eliminar el producto "${nombre}"? Esta acción no se puede deshacer.`;
  const btnOk = document.getElementById('btn-confirmar-ok');
  btnOk.onclick = () => eliminarProducto(id);
  abrirModal('modal-confirmar');
}

async function eliminarProducto(id) {
  const btn = document.getElementById('btn-confirmar-ok');
  btn.disabled = true;
  try {
    await apiDelete(`/api/productos/${id}`);
    mostrarToast('Producto eliminado.', 'exito');
    cerrarModal('modal-confirmar');
    await cargarTodo();
  } catch (err) {
    mostrarToast(err.message, 'error');
  } finally {
    btn.disabled = false;
  }
}

// ---------- MODAL MOVIMIENTO ----------

function abrirModalMovimiento(productoId, nombreProducto, tipoInicial = 'ENTRADA') {
  limpiarErrs(['err-mov-cantidad']);
  document.getElementById('mov-producto-id').value    = productoId;
  document.getElementById('mov-producto-nombre').value = nombreProducto;
  document.getElementById('mov-tipo').value           = tipoInicial;
  document.getElementById('mov-cantidad').value       = '';
  document.getElementById('mov-observacion').value    = '';

  const titulo = tipoInicial === 'ENTRADA' ? 'Registrar entrada' : 'Registrar salida / venta';
  document.getElementById('modal-mov-titulo').textContent = titulo;

  // Para entrada solo mostrar ENTRADA; para salida mostrar SALIDA y VENTA
  const selTipo = document.getElementById('mov-tipo');
  if (tipoInicial === 'ENTRADA') {
    selTipo.innerHTML = '<option value="ENTRADA">Entrada</option>';
  } else {
    selTipo.innerHTML = '<option value="SALIDA">Salida</option><option value="VENTA">Venta</option>';
  }

  abrirModal('modal-movimiento');
}

async function guardarMovimiento() {
  limpiarErrs(['err-mov-cantidad']);
  const productoId  = Number(document.getElementById('mov-producto-id').value);
  const tipo        = document.getElementById('mov-tipo').value;
  const cantidad    = Number(document.getElementById('mov-cantidad').value);
  const observacion = document.getElementById('mov-observacion').value.trim();

  if (!cantidad || cantidad < 1) { mostrarErrForm('err-mov-cantidad', true); return; }

  const btn = document.getElementById('btn-guardar-mov');
  btn.disabled = true;

  try {
    await apiPost('/api/movimientos', { productoId, tipo, cantidad, observacion });
    mostrarToast('Movimiento registrado correctamente.', 'exito');
    cerrarModal('modal-movimiento');
    await cargarTodo();
  } catch (err) {
    mostrarToast(err.message, 'error');
  } finally {
    btn.disabled = false;
  }
}

// ---------- HISTORIAL ----------

async function verHistorial(productoId, nombre) {
  document.getElementById('modal-historial-titulo').textContent = `Historial — ${nombre}`;
  document.getElementById('tabla-historial-body').innerHTML =
    `<tr class="cargando-fila"><td colspan="4"><span class="spinner spinner-dark"></span> Cargando...</td></tr>`;
  abrirModal('modal-historial');

  try {
    const movs = await apiGet(`/api/movimientos/producto/${productoId}`);
    const tbody = document.getElementById('tabla-historial-body');
    if (!movs.length) {
      tbody.innerHTML = `<tr><td colspan="4"><div class="estado-vacio"><p>Sin movimientos registrados.</p></div></td></tr>`;
      return;
    }
    tbody.innerHTML = movs.map(m => {
      const colorTipo = m.tipo === 'ENTRADA' ? 'badge-normal' : m.tipo === 'VENTA' ? 'badge-bajo' : 'badge-exceso';
      return `<tr>
        <td>${escapar(normalizarFecha(m.fecha))}</td>
        <td><span class="badge ${colorTipo}">${escapar(m.tipo)}</span></td>
        <td>${m.cantidad}</td>
        <td>${escapar(m.observacion || '—')}</td>
      </tr>`;
    }).join('');
  } catch (err) {
    mostrarToast(err.message, 'error');
    cerrarModal('modal-historial');
  }
}

// ---------- CATEGORÍAS ----------

async function cargarCategorias() {
  try {
    todasCategorias = await apiGet('/api/categorias');
    renderTablaCategorias();
  } catch (err) {
    mostrarToast(err.message, 'error');
  }
}

function renderTablaCategorias() {
  const tbody = document.getElementById('tabla-categorias-body');
  if (!todasCategorias.length) {
    tbody.innerHTML = `<tr><td colspan="3"><div class="estado-vacio"><p>Sin categorías.</p></div></td></tr>`;
    return;
  }
  tbody.innerHTML = todasCategorias.map(c => `<tr class="productos-card-row">
    <td><span class="productos-entity-id">${c.id}</span></td>
    <td><strong class="productos-entity-name">${escapar(c.nombre)}</strong></td>
    <td>
      <div class="productos-row-actions productos-entity-actions">
        <button type="button" class="productos-action-button productos-row-edit" title="Editar categoría" aria-label="Editar categoría ${escapar(c.nombre)}" onclick="abrirModalCategoria(${c.id},'${escapar(c.nombre)}')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4 11.5-11.5Z"/></svg>
        </button>
        <button type="button" class="productos-action-button productos-row-delete" title="Eliminar categoría" aria-label="Eliminar categoría ${escapar(c.nombre)}" onclick="confirmarEliminarCat(${c.id},'${escapar(c.nombre)}')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h16M10 11v6m4-6v6M6 7l1 14h10l1-14M9 7V4h6v3"/></svg>
        </button>
      </div>
    </td>
  </tr>`).join('');
}

function abrirModalCategoria(id = null, nombre = '') {
  document.getElementById('cat-id').value     = id || '';
  document.getElementById('cat-nombre').value = nombre;
  document.getElementById('modal-cat-titulo').textContent = id ? 'Editar categoría' : 'Nueva categoría';
  limpiarErrs(['err-cat-nombre']);
  abrirModal('modal-categoria');
}

async function guardarCategoria() {
  const nombre = document.getElementById('cat-nombre').value.trim();
  const id     = document.getElementById('cat-id').value;
  if (!nombre) { mostrarErrForm('err-cat-nombre', true); return; }

  try {
    if (id) {
      await apiPut(`/api/categorias/${id}`, { nombre });
      mostrarToast('Categoría actualizada.', 'exito');
    } else {
      await apiPost('/api/categorias', { nombre });
      mostrarToast('Categoría creada.', 'exito');
    }
    cerrarModal('modal-categoria');
    await cargarCategorias();
    // Actualizar filtro y selects de producto
    poblarFiltroCategoria();
  } catch (err) {
    mostrarToast(err.message, 'error');
  }
}

function confirmarEliminarCat(id, nombre) {
  document.getElementById('confirmar-mensaje').textContent = `¿Eliminar la categoría "${nombre}"?`;
  document.getElementById('btn-confirmar-ok').onclick = () => eliminarCategoria(id);
  abrirModal('modal-confirmar');
}

async function eliminarCategoria(id) {
  const btn = document.getElementById('btn-confirmar-ok');
  btn.disabled = true;
  try {
    await apiDelete(`/api/categorias/${id}`);
    mostrarToast('Categoría eliminada.', 'exito');
    cerrarModal('modal-confirmar');
    await cargarCategorias();
  } catch (err) {
    mostrarToast(err.message, 'error');
  } finally {
    btn.disabled = false;
  }
}

// ---------- PROVEEDORES ----------

async function cargarProveedores() {
  try {
    todosProveedores = await apiGet('/api/proveedores');
    renderTablaProveedores();
  } catch (err) {
    mostrarToast(err.message, 'error');
  }
}

function renderTablaProveedores() {
  const tbody = document.getElementById('tabla-proveedores-body');
  if (!todosProveedores.length) {
    tbody.innerHTML = `<tr><td colspan="4"><div class="estado-vacio"><p>Sin proveedores.</p></div></td></tr>`;
    return;
  }
  tbody.innerHTML = todosProveedores.map(p => `<tr class="productos-card-row">
    <td><span class="productos-entity-id">${p.id}</span></td>
    <td><strong class="productos-entity-name">${escapar(p.nombre)}</strong></td>
    <td><span class="productos-entity-phone">${escapar(p.telefono || '—')}</span></td>
    <td>
      <div class="productos-row-actions productos-entity-actions">
        <button type="button" class="productos-action-button productos-row-edit" title="Editar proveedor" aria-label="Editar proveedor ${escapar(p.nombre)}" onclick="abrirModalProveedor(${p.id},'${escapar(p.nombre)}','${escapar(p.telefono||'')}')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4 11.5-11.5Z"/></svg>
        </button>
        <button type="button" class="productos-action-button productos-row-delete" title="Eliminar proveedor" aria-label="Eliminar proveedor ${escapar(p.nombre)}" onclick="confirmarEliminarProv(${p.id},'${escapar(p.nombre)}')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h16M10 11v6m4-6v6M6 7l1 14h10l1-14M9 7V4h6v3"/></svg>
        </button>
      </div>
    </td>
  </tr>`).join('');
}

function abrirModalProveedor(id = null, nombre = '', telefono = '') {
  document.getElementById('prov-id').value       = id || '';
  document.getElementById('prov-nombre').value   = nombre;
  document.getElementById('prov-telefono').value = telefono;
  document.getElementById('modal-prov-titulo').textContent = id ? 'Editar proveedor' : 'Nuevo proveedor';
  limpiarErrs(['err-prov-nombre']);
  abrirModal('modal-proveedor');
}

async function guardarProveedor() {
  const nombre   = document.getElementById('prov-nombre').value.trim();
  const telefono = document.getElementById('prov-telefono').value.trim();
  const id       = document.getElementById('prov-id').value;
  if (!nombre) { mostrarErrForm('err-prov-nombre', true); return; }

  try {
    if (id) {
      await apiPut(`/api/proveedores/${id}`, { nombre, telefono });
      mostrarToast('Proveedor actualizado.', 'exito');
    } else {
      await apiPost('/api/proveedores', { nombre, telefono });
      mostrarToast('Proveedor creado.', 'exito');
    }
    cerrarModal('modal-proveedor');
    await cargarProveedores();
  } catch (err) {
    mostrarToast(err.message, 'error');
  }
}

function confirmarEliminarProv(id, nombre) {
  document.getElementById('confirmar-mensaje').textContent = `¿Eliminar el proveedor "${nombre}"?`;
  document.getElementById('btn-confirmar-ok').onclick = () => eliminarProveedor(id);
  abrirModal('modal-confirmar');
}

async function eliminarProveedor(id) {
  const btn = document.getElementById('btn-confirmar-ok');
  btn.disabled = true;
  try {
    await apiDelete(`/api/proveedores/${id}`);
    mostrarToast('Proveedor eliminado.', 'exito');
    cerrarModal('modal-confirmar');
    await cargarProveedores();
  } catch (err) {
    mostrarToast(err.message, 'error');
  } finally {
    btn.disabled = false;
  }
}

// ---------- INICIO ----------
const tabInicial = location.hash.slice(1);
if (tabInicial === 'categorias' || tabInicial === 'proveedores') cambiarTab(tabInicial);
cargarTodo();
