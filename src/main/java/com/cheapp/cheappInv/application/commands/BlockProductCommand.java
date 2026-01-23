package com.cheapp.cheappInv.application.commands;

public record BlockProductCommand(
		String correlationId,
		String sku,
		String reason
) {
}
