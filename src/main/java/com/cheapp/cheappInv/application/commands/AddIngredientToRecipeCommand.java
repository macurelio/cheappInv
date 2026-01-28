package com.cheapp.cheappInv.application.commands;

import com.cheapp.cheappInv.domain.UnitCode;
import jakarta.validation.constraints.NotBlank;

public record AddIngredientToRecipeCommand(
		@NotBlank String recipeId,
		@NotBlank String ingredientSku,
		long quantity,
		UnitCode unitCode,
		String correlationId
) {
}
