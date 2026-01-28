package com.cheapp.cheappInv.infra.events.emitted;

public record StockInsuficienteEvent(
		String comandaId,
		String sku,
		String warehouseId,
		long requested,
		long available
) {
}
