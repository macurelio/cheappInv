package com.cheapp.cheappInv.domain.recipes;

import com.cheapp.cheappInv.domain.UnitCode;

import java.time.Instant;
import java.util.*;

/**
 * Aggregate Root: Recipe.
 *
 * Una receta est asociada a un plato (identificado por dishSku).
 * Se versiona: cada versin tiene un nmero incremental; solo una versin puede estar ACTIVE.
 */
public class Recipe {
	private final String recipeId;
	private final String dishSku;
	private final int version;
	private RecipeStatus status;
	private final List<RecipeIngredient> ingredients;
	private final Instant createdAt;

	public Recipe(String recipeId, String dishSku, int version, RecipeStatus status, List<RecipeIngredient> ingredients, Instant createdAt) {
		this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
		this.dishSku = requireNonBlank(dishSku, "dishSku");
		if (version <= 0) {
			throw new IllegalArgumentException("version debe ser >= 1");
		}
		this.version = version;
		this.status = Objects.requireNonNull(status, "status");
		this.ingredients = new ArrayList<>(Objects.requireNonNull(ingredients, "ingredients"));
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		validateNoDuplicateIngredients();
	}

	public static Recipe newDraft(String recipeId, String dishSku, Instant now) {
		return new Recipe(recipeId, dishSku, 1, RecipeStatus.DRAFT, List.of(), now);
	}

	public void addIngredient(String ingredientSku, long quantity, UnitCode unitCode) {
		ensureMutable();
		this.ingredients.add(new RecipeIngredient(ingredientSku, quantity, unitCode));
		validateNoDuplicateIngredients();
	}

	public Recipe versionCopyAsDraft(String newRecipeId, Instant now) {
		if (this.status != RecipeStatus.ACTIVE) {
			throw new IllegalStateException("Solo se puede versionar desde una receta ACTIVE");
		}
		return new Recipe(newRecipeId, dishSku, version + 1, RecipeStatus.DRAFT, ingredients, now);
	}

	public void activate() {
		if (ingredients.isEmpty()) {
			throw new IllegalStateException("No se puede activar una receta sin ingredientes");
		}
		if (status == RecipeStatus.ARCHIVED) {
			throw new IllegalStateException("No se puede activar una receta ARCHIVED");
		}
		status = RecipeStatus.ACTIVE;
	}

	public void archive() {
		status = RecipeStatus.ARCHIVED;
	}

	public String recipeId() {
		return recipeId;
	}

	public String dishSku() {
		return dishSku;
	}

	public int version() {
		return version;
	}

	public RecipeStatus status() {
		return status;
	}

	public List<RecipeIngredient> ingredients() {
		return Collections.unmodifiableList(ingredients);
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Map<String, Long> ingredientsConsumptionForDishQuantity(long dishQuantity) {
		if (dishQuantity <= 0) {
			throw new IllegalArgumentException("dishQuantity debe ser > 0");
		}
		Map<String, Long> totals = new HashMap<>();
		for (RecipeIngredient i : ingredients) {
			long consumed = Math.multiplyExact(i.quantity(), dishQuantity);
			totals.merge(i.ingredientSku(), consumed, Math::addExact);
		}
		return totals;
	}

	private void ensureMutable() {
		if (status != RecipeStatus.DRAFT) {
			throw new IllegalStateException("Solo se puede modificar una receta en estado DRAFT");
		}
	}

	private void validateNoDuplicateIngredients() {
		Set<String> seen = new HashSet<>();
		for (RecipeIngredient i : ingredients) {
			String sku = i.ingredientSku();
			if (!seen.add(sku)) {
				throw new IllegalArgumentException("Ingrediente duplicado en receta: " + sku);
			}
		}
	}

	private static String requireNonBlank(String v, String field) {
		Objects.requireNonNull(v, field);
		if (v.isBlank()) {
			throw new IllegalArgumentException(field + " no puede ser vacio");
		}
		return v;
	}
}
