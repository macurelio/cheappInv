package com.cheapp.cheappInv.application.commands;

import jakarta.validation.constraints.NotBlank;

public record VersionRecipeCommand(
		@NotBlank String activeRecipeId,
		String correlationId
) {
}
