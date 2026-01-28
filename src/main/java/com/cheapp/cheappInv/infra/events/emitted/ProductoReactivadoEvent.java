package com.cheapp.cheappInv.infra.events.emitted;

public record ProductoReactivadoEvent(
		String sku,
		String reason
) {
}
