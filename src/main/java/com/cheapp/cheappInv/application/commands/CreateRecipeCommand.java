package com.cheapp.cheappInv.application.commands;

import jakarta.validation.constraints.NotBlank;

public record CreateRecipeCommand(
		@NotBlank String dishSku,
		String correlationId
) {
}
