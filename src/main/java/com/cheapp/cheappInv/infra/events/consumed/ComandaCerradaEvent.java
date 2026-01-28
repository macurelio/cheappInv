package com.cheapp.cheappInv.infra.events.consumed;

import java.util.List;

/**
 * Evento consumido: ComandaCerrada.
 *
 * Este MS no conoce comandas internamente; solo reacciona al cierre y descuenta stock por recetas.
 */
public record ComandaCerradaEvent(
		String eventId,
		String correlationId,
		String comandaId,
		List<DishLine> dishes,
		String warehouseId
) {
	public record DishLine(
			String dishSku,
			long quantity
	) {
	}
}
