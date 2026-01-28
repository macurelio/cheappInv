# cheappInv – Inventory Service

Microservicio de inventario (Java 21 + Spring Boot) orientado a consistencia de stock.

## Dominio
- **Producto**: `ACTIVE | BLOCKED`
- **Stock**: cantidad por `sku` + `warehouseId`
- **MovimientoInventario**: ledger inmutable de créditos/débitos
- **Receta (BOM)**: define insumos por plato (para consumo automático en cierre de comanda)

## Consistencia
- La **base de datos es la única fuente de verdad**.
- El descuento/reposición se ejecuta en **una transacción**.
- Se usa **locking pesimista** sobre el registro de stock para evitar condiciones de carrera.
- Consumo de eventos **idempotente** vía tabla `inbox_events`.
- Emisión de eventos con **outbox** (`outbox_events`) + scheduler.

## Endpoints

### Inventario (gestión + consultas)
- `POST /api/products/{sku}/block`
- `POST /api/products/{sku}/reactivate`
- `GET /api/products/{sku}`
- `GET /api/stock?sku=...&warehouseId=MAIN`

### Catálogo (lectura para UI)
- `GET /api/products` (paginado + filtros)

### Categorías
- `GET /api/categories`
- `GET /api/categories/{code}/products` (paginado)

### Recetas
- `POST /api/recipes`
- `POST /api/recipes/{recipeId}/ingredients`
- `POST /api/recipes/{recipeId}/activate`
- `POST /api/recipes/{recipeId}/version`

### Consumo histórico
- `GET /api/consumption/average?sku=...&windowDays=30`

### Simulación de eventos consumidos (opcional)
- `POST /api/events/in/item-agregado`
- `POST /api/events/in/pedido-proveedor-recibido`
- `POST /api/events/in/comanda-cerrada`

Payload ejemplo (evento tipo ItemAgregado/PedidoProveedorRecibido):
```json
{
  "eventId": "evt-123",
  "correlationId": "corr-1",
  "sku": "SKU-1",
  "quantity": 2,
  "warehouseId": "MAIN"
}
```

## Swagger / OpenAPI
- Archivo: `openapi.yaml`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Eventos emitidos
El ms genera eventos en `outbox_events` y el scheduler los “publica” por log:
- `StockDescontado`
- `ProductoBloqueado`
- `StockRepuesto`

## Ejecutar
```bash
./mvnw test
./mvnw spring-boot:run
```
