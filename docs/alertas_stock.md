# Alertas de Stock Bajo y Estado Crítico

El sistema evalúa de manera automática los niveles de inventario de cada producto comparando el **stock actual** con los parámetros definidos (**stock mínimo** y **stock máximo**):

1. **Estado OK:** El stock actual supera el mínimo establecido.
2. **Stock Bajo:** El stock actual es menor o igual al stock mínimo, lo que genera una alerta visual en el Dashboard y avisa al reponedor que debe planificar una compra.
3. **Estado Crítico:** El stock actual llega a niveles mínimos alarmantes o a cero (0), indicando una urgencia de reposición inmediata con los proveedores asociados (por ejemplo, *Textiles Andina* o *Modas Rosita*).
