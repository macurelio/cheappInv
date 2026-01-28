package com.cheapp.cheappInv.infra.persistence;

import com.cheapp.cheappInv.domain.UnitCode;
import jakarta.persistence.*;

@Entity
@Table(name = "recipe_ingredients", uniqueConstraints = {
		@UniqueConstraint(name = "uk_recipe_ingredient", columnNames = {"recipe_id", "ingredient_sku"})
}, indexes = {
		@Index(name = "idx_recipe_ingredients_recipe", columnList = "recipe_id")
})
public class RecipeIngredientEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "recipe_id", nullable = false)
	private RecipeEntity recipe;

	@Column(name = "ingredient_sku", nullable = false)
	private String ingredientSku;

	@Column(nullable = false)
	private long quantity;

	@Enumerated(EnumType.STRING)
	@Column(name = "unit_code")
	private UnitCode unitCode;

	protected RecipeIngredientEntity() {
	}

	public RecipeIngredientEntity(RecipeEntity recipe, String ingredientSku, long quantity, UnitCode unitCode) {
		this.recipe = recipe;
		this.ingredientSku = ingredientSku;
		this.quantity = quantity;
		this.unitCode = unitCode;
	}

	public Long getId() {
		return id;
	}

	public RecipeEntity getRecipe() {
		return recipe;
	}

	public String getIngredientSku() {
		return ingredientSku;
	}

	public long getQuantity() {
		return quantity;
	}

	public UnitCode getUnitCode() {
		return unitCode;
	}
}
