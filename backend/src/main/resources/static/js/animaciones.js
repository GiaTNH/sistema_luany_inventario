(() => {
  const reducirMovimiento = window.matchMedia('(prefers-reduced-motion: reduce)');

  function inicializarDashboard() {
    const tarjetas = [...document.querySelectorAll('.dashboard-page .dashboard-stat-card')];
    tarjetas.forEach((tarjeta, indice) => {
      tarjeta.style.setProperty('--dashboard-delay', `${indice * 80}ms`);
    });

    const numeroAnterior = new WeakMap();
    const animacionesNumero = new WeakMap();
    const formatoNumero = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 0 });

    function leerEntero(texto) {
      const limpio = texto.trim().replace(/[\s\u00a0\u202f]/g, '');
      if (/^[+-]?\d+$/.test(limpio)) {
        const numero = Number(limpio);
        return Number.isSafeInteger(numero) ? numero : null;
      }
      if (/^[+-]?\d{1,3}(?:[.,]\d{3})+$/.test(limpio)) {
        const numero = Number(limpio.replace(/[.,]/g, ''));
        return Number.isSafeInteger(numero) ? numero : null;
      }
      return null;
    }

    function escribirTexto(elemento, texto) {
      const nodo = [...elemento.childNodes].find((hijo) => hijo.nodeType === Node.TEXT_NODE);
      if (nodo) nodo.nodeValue = texto;
      else elemento.prepend(document.createTextNode(texto));
    }

    function animarNumero(elemento) {
      const textoFinal = elemento.textContent.trim();
      const destino = leerEntero(textoFinal);
      const estado = animacionesNumero.get(elemento);

      if (destino === null) {
        if (estado) cancelAnimationFrame(estado.frame);
        animacionesNumero.delete(elemento);
        return;
      }
      if (estado?.textoFinal === textoFinal) return;

      if (estado) cancelAnimationFrame(estado.frame);
      const inicio = numeroAnterior.has(elemento) ? numeroAnterior.get(elemento) : 0;
      numeroAnterior.set(elemento, destino);

      if (reducirMovimiento.matches || inicio === destino) {
        escribirTexto(elemento, textoFinal);
        animacionesNumero.set(elemento, { valorFinal: destino, textoFinal, frame: 0 });
        return;
      }

      const nuevaAnimacion = { valorFinal: destino, textoFinal, frame: 0 };
      animacionesNumero.set(elemento, nuevaAnimacion);
      const momentoInicial = performance.now();

      function avanzar(ahora) {
        if (animacionesNumero.get(elemento) !== nuevaAnimacion) return;
        const progreso = Math.min((ahora - momentoInicial) / 900, 1);
        const easeOut = 1 - (1 - progreso) ** 3;
        const valor = Math.round(inicio + (destino - inicio) * easeOut);
        escribirTexto(elemento, progreso === 1 ? textoFinal : formatoNumero.format(valor));

        if (progreso < 1) nuevaAnimacion.frame = requestAnimationFrame(avanzar);
      }

      nuevaAnimacion.frame = requestAnimationFrame(avanzar);
    }

    const observadorNumeros = new MutationObserver((mutaciones) => {
      mutaciones.forEach((mutacion) => {
        if (mutacion.type === 'childList') animarNumero(mutacion.target);
      });
    });

    tarjetas.forEach((tarjeta) => {
      tarjeta.querySelectorAll('.tarjeta-info h3').forEach((numero) => {
        animarNumero(numero);
        observadorNumeros.observe(numero, { childList: true });
      });
    });

    const listaAlertas = document.getElementById('lista-alertas');
    if (listaAlertas) {
      const filasAnimadas = new WeakSet();
      const observarFilas = (mutaciones) => {
        mutaciones.forEach((mutacion) => {
          mutacion.addedNodes.forEach((nodo) => {
            if (!(nodo instanceof Element)) return;
            const filas = [
              ...(nodo.matches('.dashboard-alert-row') ? [nodo] : []),
              ...nodo.querySelectorAll('.dashboard-alert-row'),
            ];
            filas.forEach((fila) => {
              if (filasAnimadas.has(fila)) return;
              filasAnimadas.add(fila);
              const indice = [...listaAlertas.querySelectorAll('.dashboard-alert-row')].indexOf(fila);
              fila.style.setProperty('--dashboard-alert-delay', `${Math.max(indice, 0) * 70}ms`);
            });
          });
        });
      };
      const observadorAlertas = new MutationObserver(observarFilas);
      observadorAlertas.observe(listaAlertas, { childList: true, subtree: true });
      observarFilas([{ addedNodes: listaAlertas.childNodes }]);
    }

    const ranking = document.getElementById('grafico-ventas');
    if (ranking) {
      const filasAnimadas = new WeakSet();
      const animarFilasRanking = () => {
        ranking.querySelectorAll('.dashboard-ranking-row').forEach((fila, indice) => {
          if (filasAnimadas.has(fila)) return;
          filasAnimadas.add(fila);
          fila.style.setProperty('--dashboard-ranking-delay', `${indice * 80}ms`);
        });
      };
      const observadorRanking = new MutationObserver(animarFilasRanking);
      observadorRanking.observe(ranking, { childList: true, subtree: true });
      animarFilasRanking();
    }
  }

  function inicializarProductos() {
    const cuerpoTabla = document.getElementById('tabla-productos-body');
    if (cuerpoTabla) {
      const filasAnimadas = new WeakSet();
      const animarFilas = (mutaciones) => {
        const filasNuevas = [];
        mutaciones.forEach((mutacion) => {
          mutacion.addedNodes.forEach((nodo) => {
            if (!(nodo instanceof Element)) return;
            if (nodo.matches('tr.productos-card-row')) filasNuevas.push(nodo);
            nodo.querySelectorAll('tr.productos-card-row').forEach((fila) => filasNuevas.push(fila));
          });
        });

        const filasActuales = [...cuerpoTabla.querySelectorAll(':scope > tr.productos-card-row')];
        filasNuevas.forEach((fila) => {
          if (filasAnimadas.has(fila)) return;
          filasAnimadas.add(fila);
          const indice = filasActuales.indexOf(fila);
          fila.style.setProperty('--productos-row-delay', `${indice >= 0 && indice < 12 ? indice * 40 : 0}ms`);
        });
      };

      const observadorTabla = new MutationObserver(animarFilas);
      observadorTabla.observe(cuerpoTabla, { childList: true });
      animarFilas([{ addedNodes: cuerpoTabla.childNodes }]);
    }

  }

  function inicializarAsistente() {
    const hilo = document.getElementById('chat-mensajes');
    if (!hilo) return;

    function animarMensajes(nodos) {
      nodos.forEach((nodo) => {
        if (!(nodo instanceof Element)) return;
        if (nodo.matches('.burbuja-wrapper')) nodo.classList.add('animacion-mensaje');
        nodo.querySelectorAll('.burbuja-wrapper').forEach((burbuja) => {
          burbuja.classList.add('animacion-mensaje');
        });
      });
    }

    animarMensajes(hilo.children);
    const observadorMensajes = new MutationObserver((mutaciones) => {
      mutaciones.forEach((mutacion) => animarMensajes(mutacion.addedNodes));
    });
    observadorMensajes.observe(hilo, { childList: true });
  }

  function inicializarAnimaciones() {
    if (document.body.classList.contains('dashboard-page')) inicializarDashboard();
    if (document.body.classList.contains('productos-page')) inicializarProductos();
    if (document.querySelector('.ai-header')) inicializarAsistente();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', inicializarAnimaciones, { once: true });
  } else {
    inicializarAnimaciones();
  }
})();
