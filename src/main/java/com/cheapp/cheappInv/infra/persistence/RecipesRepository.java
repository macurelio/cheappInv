package com.cheapp.cheappInv.infra.persistence;

import com.cheapp.cheappInv.domain.recipes.RecipeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecipesRepository extends JpaRepository<RecipeEntity, Long> {
	Optional<RecipeEntity> findByRecipeId(String recipeId);

	@Query("select r from RecipeEntity r where r.dishSku = :dishSku and r.status = 'ACTIVE'")
	Optional<RecipeEntity> findActiveByDishSku(@Param("dishSku") String dishSku);

	@Query("select r from RecipeEntity r where r.dishSku = :dishSku and r.status = :status and r.versionNumber = :version")
	Optional<RecipeEntity> findByDishSkuAndStatusAndVersion(@Param("dishSku") String dishSku,
													 @Param("status") RecipeStatus status,
													 @Param("version") int version);
}
