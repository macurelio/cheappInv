package com.cheapp.cheappInv.infra.events.consumed;

public record ItemAgregadoEvent(
		String eventId,
		String correlationId,
		String sku,
		long quantity,
		String warehouseId
) {
}
