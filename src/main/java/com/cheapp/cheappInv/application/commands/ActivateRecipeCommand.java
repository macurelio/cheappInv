package com.cheapp.cheappInv.application.commands;

import jakarta.validation.constraints.NotBlank;

public record ActivateRecipeCommand(
		@NotBlank String recipeId,
		String correlationId
) {
}
