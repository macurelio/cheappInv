# cheappInv – Microservicio de Inventario (Inventory Service)

Este documento describe **cómo funciona** el microservicio `cheappInv`: qué problema resuelve, cómo mantiene consistencia de stock y cómo integrarse vía HTTP y eventos.

## 1) Qué hace

`cheappInv` gestiona:

- **Productos** identificados por `sku`.
- **Stock** por `sku` y `warehouseId`.
- Un **ledger de movimientos** (créditos/débitos) para auditar cambios de stock.
- **Consumo idempotente** de eventos de entrada.
- **Emisión de eventos** mediante patrón **Outbox**.

Objetivo principal: **descontar y reponer stock de forma consistente** bajo concurrencia.

## 2) Stack y ejecución

- Java **21**
- Spring Boot **4.x**
- Spring Web, Spring Data JPA, Validation, Actuator
- Base de datos: **H2 en memoria** (configurada en modo compatible PostgreSQL)

Configuración principal en `src/main/resources/application.yaml`:

- `spring.datasource.url=jdbc:h2:mem:cheappInv;DB_CLOSE_DELAY=-1;MODE=PostgreSQL`
- `spring.jpa.hibernate.ddl-auto=update` (crea/actualiza tablas automáticamente)
- Outbox:
  - `inventory.outbox.enabled` (default `true`)
  - `inventory.outbox.poll-interval` (default `2s`)
  - `inventory.outbox.batch-size` (default `50`)
- `inventory.default-warehouse-id` (default `MAIN`)

### Base de datos: ¿es necesaria?

Sí. Este microservicio persiste **productos**, **stock**, **movimientos**, **inbox** y **outbox** usando **Spring Data JPA**.

- En desarrollo, está configurado para usar **H2 en memoria** (por defecto), por eso “funciona sin instalar nada”.
- Para un entorno persistente (dev compartido / staging / producción) se recomienda **PostgreSQL**.

#### Script PostgreSQL (DBeaver)

En el repo tienes un script listo para ejecutar en PostgreSQL:

- `db/postgres/init.sql`

Crea las tablas: `products`, `stock`, `inventory_movements`, `inbox_events`, `outbox_events` con sus índices/constraints.

#### Seed PostgreSQL (datos demo)

Además del init, hay un seed (útil para UI/QA local):

- `db/postgres/seed.sql`

> Nota: este seed inserta categorías y productos con relaciones, por lo que es común tener productos con **varias categorías**.

#### Configuración rápida para usar PostgreSQL (ejemplo)

Ejemplo de properties (puedes ponerlo en un `application-postgres.yaml` o variables de entorno):

- `spring.datasource.url=jdbc:postgresql://localhost:5432/cheappinv`
- `spring.datasource.username=cheappinv_user`
- `spring.datasource.password=cheappinv_pass`
- `spring.jpa.hibernate.ddl-auto=validate` (recomendado en prod)

## 3) Modelo de dominio (conceptos)

### Producto
- Estado: `ACTIVE` o `BLOCKED` (`ProductStatus`).
- Si un producto está `BLOCKED`, no se permite descontar stock.

### Stock
- Se guarda por `(productId, warehouseId)`.
- Se actualiza dentro de transacciones.

### Movimiento de inventario (ledger)
- Registro inmutable por operación:
  - `MovementType`: `DEBIT` (descontar) o `CREDIT` (reponer)
  - `quantity`, `reason`, `eventId`, `correlationId`, `occurredAt`

## 4) Consistencia, concurrencia e idempotencia

### 4.1 Transacciones y locking
Las operaciones críticas (`descontarStockPorItem` y `reponerStock`) están anotadas con `@Transactional`.

Para evitar race conditions cuando hay muchos descuentos/reposiciones concurrentes, la lectura del stock se hace con **locking pesimista**:

- `StockRepository.findForUpdate(...)` usa `@Lock(PESSIMISTIC_WRITE)`.

Esto asegura que, para un mismo `(producto, warehouse)`, solo una transacción pueda modificar el stock a la vez.

### 4.2 Reglas al descontar stock
Ruta principal: `InventoryService.descontarStockPorItem(DiscountStockCommand)`.

Validaciones y reglas:

1. `quantity > 0` (si no, `IllegalArgumentException`).
2. **Idempotencia**: si llega `eventId` y ya fue procesado, se rechaza con `IdempotencyViolationException`.
3. El producto debe existir y estar `ACTIVE` (si no, `ProductNotFoundException` o `ProductBlockedException`).
4. Se carga/crea el registro de stock (si no existe, se crea con 0).
5. Se valida que `stock >= quantity` (si no, `StockInsufficientException`).
6. Se descuenta el stock y se registra un movimiento `DEBIT`.
7. Se registra el `eventId` en `inbox_events` (si aplica).
8. Se escribe un evento en `outbox_events` (`StockDescontado`).

