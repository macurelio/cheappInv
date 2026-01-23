# cheappInv – Inventory Service

Microservicio de inventario (Java 21 + Spring Boot) orientado a consistencia de stock.

## Dominio
- **Producto**: `ACTIVE | BLOCKED`
- **Stock**: cantidad por `sku` + `warehouseId`
- **MovimientoInventario**: ledger inmutable de créditos/débitos

## Consistencia
- La **base de datos es la única fuente de verdad**.
- El descuento/reposición se ejecuta en **una transacción**.
- Se usa **locking pesimista** sobre el registro de stock para evitar condiciones de carrera.
- Consumo de eventos **idempotente** vía tabla `inbox_events`.
- Emisión de eventos con **outbox** (`outbox_events`) + scheduler.

## Endpoints (mínimos)
- `POST /api/products/{sku}/block`
- `POST /api/products/{sku}/reactivate`
- `GET /api/products/{sku}`
- `GET /api/stock?sku=...&warehouseId=MAIN`

### Simulación de eventos consumidos (opcional)
- `POST /api/events/in/item-agregado`
- `POST /api/events/in/pedido-proveedor-recibido`

Payload ejemplo:
```json
{
  "eventId": "evt-123",
  "correlationId": "corr-1",
  "sku": "SKU-1",
  "quantity": 2,
  "warehouseId": "MAIN"
}
```

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
