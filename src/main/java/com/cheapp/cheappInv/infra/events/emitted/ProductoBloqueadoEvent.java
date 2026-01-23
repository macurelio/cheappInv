package com.cheapp.cheappInv.infra.events.emitted;

public record ProductoBloqueadoEvent(
		String sku,
		String reason
) {
}
