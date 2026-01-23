package com.cheapp.cheappInv.application;

import com.cheapp.cheappInv.application.commands.BlockProductCommand;
import com.cheapp.cheappInv.application.commands.DiscountStockCommand;
import com.cheapp.cheappInv.application.commands.ReactivateProductCommand;
import com.cheapp.cheappInv.application.commands.RestockCommand;
import com.cheapp.cheappInv.domain.*;
import com.cheapp.cheappInv.infra.events.emitted.ProductoBloqueadoEvent;
import com.cheapp.cheappInv.infra.events.emitted.StockDescontadoEvent;
import com.cheapp.cheappInv.infra.events.emitted.StockRepuestoEvent;
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

	public InventoryService(ProductRepository productRepository,
						StockRepository stockRepository,
						InventoryMovementRepository movementRepository,
						InboxEventRepository inboxEventRepository,
						OutboxEventRepository outboxEventRepository,
						JsonMapper jsonMapper,
						Clock clock) {
		this.productRepository = productRepository;
		this.stockRepository = stockRepository;
		this.movementRepository = movementRepository;
		this.inboxEventRepository = inboxEventRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
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

		writeOutbox("StockRepuesto", cmd.correlationId(), new StockRepuestoEvent(cmd.sku(), warehouse, cmd.quantity(), newQty));
	}

	@Transactional
	public void bloquearProducto(BlockProductCommand cmd) {
		Instant now = Instant.now(clock);
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
