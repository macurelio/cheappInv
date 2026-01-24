package com.cheapp.cheappInv.infra.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
	Optional<ProductEntity> findBySku(String sku);

	@Query("""
		select distinct p.id
		from ProductEntity p
		left join p.categories c
		where (:q is null or (lower(p.sku) like :q or lower(coalesce(p.name,'')) like :q))
			and (:status is null or p.status = :status)
			and (:categoryCode is null or c.code = :categoryCode)
		order by coalesce(p.name,p.sku) asc, p.id asc
		""")
	List<Long> findCatalogProductIds(
			@Param("q") String q,
			@Param("status") com.cheapp.cheappInv.domain.ProductStatus status,
			@Param("categoryCode") String categoryCode,
			Pageable pageable
	);

	@Query("""
		select distinct p
		from ProductEntity p
		left join fetch p.categories c
		where p.id in :ids
		""")
	List<ProductEntity> findAllWithCategoriesByIdIn(@Param("ids") List<Long> ids);

	@Query("""
		select distinct p.id as id, coalesce(p.name,p.sku) as sortKey
		from ProductEntity p
		left join p.categories c
		where (:q is null or (lower(p.sku) like :q or lower(coalesce(p.name,'')) like :q))
			and (:status is null or p.status = :status)
			and (:categoryCode is null or c.code = :categoryCode)
		order by sortKey asc, id asc
		""")
	List<Object[]> findCatalogProductIdsWithSortKey(
			@Param("q") String q,
			@Param("status") com.cheapp.cheappInv.domain.ProductStatus status,
			@Param("categoryCode") String categoryCode,
			Pageable pageable
	);
}
