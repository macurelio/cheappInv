package com.cheapp.cheappInv.application.commands;

public record DiscountStockCommand(
		String eventId,
		String correlationId,
		String sku,
		String warehouseId,
		long quantity,
		String reason
) {
}
