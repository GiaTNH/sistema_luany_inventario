# Pruebas Completas de Endpoints de la API - Luany Inventario

Documentación técnica integral para la verificación de todos los endpoints de la API REST local. Incluye comandos para **PowerShell** e **cURL**, payloads de entrada (JSON) y las respuestas HTTP esperadas.

## 1. Módulo de Productos (`/api/productos`)

### 1.1 Listar todos los productos
* **Método:** `GET`
* **Ruta:** `http://localhost:8080/api/productos`

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/productos" -Method Get
```

**cURL:**
```bash
curl -X GET http://localhost:8080/api/productos
```

**Respuesta (`200 OK`):**
```json
[
  {
    "id": 1,
    "nombre": "Producto 1",
    "categoria": { "id": 1, "nombre": "Categoria A" },
    "proveedor": { "id": 1, "nombre": "Proveedor Uno", "telefono": "999111222" },
    "stockActual": 50,
    "stockMinimo": 10,
    "stockMaximo": 100,
    "precio": 12.50
  },
  {
    "id": 2,
    "nombre": "Producto 2",
    "categoria": { "id": 1, "nombre": "Categoria A" },
    "proveedor": { "id": 1, "nombre": "Proveedor Uno", "telefono": "999111222" },
    "stockActual": 8,
    "stockMinimo": 10,
    "stockMaximo": 100,
    "precio": 7.00
  }
]
```

### 1.2 Obtener producto por ID
* **Método:** `GET`
* **Ruta:** `http://localhost:8080/api/productos/{id}`

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/productos/1" -Method Get
```

**cURL:**
```bash
curl -X GET http://localhost:8080/api/productos/1
```

### 1.3 Crear un nuevo producto
* **Método:** `POST`
* **Ruta:** `http://localhost:8080/api/productos`

**PowerShell:**
```powershell
$body = @{
    nombre = "Laptop Gamer Asus"
    categoriaId = 1
    proveedorId = 1
    stockActual = 15
    stockMinimo = 5
    stockMaximo = 30
    precio = 3500.00
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/productos" -Method Post -Body $body -ContentType "application/json"
```

**cURL:**
```bash
curl -X POST http://localhost:8080/api/productos \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Laptop Gamer Asus","categoriaId":1,"proveedorId":1,"stockActual":15,"stockMinimo":5,"stockMaximo":30,"precio":3500.00}'
```

**Respuesta (`201 Created`):**
```json
{
  "id": 6,
  "nombre": "Laptop Gamer Asus",
  "categoria": { "id": 1, "nombre": "Categoria A" },
  "proveedor": { "id": 1, "nombre": "Proveedor Uno", "telefono": "999111222" },
  "stockActual": 15,
  "stockMinimo": 5,
  "stockMaximo": 30,
  "precio": 3500.00
}
```

### 1.4 Actualizar un producto
* **Método:** `PUT`
* **Ruta:** `http://localhost:8080/api/productos/{id}`

**PowerShell:**
```powershell
$body = @{
    nombre = "Laptop Gamer Asus Pro"
    categoriaId = 1
    proveedorId = 1
    stockActual = 20
    stockMinimo = 5
    stockMaximo = 30
    precio = 3800.00
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/productos/6" -Method Put -Body $body -ContentType "application/json"
```

### 1.5 Eliminar un producto
* **Método:** `DELETE`
* **Ruta:** `http://localhost:8080/api/productos/{id}`

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/productos/6" -Method Delete
```

**cURL:**
```bash
curl -X DELETE http://localhost:8080/api/productos/6
```

---

## 2. Módulo de Categorías (`/api/categorias`)

### 2.1 Listar todas las categorías
* **Método:** `GET`
* **Ruta:** `http://localhost:8080/api/categorias`

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/categorias" -Method Get
```

**cURL:**
```bash
curl -X GET http://localhost:8080/api/categorias
```

**Respuesta (`200 OK`):**
```json
[
  { "id": 1, "nombre": "Categoria A" },
  { "id": 2, "nombre": "Categoria B" }
]
```

### 2.2 Obtener categoría por ID
* **Método:** `GET`
* **Ruta:** `http://localhost:8080/api/categorias/{id}`

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/categorias/1" -Method Get
```

### 2.3 Crear una categoría
* **Método:** `POST`
* **Ruta:** `http://localhost:8080/api/categorias`

