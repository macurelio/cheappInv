package com.cheapp.cheappInv.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface StockRepository extends JpaRepository<StockEntity, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from StockEntity s where s.product.id = :productId and s.warehouseId = :warehouseId")
	Optional<StockEntity> findForUpdate(@Param("productId") Long productId, @Param("warehouseId") String warehouseId);

	Optional<StockEntity> findByProductIdAndWarehouseId(Long productId, String warehouseId);
}
