package com.cheapp.cheappInv.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
	Optional<ProductEntity> findBySku(String sku);
}