### 4.3 Reglas al reponer stock
Ruta: `InventoryService.reponerStock(RestockCommand)`.

1. `quantity > 0`.
2. Idempotencia por `eventId`.
3. Producto debe existir.
4. Se carga/crea stock y se incrementa.
5. Movimiento `CREDIT`.
6. Se registra el evento en `inbox_events`.
7. Se escribe evento outbox `StockRepuesto`.

## 5) Patrón Inbox/Outbox

### Inbox (idempotencia de consumo)
Cuando el microservicio “consume” un evento de entrada con `eventId`, lo persiste en una tabla de inbox (`inbox_events`).

- Si vuelve a llegar el mismo `eventId`, se considera duplicado y la operación se aborta.
- Esto evita dobles descuentos/reposiciones por reintentos del bus de mensajes.

En API REST, este caso se mapea a:

- HTTP **202 Accepted** con error `DUPLICATE_EVENT` (ver `RestExceptionHandler`).

### Outbox (emisión confiable)
Cuando una operación cambia el estado del dominio, se guarda un “evento a publicar” en `outbox_events` en la **misma transacción**.

- Escritos desde `InventoryService.writeOutbox(...)`.
- Publicados periódicamente por `OutboxScheduler`.

En este proyecto el publisher real está simulado:

- `LoggingOutboxPublisher` registra el “publish” por log.

En un entorno real, `OutboxPublisher` se implementaría para enviar a Kafka/Rabbit/SQS, etc.

## 6) Endpoints HTTP

Todos bajo prefijo `/api`.

### 6.1 Catálogo (lectura para UI)

#### Listar productos (catálogo)
- `GET /api/products`

Parámetros:
- `query` (opcional): búsqueda por `sku` o `name`.
- `categoryCode` (opcional)
- `status` (opcional): `ACTIVE|BLOCKED`
- `warehouseId` (default: `MAIN`) para calcular stock
- `inStockOnly` (default: `false`)
- `page` (default: `0`)
- `size` (default: `20`)

##### Flujo interno (importante en PostgreSQL)

Para soportar paginación + categorías sin problemas en Postgres (y evitar duplicados), el endpoint sigue un flujo **en 2 pasos**:

1) **Consulta de IDs paginados** (sin `fetch join`):
   - Obtiene los IDs de producto en orden estable (`name/sku`, luego `id`).
   - Como el filtro puede incluir `left join` a categorías, se aplica **deduplicación** de IDs (un producto puede pertenecer a varias categorías).

2) **Fetch de entidades por IDs** (con `left join fetch p.categories`):
   - Se traen los `ProductEntity` con sus categorías.
   - Se arma un mapa `id -> ProductEntity` tolerante a duplicados (Hibernate puede repetir filas por el `fetch join`).
   - Se reordena según la lista de IDs para mantener el orden correcto de la página.

3) **Enriquecimiento**:
   - Se consulta stock por producto para `warehouseId`.
   - Se calcula `lastRequestedAt` como el último `CREDIT` (última reposición) por producto.
   - Si existe `estimatedShelfLifeDays`, se calcula `estimatedExpiryAt`.

> Este endpoint tuvo una regresión histórica típica: si un producto tenía varias categorías, podía explotar con `Duplicate key ...` al construir el mapa. Ahora el flujo deduplica y el código tolera duplicados.

### 6.2 Gestión de producto

#### Bloquear producto
- `POST /api/products/{sku}/block`
- Query params opcionales:
  - `correlationId`
  - `reason`
- Respuesta: **204 No Content**

Efecto:
- Cambia estado del producto a `BLOCKED`.
- Emite evento outbox: `ProductoBloqueado`.

#### Reactivar producto
- `POST /api/products/{sku}/reactivate`
- Query params opcionales:
  - `correlationId`
  - `reason`
- Respuesta: **204 No Content**

Efecto:
- Cambia estado del producto a `ACTIVE`.
- No emite evento (según comentario del código).

#### Consultar producto
- `GET /api/products/{sku}`
- Respuesta:
  - `200 OK` con `{ "sku": "...", "status": "ACTIVE|BLOCKED" }`
  - Si no existe, actualmente devuelve `null` (Spring lo serializa como respuesta vacía). En un API productivo sería preferible `404`.

### 6.3 Consulta de stock

- `GET /api/stock?sku=...&warehouseId=MAIN`
- `warehouseId` por defecto: `MAIN`

Respuesta:
- `{ "sku": "SKU-1", "warehouseId": "MAIN", "quantity": 10 }`
- Si el producto existe pero no hay stock, devuelve `quantity=0`.
- Si el producto no existe, actualmente devuelve `null`.

### 6.4 Simulación de consumo de eventos (opcional)

