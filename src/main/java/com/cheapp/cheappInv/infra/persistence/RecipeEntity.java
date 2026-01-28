package com.cheapp.cheappInv.infra.persistence;

import com.cheapp.cheappInv.domain.recipes.RecipeStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recipes", indexes = {
		@Index(name = "idx_recipes_dish_sku", columnList = "dish_sku"),
		@Index(name = "idx_recipes_dish_status", columnList = "dish_sku,status")
}, uniqueConstraints = {
		@UniqueConstraint(name = "uk_recipes_recipe_id", columnNames = {"recipe_id"})
})
public class RecipeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "recipe_id", nullable = false, updatable = false)
	private String recipeId;

	@Column(name = "dish_sku", nullable = false, updatable = false)
	private String dishSku;

	@Column(name = "version_number", nullable = false, updatable = false)
	private int versionNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RecipeStatus status;

	@OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<RecipeIngredientEntity> ingredients = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	private long version;

	protected RecipeEntity() {
	}

	public RecipeEntity(String recipeId, String dishSku, int versionNumber, RecipeStatus status, Instant createdAt) {
		this.recipeId = recipeId;
		this.dishSku = dishSku;
		this.versionNumber = versionNumber;
		this.status = status;
		this.createdAt = createdAt;
	}

	public void addIngredient(RecipeIngredientEntity entity) {
		this.ingredients.add(entity);
	}

	public Long getId() {
		return id;
	}

	public String getRecipeId() {
		return recipeId;
	}

	public String getDishSku() {
		return dishSku;
	}

	public int getVersionNumber() {
		return versionNumber;
	}

	public RecipeStatus getStatus() {
		return status;
	}

	public void setStatus(RecipeStatus status) {
		this.status = status;
	}

	public List<RecipeIngredientEntity> getIngredients() {
		return ingredients;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