**PowerShell:**
```powershell
$body = @{ nombre = "Electrónica y Cómputo" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/categorias" -Method Post -Body $body -ContentType "application/json"
```

### 2.4 Actualizar una categoría
* **Método:** `PUT`
* **Ruta:** `http://localhost:8080/api/categorias/{id}`

**PowerShell:**
```powershell
$body = @{ nombre = "Hardware y Laptops" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/categorias/1" -Method Put -Body $body -ContentType "application/json"
```

### 2.5 Eliminar una categoría
* **Método:** `DELETE`
* **Ruta:** `http://localhost:8080/api/categorias/{id}`

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/categorias/3" -Method Delete
```

---

## 3. Módulo de Proveedores (`/api/proveedores`)

### 3.1 Listar todos los proveedores
* **Método:** `GET`
* **Ruta:** `http://localhost:8080/api/proveedores`

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/proveedores" -Method Get
```

### 3.2 Obtener proveedor por ID
* **Método:** `GET`
* **Ruta:** `http://localhost:8080/api/proveedores/{id}`

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/proveedores/1" -Method Get
```

### 3.3 Crear un proveedor
* **Método:** `POST`
* **Ruta:** `http://localhost:8080/api/proveedores`

**PowerShell:**
```powershell
$body = @{
    nombre = "Distribuidora Tech SAC"
    telefono = "987654321"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/proveedores" -Method Post -Body $body -ContentType "application/json"
```

### 3.4 Actualizar un proveedor
* **Método:** `PUT`
* **Ruta:** `http://localhost:8080/api/proveedores/{id}`

**PowerShell:**
```powershell
$body = @{
    nombre = "Distribuidora Tech SAC - Central"
    telefono = "999888777"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/proveedores/1" -Method Put -Body $body -ContentType "application/json"
```

### 3.5 Eliminar un proveedor
* **Método:** `DELETE`
* **Ruta:** `http://localhost:8080/api/proveedores/{id}`

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/proveedores/3" -Method Delete
```

---

## 4. Módulo de Alertas de Stock (`/api/alertas`)

### 4.1 Consultar productos en estado crítico de stock
* **Método:** `GET`
* **Ruta:** `http://localhost:8080/api/alertas/stock`

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/alertas/stock" -Method Get
```

**cURL:**
```bash
curl -X GET http://localhost:8080/api/alertas/stock
```

**Respuesta (`200 OK`):**
```json
[
  {
    "productoId": 2,
    "nombreProducto": "Producto 2",
    "stockActual": 8,
    "stockMinimo": 10,
    "mensaje": "El stock actual (8) está por debajo del mínimo (10)"
  },
  {
    "productoId": 3,
    "nombreProducto": "Producto 3",
    "stockActual": 0,
    "stockMinimo": 5,
    "mensaje": "El stock actual (0) está por debajo del mínimo (5)"
  }
]
```

---

## 5. Módulo de Movimientos de Inventario (`/api/movimientos`)

### 5.1 Consultar historial completo de movimientos
* **Método:** `GET`
* **Ruta:** `http://localhost:8080/api/movimientos`

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/movimientos" -Method Get
```

### 5.2 Registrar un movimiento (Entrada / Salida / Venta)
* **Método:** `POST`
* **Ruta:** `http://localhost:8080/api/movimientos`

**PowerShell:**
```powershell
$body = @{
    productoId = 1
    cantidad = 10
    tipo = "ENTRADA"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/movimientos" -Method Post -Body $body -ContentType "application/json"
```

---

## 6. Pruebas de Respuestas de Error (`ManejadorErrores`)

### 6.1 Recurso no encontrado (`404 Not Found`)
**Comando:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/ruta-inexistente" -Method Get
```

**Respuesta (`404 Not Found`):**
```json
{
  "timestamp": "2026-10-28T01:46:30.184Z",
  "status": 404,
  "error": "Not Found",
  "path": "/api/ruta-inexistente"
}
```