package com.cheapp.cheappInv.application;

import com.cheapp.cheappInv.application.commands.BlockProductCommand;
import com.cheapp.cheappInv.application.commands.ConsumeComandaCommand;
import com.cheapp.cheappInv.application.commands.DiscountStockCommand;
import com.cheapp.cheappInv.application.commands.ReactivateProductCommand;
import com.cheapp.cheappInv.application.commands.RestockCommand;
import com.cheapp.cheappInv.domain.*;
import com.cheapp.cheappInv.infra.events.emitted.ProductoBloqueadoEvent;
import com.cheapp.cheappInv.infra.events.emitted.StockDescontadoEvent;
import com.cheapp.cheappInv.infra.persistence.*;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class InventoryService {
	private final ProductRepository productRepository;
	private final StockRepository stockRepository;
	private final InventoryMovementRepository movementRepository;
	private final InboxEventRepository inboxEventRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final JsonMapper jsonMapper;
	private final Clock clock;
	private final RecipesRepository recipesRepository;
	private final ProcessedComandaRepository processedComandaRepository;
	private final HistoricalConsumptionRepository historicalConsumptionRepository;

	public InventoryService(ProductRepository productRepository,
						StockRepository stockRepository,
						InventoryMovementRepository movementRepository,
						InboxEventRepository inboxEventRepository,
						OutboxEventRepository outboxEventRepository,
						JsonMapper jsonMapper,
						Clock clock,
						RecipesRepository recipesRepository,
						ProcessedComandaRepository processedComandaRepository,
						HistoricalConsumptionRepository historicalConsumptionRepository) {
		this.productRepository = productRepository;
		this.stockRepository = stockRepository;
		this.movementRepository = movementRepository;
		this.inboxEventRepository = inboxEventRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
		this.recipesRepository = recipesRepository;
		this.processedComandaRepository = processedComandaRepository;
		this.historicalConsumptionRepository = historicalConsumptionRepository;
	}

	@Transactional
	public void descontarStockPorItem(DiscountStockCommand cmd) {
		assertValidQuantity(cmd.quantity());
		if (cmd.eventId() != null && inboxEventRepository.existsByEventId(cmd.eventId())) {
			throw new IdempotencyViolationException(cmd.eventId());
		}

		Instant now = Instant.now(clock);
		ProductEntity product = productRepository.findBySku(cmd.sku())
				.orElseThrow(() -> new ProductNotFoundException(cmd.sku()));

		if (product.getStatus() == ProductStatus.BLOCKED) {
			throw new ProductBlockedException(cmd.sku());
		}

		String warehouse = cmd.warehouseId();
		StockEntity stock = stockRepository.findForUpdate(product.getId(), warehouse)
				.orElseGet(() -> stockRepository.save(new StockEntity(product, warehouse, 0, now)));

		long available = stock.getQuantity();
		if (available < cmd.quantity()) {
			throw new StockInsufficientException(cmd.sku(), cmd.quantity(), available);
		}

		long newQty = available - cmd.quantity();
		stock.setQuantity(newQty);
		stock.setUpdatedAt(now);
		stockRepository.save(stock);

		movementRepository.save(new InventoryMovementEntity(
				product,
				warehouse,
				MovementType.DEBIT,
				cmd.quantity(),
				cmd.reason() == null ? "ItemAgregado" : cmd.reason(),
				cmd.eventId(),
				cmd.correlationId(),
				now
		));

		if (cmd.eventId() != null) {
			inboxEventRepository.save(new InboxEventEntity(cmd.eventId(), "ItemAgregado", now));
		}

		writeOutbox("StockDescontado", cmd.correlationId(), new StockDescontadoEvent(cmd.sku(), warehouse, cmd.quantity(), newQty));
	}

	@Transactional
	public void reponerStock(RestockCommand cmd) {
		assertValidQuantity(cmd.quantity());
		if (cmd.eventId() != null && inboxEventRepository.existsByEventId(cmd.eventId())) {
			throw new IdempotencyViolationException(cmd.eventId());
		}

		Instant now = Instant.now(clock);
		ProductEntity product = productRepository.findBySku(cmd.sku())
				.orElseThrow(() -> new ProductNotFoundException(cmd.sku()));

		String warehouse = cmd.warehouseId();
		StockEntity stock = stockRepository.findForUpdate(product.getId(), warehouse)
				.orElseGet(() -> stockRepository.save(new StockEntity(product, warehouse, 0, now)));

		long newQty = stock.getQuantity() + cmd.quantity();
		stock.setQuantity(newQty);
		stock.setUpdatedAt(now);
		stockRepository.save(stock);

		movementRepository.save(new InventoryMovementEntity(
				product,
				warehouse,
				MovementType.CREDIT,
				cmd.quantity(),
				cmd.reason() == null ? "PedidoProveedorRecibido" : cmd.reason(),
				cmd.eventId(),
				cmd.correlationId(),
				now
		));

		if (cmd.eventId() != null) {
			inboxEventRepository.save(new InboxEventEntity(cmd.eventId(), "PedidoProveedorRecibido", now));
		}

		writeOutbox("StockRepuesto", cmd.correlationId(), new com.cheapp.cheappInv.infra.events.emitted.StockRepuestoEvent(cmd.sku(), warehouse, cmd.quantity(), newQty));
	}

	@Transactional
	public void bloquearProducto(BlockProductCommand cmd) {
		ProductEntity product = productRepository.findBySku(cmd.sku())
				.orElseThrow(() -> new ProductNotFoundException(cmd.sku()));
		product.setStatus(ProductStatus.BLOCKED);
		productRepository.save(product);

		writeOutbox("ProductoBloqueado", cmd.correlationId(), new ProductoBloqueadoEvent(cmd.sku(), cmd.reason()));
	}

	@Transactional
	public void reactivarProducto(ReactivateProductCommand cmd) {
		ProductEntity product = productRepository.findBySku(cmd.sku())
				.orElseThrow(() -> new ProductNotFoundException(cmd.sku()));
		product.setStatus(ProductStatus.ACTIVE);
		productRepository.save(product);
		// No evento solicitado explícitamente para reactivación.
	}

	@Transactional
	public ProductEntity ensureProductExists(String sku) {
		Instant now = Instant.now(clock);
		return productRepository.findBySku(sku)
				.orElseGet(() -> productRepository.save(new ProductEntity(sku, now)));
	}

	/**
	 * Flujo: ComandaCerrada -> resolver recetas activas por plato -> consumir ingredientes.
	 *
	 * Reglas:
	 * - Idempotencia por comandaId.
	 * - Stock nunca negativo.
	 * - Si una línea deja un insumo en 0, bloquea el producto.
	 */
	@Transactional
	public void descontarStockPorReceta(ConsumeComandaCommand cmd) {
		if (cmd.comandaId() == null || cmd.comandaId().isBlank()) {
			throw new IllegalArgumentException("comandaId es requerido");
		}
		if (cmd.dishes() == null || cmd.dishes().isEmpty()) {
			return; // nada que hacer
		}

		// Idempotencia: eventId (bus) + comandaId (negocio)
		if (cmd.eventId() != null && inboxEventRepository.existsByEventId(cmd.eventId())) {
			throw new com.cheapp.cheappInv.domain.IdempotencyViolationException(cmd.eventId());
		}
		if (processedComandaRepository.existsByComandaId(cmd.comandaId())) {
			throw new ComandaAlreadyProcessedException(cmd.comandaId());
		}

		String warehouse = (cmd.warehouseId() == null || cmd.warehouseId().isBlank()) ? "MAIN" : cmd.warehouseId();
		Instant now = Instant.now(clock);

		// 1) calcular consumo total por ingrediente
		java.util.Map<String, Long> totalByIngredientSku = new java.util.HashMap<>();
		for (ConsumeComandaCommand.ComandaDishLine dish : cmd.dishes()) {
			if (dish.quantity() <= 0) {
				throw new IllegalArgumentException("dish.quantity debe ser > 0");
			}
			var recipe = recipesRepository.findActiveByDishSku(dish.dishSku())
					.orElseThrow(() -> new IllegalStateException("No hay receta ACTIVE para plato: " + dish.dishSku()));
			for (var ing : recipe.getIngredients()) {
				long consumed = Math.multiplyExact(ing.getQuantity(), dish.quantity());
				totalByIngredientSku.merge(ing.getIngredientSku(), consumed, Math::addExact);
			}
		}

		// 2) aplicar consumo por ingrediente
		for (var e : totalByIngredientSku.entrySet()) {
			String ingredientSku = e.getKey();
			long qtyToConsume = e.getValue();
			assertValidQuantity(qtyToConsume);

			ProductEntity product = productRepository.findBySku(ingredientSku)
					.orElseThrow(() -> new com.cheapp.cheappInv.domain.ProductNotFoundException(ingredientSku));

			if (product.getStatus() == ProductStatus.BLOCKED) {
				// ya bloqueado; reportamos insuficiente para visibilidad
				writeOutbox("StockInsuficiente", cmd.correlationId(), new com.cheapp.cheappInv.infra.events.emitted.StockInsuficienteEvent(cmd.comandaId(), ingredientSku, warehouse, qtyToConsume, 0));
				throw new com.cheapp.cheappInv.domain.ProductBlockedException(ingredientSku);
			}

			StockEntity stock = stockRepository.findForUpdate(product.getId(), warehouse)
					.orElseGet(() -> stockRepository.save(new StockEntity(product, warehouse, 0, now)));

			long available = stock.getQuantity();
			if (available < qtyToConsume) {
				writeOutbox("StockInsuficiente", cmd.correlationId(), new com.cheapp.cheappInv.infra.events.emitted.StockInsuficienteEvent(cmd.comandaId(), ingredientSku, warehouse, qtyToConsume, available));
				throw new StockInsufficientException(ingredientSku, qtyToConsume, available);
			}

			long newQty = available - qtyToConsume;
			stock.setQuantity(newQty);
			stock.setUpdatedAt(now);
			stockRepository.save(stock);

			movementRepository.save(new InventoryMovementEntity(
					product,
					warehouse,
					com.cheapp.cheappInv.domain.MovementType.DEBIT,
					qtyToConsume,
					"ComandaCerrada:" + cmd.comandaId(),
					cmd.eventId(),
					cmd.correlationId(),
					now
			));

			historicalConsumptionRepository.save(new HistoricalConsumptionEntity(ingredientSku, qtyToConsume, cmd.comandaId(), now));

			writeOutbox("StockDescontado", cmd.correlationId(), new StockDescontadoEvent(ingredientSku, warehouse, qtyToConsume, newQty));

			if (newQty == 0 && product.getStatus() != ProductStatus.BLOCKED) {
				product.setStatus(ProductStatus.BLOCKED);
				productRepository.save(product);
				writeOutbox("ProductoBloqueado", cmd.correlationId(), new ProductoBloqueadoEvent(ingredientSku, "Stock en cero"));
			}
			if (newQty > 0 && product.getStatus() == ProductStatus.BLOCKED) {
				product.setStatus(ProductStatus.ACTIVE);
				productRepository.save(product);
				writeOutbox("ProductoReactivado", cmd.correlationId(), new com.cheapp.cheappInv.infra.events.emitted.ProductoReactivadoEvent(ingredientSku, "Stock disponible"));
			}
		}

		// 3) marcar comanda procesada solo al final (transaccional)
		processedComandaRepository.save(new ProcessedComandaEntity(cmd.comandaId(), now));
		if (cmd.eventId() != null) {
			inboxEventRepository.save(new InboxEventEntity(cmd.eventId(), "ComandaCerrada", now));
		}
	}

	private void writeOutbox(String eventType, String correlationId, Object payload) {
		try {
			String json = jsonMapper.writeValueAsString(payload);
			outboxEventRepository.save(new OutboxEventEntity(
					UUID.randomUUID().toString(),
					eventType,
					1,
					correlationId,
					json,
					Instant.now(clock)
			));
		} catch (Exception e) {
			throw new RuntimeException("No se pudo serializar payload outbox: " + eventType, e);
		}
	}

	private static void assertValidQuantity(long quantity) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("quantity debe ser > 0");
		}
	}
}
