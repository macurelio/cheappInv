package com.cheapp.cheappInv.api;

import com.cheapp.cheappInv.application.RecipeService;
import com.cheapp.cheappInv.application.commands.*;
import com.cheapp.cheappInv.domain.UnitCode;
import com.cheapp.cheappInv.infra.logging.Loggable;
import com.cheapp.cheappInv.infra.persistence.RecipeEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Recipes", description = "API para definir recetas (BOM) por plato. La receta consume insumos (productos).")
@Loggable("recipes")
public class RecipeController {
	private final RecipeService recipeService;

	public RecipeController(RecipeService recipeService) {
		this.recipeService = recipeService;
	}

	@PostMapping("/recipes")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(
			summary = "Crear receta (DRAFT)",
			responses = {
					@ApiResponse(responseCode = "201", description = "Receta creada", content = @Content(schema = @Schema(implementation = RecipeView.class)))
			}
	)
	public RecipeView create(@RequestBody CreateRecipeRequest req, @RequestParam(required = false) String correlationId) {
		RecipeEntity e = recipeService.createRecipe(new CreateRecipeCommand(req.dishSku(), correlationId));
		return RecipeView.from(e);
	}

	@PostMapping("/recipes/{recipeId}/ingredients")
	@ResponseStatus(HttpStatus.OK)
	@Operation(
			summary = "Agregar ingrediente a receta DRAFT",
			responses = {
					@ApiResponse(responseCode = "200", description = "Ingrediente agregado", content = @Content(schema = @Schema(implementation = RecipeView.class)))
			}
	)
	public RecipeView addIngredient(@PathVariable String recipeId, @RequestBody AddIngredientRequest req, @RequestParam(required = false) String correlationId) {
		RecipeEntity e = recipeService.addIngredient(new AddIngredientToRecipeCommand(recipeId, req.ingredientSku(), req.quantity(), req.unitCode(), correlationId));
		return RecipeView.from(e);
	}

	@PostMapping("/recipes/{recipeId}/activate")
	@ResponseStatus(HttpStatus.OK)
	@Operation(summary = "Activar receta")
	public RecipeView activate(@PathVariable String recipeId, @RequestParam(required = false) String correlationId) {
		RecipeEntity e = recipeService.activate(new ActivateRecipeCommand(recipeId, correlationId));
		return RecipeView.from(e);
	}

	@PostMapping("/recipes/{recipeId}/version")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Versionar receta activa -> crea nueva DRAFT")
	public RecipeView version(@PathVariable String recipeId, @RequestParam(required = false) String correlationId) {
		RecipeEntity e = recipeService.versionActiveRecipe(new VersionRecipeCommand(recipeId, correlationId));
		return RecipeView.from(e);
	}

	public record CreateRecipeRequest(@NotBlank String dishSku) {
	}

	public record AddIngredientRequest(@NotBlank String ingredientSku, long quantity, UnitCode unitCode) {
	}

	public record RecipeIngredientView(String ingredientSku, long quantity, String unitCode) {
	}

	public record RecipeView(String recipeId, String dishSku, int version, String status, List<RecipeIngredientView> ingredients) {
		static RecipeView from(RecipeEntity e) {
			List<RecipeIngredientView> ings = e.getIngredients().stream()
					.map(i -> new RecipeIngredientView(i.getIngredientSku(), i.getQuantity(), i.getUnitCode() == null ? null : i.getUnitCode().name()))
					.toList();
			return new RecipeView(e.getRecipeId(), e.getDishSku(), e.getVersionNumber(), e.getStatus().name(), ings);
		}
	}
}
