package com.cheapp.cheappInv.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface HistoricalConsumptionRepository extends JpaRepository<HistoricalConsumptionEntity, Long> {
	@Query("select sum(h.quantity) from HistoricalConsumptionEntity h where h.productSku = :sku and h.createdAt >= :from")
	Optional<Long> sumQuantitySince(@Param("sku") String sku, @Param("from") Instant from);
}
