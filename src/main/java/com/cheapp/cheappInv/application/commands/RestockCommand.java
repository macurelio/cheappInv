package com.cheapp.cheappInv.application.commands;

public record RestockCommand(
		String eventId,
		String correlationId,
		String sku,
		String warehouseId,
		long quantity,
		String reason
) {
}
