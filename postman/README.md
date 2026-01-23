# Postman

## Colección
- `CheappInv.postman_collection.json`

### Variables incluidas
- `baseUrl` (default: `http://localhost:9090`)
- `sku`
- `warehouseId` (default: `MAIN`)
- `correlationId`
- `reason`
- `eventId`
- `quantity`

## Importar en Postman
1. Postman → **Import** → selecciona `postman/CheappInv.postman_collection.json`.
2. (Opcional) En la colección, ajusta las variables si tu servicio corre en otro puerto/host.

## Notas de comportamiento
- `GET /api/products/{sku}` y `GET /api/stock` devuelven `null` si el producto no existe actualmente.
- Los endpoints `/api/events/in/*` aceptan el evento y pueden devolver `202` también en caso de duplicado (`DUPLICATE_EVENT`).
