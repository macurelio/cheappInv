package com.cheapp.cheappInv.api;

import com.cheapp.cheappInv.domain.MovementType;
import com.cheapp.cheappInv.domain.ProductStatus;
import com.cheapp.cheappInv.infra.logging.Loggable;
import com.cheapp.cheappInv.infra.persistence.ProductEntity;
import com.cheapp.cheappInv.infra.persistence.ProductRepository;
import com.cheapp.cheappInv.infra.persistence.StockRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Tag(name = "Catalog", description = "Endpoints de lectura para navegar el inventario como un supermercado (búsqueda, filtros, métricas).")
@Loggable("catalog")
public class CatalogController {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CatalogController.class);

	private final ProductRepository productRepository;
	private final StockRepository stockRepository;
	private final EntityManager entityManager;

	public CatalogController(ProductRepository productRepository, StockRepository stockRepository, EntityManager entityManager) {
		this.productRepository = productRepository;
		this.stockRepository = stockRepository;
		this.entityManager = entityManager;
	}

	@GetMapping("/products")
	@Operation(
			summary = "Listar productos (catálogo)",
			description = "Listado paginado para UI tipo supermercado. Permite filtrar por texto (sku/nombre), categoría, estado y (opcionalmente) stock por warehouse.",
			responses = {
					@ApiResponse(responseCode = "200", description = "Página de productos", content = @Content(schema = @Schema(implementation = ProductListItem.class)))
			}
	)
	public Page<ProductListItem> listProducts(
			@Parameter(description = "Texto a buscar en sku o nombre") @RequestParam(required = false) String query,
			@Parameter(description = "Filtrar por código de categoría") @RequestParam(required = false) String categoryCode,
			@Parameter(description = "Filtrar por estado (ACTIVE/BLOCKED)") @RequestParam(required = false) String status,
			@Parameter(description = "Almacén para calcular stock") @RequestParam(defaultValue = "MAIN") String warehouseId,
			@Parameter(description = "Solo productos con stock > 0") @RequestParam(defaultValue = "false") boolean inStockOnly,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		log.info("Listando productos query={} categoryCode={} status={} warehouseId={} inStockOnly={} page={} size={}",
				query, categoryCode, status, warehouseId, inStockOnly, page, size);
		String qRaw = (query == null || query.isBlank()) ? null : query.trim().toLowerCase();
		String q = qRaw == null ? null : ("%" + qRaw + "%");
		ProductStatus st = (status == null || status.isBlank()) ? null : ProductStatus.valueOf(status.trim().toUpperCase());
		String cat = (categoryCode == null || categoryCode.isBlank()) ? null : categoryCode.trim();

		// 1) page de IDs (sin fetch join) -> evita DISTINCT+ORDER BY problemático en PostgreSQL
		Pageable pageable = PageRequest.of(page, size);
		List<Long> ids = productRepository.findCatalogProductIdsWithSortKey(q, st, cat, pageable).stream()
				.map(r -> (Long) r[0])
				.toList();

		// IMPORTANTE: por el join a categorías, la query de IDs puede devolver duplicados.
		// Los deduplicamos preservando el orden para evitar Duplicate key al rearmar el mapa.
		if (ids.size() > 1) {
			ids = new ArrayList<>(new LinkedHashSet<>(ids));
		}

		// 2) count
		String countJpql = "select count(distinct p.id) from ProductEntity p left join p.categories c where 1=1 ";
		if (q != null) {
			countJpql += " and (lower(p.sku) like :q or lower(coalesce(p.name,'')) like :q) ";
		}
		if (st != null) {
			countJpql += " and p.status = :status ";
		}
		if (cat != null) {
			countJpql += " and c.code = :categoryCode ";
		}

		TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
		if (q != null) {
			countQuery.setParameter("q", q);
		}
		if (st != null) {
			countQuery.setParameter("status", st);
		}
		if (cat != null) {
			countQuery.setParameter("categoryCode", cat);
		}
		long total = countQuery.getSingleResult();

		// 3) fetch entities with categories for the current page
		List<ProductEntity> products;
		if (ids.isEmpty()) {
			products = List.of();
		} else {
			// la query IN(:ids) no garantiza orden; reordenamos según ids
			// Nota: con LEFT JOIN FETCH a categorías, Hibernate puede devolver el mismo producto repetido.
			Map<Long, ProductEntity> byId = productRepository.findAllWithCategoriesByIdIn(ids).stream()
					.collect(Collectors.toMap(ProductEntity::getId, p -> p, (a, b) -> {
						log.debug("Producto duplicado en fetch por id={} (manteniendo el primero)", a.getId());
						return a;
					}));
			products = ids.stream().map(byId::get).filter(p -> p != null).toList();
		}

		// stock for current page
		Map<Long, Long> stockByProductId = products.stream()
				.collect(Collectors.toMap(ProductEntity::getId,
						p -> stockRepository.findByProductIdAndWarehouseId(p.getId(), warehouseId).map(s -> s.getQuantity()).orElse(0L)));

		// last restock (CREDIT) timestamp for each product
		Map<Long, Instant> lastRestockByProductId = fetchLastMovementTs(products.stream().map(ProductEntity::getId).toList(), warehouseId, MovementType.CREDIT);

		List<ProductListItem> items = products.stream()
				.map(p -> {
					Long qty = stockByProductId.getOrDefault(p.getId(), 0L);
					if (inStockOnly && qty <= 0) {
						return null;
					}
					Instant lastRestockedAt = lastRestockByProductId.get(p.getId());
					Instant estimatedExpiryAt = null;
					if (lastRestockedAt != null && p.getEstimatedShelfLifeDays() != null) {
						estimatedExpiryAt = lastRestockedAt.plusSeconds(p.getEstimatedShelfLifeDays().longValue() * 86400L);
					}
					return ProductListItem.from(p, warehouseId, qty, lastRestockedAt, estimatedExpiryAt);
				})
				.filter(i -> i != null)
				.toList();

		return new PageImpl<>(items, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")), total);
	}

	private Map<Long, Instant> fetchLastMovementTs(List<Long> productIds, String warehouseId, MovementType type) {
		if (productIds.isEmpty()) {
			return Map.of();
		}
		// JPQL: max createdAt per product
		var query = entityManager.createQuery(
				"select m.product.id, max(m.createdAt) from InventoryMovementEntity m " +
						"where m.product.id in :ids and m.warehouseId = :wh and m.type = :type group by m.product.id",
				Object[].class
		);
		query.setParameter("ids", productIds);
		query.setParameter("wh", warehouseId);
		query.setParameter("type", type);
		List<Object[]> rows = query.getResultList();
		return rows.stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Instant) r[1], (a, b) -> a));
	}

	public record CategoryView(String code, String name) {
	}

	public record ProductListItem(
			String sku,
			String name,
			String brand,
			String status,
			List<CategoryView> categories,
			String warehouseId,
			long stockQuantity,
			Instant lastRequestedAt,
			Instant estimatedExpiryAt,
			String unitType,
			String unitCode,
			String unitAmount
	) {
		static ProductListItem from(ProductEntity p, String warehouseId, long stock, Instant lastRestockedAt, Instant estimatedExpiryAt) {
			List<CategoryView> cats = p.getCategories().stream()
					.map(c -> new CategoryView(c.getCode(), c.getName()))
					.sorted((a, b) -> a.code.compareToIgnoreCase(b.code))
					.toList();
			return new ProductListItem(
					p.getSku(),
					p.getName(),
					p.getBrand(),
					p.getStatus().name(),
					cats,
					warehouseId,
					stock,
					lastRestockedAt,
					estimatedExpiryAt,
					p.getUnitType() == null ? null : p.getUnitType().name(),
					p.getUnitCode() == null ? null : p.getUnitCode().name(),
					p.getUnitAmount() == null ? null : p.getUnitAmount().toPlainString()
			);
		}
	}
}
