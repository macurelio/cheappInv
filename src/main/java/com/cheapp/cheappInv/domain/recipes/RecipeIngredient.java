package com.cheapp.cheappInv.domain.recipes;

import com.cheapp.cheappInv.domain.UnitCode;

import java.util.Objects;

/**
 * Linea de receta: cunto insumo consume un plato.
 *
 * Dominio puro: no conoce JPA ni DTOs.
 */
public final class RecipeIngredient {
	private final String ingredientSku;
	private final long quantity;
	private final UnitCode unitCode;

	public RecipeIngredient(String ingredientSku, long quantity, UnitCode unitCode) {
		this.ingredientSku = Objects.requireNonNull(ingredientSku, "ingredientSku");
		if (ingredientSku.isBlank()) {
			throw new IllegalArgumentException("ingredientSku no puede ser vacio");
		}
		if (quantity <= 0) {
			throw new IllegalArgumentException("quantity debe ser > 0");
		}
		this.quantity = quantity;
		this.unitCode = unitCode; // opcional (puede ser null)
	}

	public String ingredientSku() {
		return ingredientSku;
	}

	public long quantity() {
		return quantity;
	}

	public UnitCode unitCode() {
		return unitCode;
	}
}
