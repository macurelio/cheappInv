package com.cheapp.cheappInv.infra.events.emitted;

public record StockDescontadoEvent(
		String sku,
		String warehouseId,
		long quantity,
		long newStock
) {
}
