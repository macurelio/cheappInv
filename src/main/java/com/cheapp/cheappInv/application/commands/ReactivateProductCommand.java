package com.cheapp.cheappInv.application.commands;

public record ReactivateProductCommand(
		String correlationId,
		String sku,
		String reason
) {
}
