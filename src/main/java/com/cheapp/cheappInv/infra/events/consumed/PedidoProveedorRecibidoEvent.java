package com.cheapp.cheappInv.infra.events.consumed;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PedidoProveedorRecibidoEvent", description = "Evento de entrada: llegó mercadería del proveedor y debe reponerse stock.")
public record PedidoProveedorRecibidoEvent(
		@Schema(description = "ID único del evento (para idempotencia)", example = "evt-124") String eventId,
		@Schema(description = "Correlation ID para trazabilidad", example = "corr-1") String correlationId,
		@Schema(description = "SKU del producto", example = "SKU-1") String sku,
		@Schema(description = "Cantidad a reponer", example = "5", minimum = "1") long quantity,
		@Schema(description = "Warehouse/almacén (si no viene, se usa el default)", example = "MAIN") String warehouseId
) {
}