Estos endpoints simulan lo que en producción sería un consumidor de mensajería.

Prefijo: `/api/events/in`

#### Item agregado (descuento)
- `POST /api/events/in/item-agregado`

Body (`ItemAgregadoEvent`):
```json
{
  "eventId": "evt-123",
  "correlationId": "corr-1",
  "sku": "SKU-1",
  "quantity": 2,
  "warehouseId": "MAIN"
}
```

Efecto:
- Asegura que exista el producto (lo crea si no existe).
- Descuenta stock.
- Registra inbox y outbox.

Respuesta: **202 Accepted**

#### Pedido de proveedor recibido (reposición)
- `POST /api/events/in/pedido-proveedor-recibido`

Body (`PedidoProveedorRecibidoEvent`):
```json
{
  "eventId": "evt-124",
  "correlationId": "corr-1",
  "sku": "SKU-1",
  "quantity": 5,
  "warehouseId": "MAIN"
}
```

Respuesta: **202 Accepted**

## 7) Errores y códigos HTTP

Manejados en `RestExceptionHandler`:

- `ProductNotFoundException` → **404** `PRODUCT_NOT_FOUND`
- `ProductBlockedException` → **409** `PRODUCT_BLOCKED`
- `StockInsufficientException` → **409** `STOCK_INSUFFICIENT`
- `IdempotencyViolationException` → **202** `DUPLICATE_EVENT`
- `IllegalArgumentException` → **400** `BAD_REQUEST`

Formato de error:
```json
{
  "code": "STOCK_INSUFFICIENT",
  "message": "..."
}
```

## 8) Flujos típicos

### Flujo A: reposición y luego descuento
1. `POST /api/events/in/pedido-proveedor-recibido` con `quantity=10`.
2. Consultar `GET /api/stock?sku=...` → `quantity=10`.
3. `POST /api/events/in/item-agregado` con `quantity=3`.
4. Consultar stock → `quantity=7`.
5. Ver logs → líneas `OUTBOX publish eventType=StockRepuesto/StockDescontado ...`.

### Flujo B: idempotencia ante reintentos
1. Enviar `POST /api/events/in/item-agregado` con `eventId=evt-1`.
2. Reenviar el mismo request con el mismo `eventId`.
3. Resultado esperado: **202** con `DUPLICATE_EVENT`.

### Flujo C: catálogo con filtros y productos multi-categoría
1. Crear/seedear un producto con múltiples categorías (el `seed.sql` ya lo hace en Postgres).
2. `GET /api/products?warehouseId=MAIN&page=0&size=20`
3. Resultado esperado: **200** (no debe ocurrir `Duplicate key ...`).

## 9) Observabilidad

- Actuator expone `health` e `info`:
  - `/actuator/health`
  - `/actuator/info`

## 9.1) Swagger / OpenAPI

El proyecto expone documentación OpenAPI y una UI interactiva.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

> Nota: si tu IDE/entorno redirige `/swagger-ui.html`, normalmente apunta a `/swagger-ui/index.html`.

### Exportar OpenAPI a archivo YAML

Además del endpoint `GET /v3/api-docs.yaml`, el repo incluye un archivo `openapi.yaml` y un script para regenerarlo.

- Archivo: `openapi.yaml`
- Script: `scripts/export-openapi.sh`

Regeneración (local):

```bash
./mvnw -DskipTests package
./scripts/export-openapi.sh
```

Esto levanta el servicio en un puerto temporal (por defecto `9090`), descarga `/v3/api-docs.yaml` y lo guarda en `openapi.yaml`.

## 10) Tests (cómo verificamos el flujo)

El repositorio incluye tests unitarios e integración.

### 10.1 Unit tests (dominio/aplicación)
- `InventoryServiceTest` cubre:
  - `reponerStock` crea stock si no existe y escribe outbox/inbox
  - `descontarStock` falla si no hay suficiente
  - bloqueo e idempotencia

### 10.2 Integration tests (HTTP + Postgres)
- `CatalogControllerIntegrationTest` cubre:
  - `/api/products` con filtros por categoría + campos calculados (`lastRequestedAt`, `estimatedExpiryAt`)
  - `/api/products` con producto con **múltiples categorías** (regresión de `Duplicate key`)
  - `/api/categories/{code}/products`

- `InventoryServiceIntegrationTest` cubre:
  - reposición + descuento afectan stock y outbox
  - caso de stock insuficiente

### 10.3 Ejecutar tests

```bash
./mvnw test
```

## 11) Notas y mejoras sugeridas (opcional)

- Los `GET` que retornan `null` podrían migrarse a `404` para consistencia del contrato HTTP.
- Implementar un `OutboxPublisher` real (Kafka/Rabbit/SQS) manteniendo el scheduler y la tabla outbox.
- Añadir documentación OpenAPI/Swagger.
