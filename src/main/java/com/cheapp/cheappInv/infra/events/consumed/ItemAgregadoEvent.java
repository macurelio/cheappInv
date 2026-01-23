package com.cheapp.cheappInv.infra.events.consumed;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ItemAgregadoEvent", description = "Evento de entrada: se agregó un ítem a una orden/pedido y debe descontarse stock.")
public record ItemAgregadoEvent(
		@Schema(description = "ID único del evento (para idempotencia)", example = "evt-123") String eventId,
		@Schema(description = "Correlation ID para trazabilidad", example = "corr-1") String correlationId,
		@Schema(description = "SKU del producto", example = "SKU-1") String sku,
		@Schema(description = "Cantidad a descontar", example = "2", minimum = "1") long quantity,
		@Schema(description = "Warehouse/almacén (si no viene, se usa el default)", example = "MAIN") String warehouseId
) {
}
