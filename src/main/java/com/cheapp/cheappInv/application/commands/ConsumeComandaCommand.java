package com.cheapp.cheappInv.application.commands;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ConsumeComandaCommand(
		String eventId,
		String correlationId,
		@NotBlank String comandaId,
		List<ComandaDishLine> dishes,
		String warehouseId
) {
	public record ComandaDishLine(
			@NotBlank String dishSku,
			long quantity
	) {
	}
}
