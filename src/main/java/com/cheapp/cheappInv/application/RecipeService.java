package com.cheapp.cheappInv.application;

import com.cheapp.cheappInv.application.commands.*;
import com.cheapp.cheappInv.domain.ProductNotFoundException;
import com.cheapp.cheappInv.domain.recipes.RecipeStatus;
import com.cheapp.cheappInv.infra.persistence.ProductRepository;
import com.cheapp.cheappInv.infra.persistence.RecipeEntity;
import com.cheapp.cheappInv.infra.persistence.RecipeIngredientEntity;
import com.cheapp.cheappInv.infra.persistence.RecipesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Application Service para casos de uso de recetas.
 *
 * Nota: el proyecto actual tiene dependencias directas a JPA en application (no es 100% hex estricto).
 * Mantengo el estilo existente para minimizar cambios y luego podemos extraer puertos.
 */
@Service
public class RecipeService {
	private final RecipesRepository recipesRepository;
	private final ProductRepository productRepository;
	private final Clock clock;

	public RecipeService(RecipesRepository recipesRepository, ProductRepository productRepository, Clock clock) {
		this.recipesRepository = recipesRepository;
		this.productRepository = productRepository;
		this.clock = clock;
	}

	@Transactional
	public RecipeEntity createRecipe(CreateRecipeCommand cmd) {
		Instant now = Instant.now(clock);
		String recipeId = UUID.randomUUID().toString();
		RecipeEntity e = new RecipeEntity(recipeId, cmd.dishSku(), 1, RecipeStatus.DRAFT, now);
		return recipesRepository.save(e);
	}

	@Transactional
	public RecipeEntity addIngredient(AddIngredientToRecipeCommand cmd) {
		RecipeEntity recipe = recipesRepository.findByRecipeId(cmd.recipeId())
				.orElseThrow(() -> new IllegalArgumentException("Recipe no encontrada: " + cmd.recipeId()));
		if (recipe.getStatus() != RecipeStatus.DRAFT) {
			throw new IllegalStateException("Solo se puede modificar receta DRAFT");
		}

		// Validar que el ingrediente (insumo) exista como producto
		productRepository.findBySku(cmd.ingredientSku()).orElseThrow(() -> new ProductNotFoundException(cmd.ingredientSku()));

		recipe.addIngredient(new RecipeIngredientEntity(recipe, cmd.ingredientSku(), cmd.quantity(), cmd.unitCode()));
		return recipesRepository.save(recipe);
	}

	@Transactional
	public RecipeEntity activate(ActivateRecipeCommand cmd) {
		RecipeEntity recipe = recipesRepository.findByRecipeId(cmd.recipeId())
				.orElseThrow(() -> new IllegalArgumentException("Recipe no encontrada: " + cmd.recipeId()));
		if (recipe.getIngredients().isEmpty()) {
			throw new IllegalStateException("No se puede activar una receta sin ingredientes");
		}
		// Desactivar cualquier receta activa del mismo plato
		recipesRepository.findActiveByDishSku(recipe.getDishSku()).ifPresent(active -> {
			active.setStatus(RecipeStatus.ARCHIVED);
			recipesRepository.save(active);
		});
		recipe.setStatus(RecipeStatus.ACTIVE);
		return recipesRepository.save(recipe);
	}

	@Transactional
	public RecipeEntity versionActiveRecipe(VersionRecipeCommand cmd) {
		RecipeEntity active = recipesRepository.findByRecipeId(cmd.activeRecipeId())
				.orElseThrow(() -> new IllegalArgumentException("Recipe no encontrada: " + cmd.activeRecipeId()));
		if (active.getStatus() != RecipeStatus.ACTIVE) {
			throw new IllegalStateException("Solo se puede versionar desde una receta ACTIVE");
		}
		Instant now = Instant.now(clock);
		String newRecipeId = UUID.randomUUID().toString();
		RecipeEntity draft = new RecipeEntity(newRecipeId, active.getDishSku(), active.getVersionNumber() + 1, RecipeStatus.DRAFT, now);
		for (var ing : active.getIngredients()) {
			draft.addIngredient(new RecipeIngredientEntity(draft, ing.getIngredientSku(), ing.getQuantity(), ing.getUnitCode()));
		}
		return recipesRepository.save(draft);
	}
}
