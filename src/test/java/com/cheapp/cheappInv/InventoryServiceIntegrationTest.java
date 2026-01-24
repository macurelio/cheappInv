package com.cheapp.cheappInv;

import com.cheapp.cheappInv.application.InventoryService;
import com.cheapp.cheappInv.application.commands.BlockProductCommand;
import com.cheapp.cheappInv.application.commands.DiscountStockCommand;
import com.cheapp.cheappInv.application.commands.RestockCommand;
import com.cheapp.cheappInv.domain.IdempotencyViolationException;
import com.cheapp.cheappInv.domain.ProductBlockedException;
import com.cheapp.cheappInv.domain.StockInsufficientException;
import com.cheapp.cheappInv.infra.persistence.OutboxEventRepository;
import com.cheapp.cheappInv.infra.persistence.ProductRepository;
import com.cheapp.cheappInv.infra.persistence.StockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class InventoryServiceIntegrationTest {
	@Autowired
	InventoryService inventoryService;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	StockRepository stockRepository;

	@Autowired
	OutboxEventRepository outboxEventRepository;

	@Test
	@Transactional
	void restock_then_discount_updates_stock_and_writes_outbox() {
		inventoryService.ensureProductExists("SKU-1");
		String restockEventId = "evt-restock-1-" + UUID.randomUUID();
		inventoryService.reponerStock(new RestockCommand(restockEventId, "corr-1", "SKU-1", "MAIN", 10, "PedidoProveedorRecibido"));
		inventoryService.descontarStockPorItem(new DiscountStockCommand("evt-item-1-" + UUID.randomUUID(), "corr-1", "SKU-1", "MAIN", 3, "ItemAgregado"));

		var p = productRepository.findBySku("SKU-1").orElseThrow();
		var s = stockRepository.findByProductIdAndWarehouseId(p.getId(), "MAIN").orElseThrow();
		assertThat(s.getQuantity()).isEqualTo(7);

		assertThat(outboxEventRepository.count()).isGreaterThanOrEqualTo(2);
	}

	@Test
	@Transactional
	void discount_fails_when_insufficient() {
		var product = inventoryService.ensureProductExists("SKU-2");
		inventoryService.reponerStock(new RestockCommand("evt-restock-2-" + UUID.randomUUID(), "corr-2", "SKU-2", "MAIN", 1, null));

		long available = stockRepository.findByProductIdAndWarehouseId(product.getId(), "MAIN").orElseThrow().getQuantity();
		long requested = available + 1;

		assertThatThrownBy(() -> inventoryService.descontarStockPorItem(
				new DiscountStockCommand("evt-item-2-" + UUID.randomUUID(), "corr-2", "SKU-2", "MAIN", requested, null)))
				.isInstanceOf(StockInsufficientException.class);
	}

	@Test
	void discount_fails_when_product_blocked() {
		inventoryService.ensureProductExists("SKU-3");
		inventoryService.reponerStock(new RestockCommand("evt-restock-3-" + UUID.randomUUID(), "corr-3", "SKU-3", "MAIN", 5, null));
		inventoryService.bloquearProducto(new BlockProductCommand("corr-3", "SKU-3", "fraude"));

		assertThatThrownBy(() -> inventoryService.descontarStockPorItem(
				new DiscountStockCommand("evt-item-3-" + UUID.randomUUID(), "corr-3", "SKU-3", "MAIN", 1, null)))
				.isInstanceOf(ProductBlockedException.class);
	}

	@Test
	void idempotency_same_event_id_is_rejected() {
		inventoryService.ensureProductExists("SKU-4");
		inventoryService.reponerStock(new RestockCommand("evt-restock-4-" + UUID.randomUUID(), "corr-4", "SKU-4", "MAIN", 5, null));

		String dupEventId = "evt-dup-" + UUID.randomUUID();
		inventoryService.descontarStockPorItem(new DiscountStockCommand(dupEventId, "corr-4", "SKU-4", "MAIN", 1, null));
		assertThatThrownBy(() -> inventoryService.descontarStockPorItem(
				new DiscountStockCommand(dupEventId, "corr-4", "SKU-4", "MAIN", 1, null)))
				.isInstanceOf(IdempotencyViolationException.class);
	}
}
