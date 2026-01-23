package com.cheapp.cheappInv.infra.events.emitted;

public record StockRepuestoEvent(
		String sku,
		String warehouseId,
		long quantity,
		long newStock
) {
}
