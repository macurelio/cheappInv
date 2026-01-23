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
		inventoryService.reponerStock(new RestockCommand("evt-restock-1", "corr-1", "SKU-1", "MAIN", 10, "PedidoProveedorRecibido"));
		inventoryService.descontarStockPorItem(new DiscountStockCommand("evt-item-1", "corr-1", "SKU-1", "MAIN", 3, "ItemAgregado"));

		var p = productRepository.findBySku("SKU-1").orElseThrow();
		var s = stockRepository.findByProductIdAndWarehouseId(p.getId(), "MAIN").orElseThrow();
		assertThat(s.getQuantity()).isEqualTo(7);

		assertThat(outboxEventRepository.count()).isGreaterThanOrEqualTo(2);
	}

	@Test
	void discount_fails_when_insufficient() {
		inventoryService.ensureProductExists("SKU-2");
		inventoryService.reponerStock(new RestockCommand("evt-restock-2", "corr-2", "SKU-2", "MAIN", 1, null));

		assertThatThrownBy(() -> inventoryService.descontarStockPorItem(
				new DiscountStockCommand("evt-item-2", "corr-2", "SKU-2", "MAIN", 2, null)))
				.isInstanceOf(StockInsufficientException.class);
	}

	@Test
	void discount_fails_when_product_blocked() {
		inventoryService.ensureProductExists("SKU-3");
		inventoryService.reponerStock(new RestockCommand("evt-restock-3", "corr-3", "SKU-3", "MAIN", 5, null));
		inventoryService.bloquearProducto(new BlockProductCommand("corr-3", "SKU-3", "fraude"));

		assertThatThrownBy(() -> inventoryService.descontarStockPorItem(
				new DiscountStockCommand("evt-item-3", "corr-3", "SKU-3", "MAIN", 1, null)))
				.isInstanceOf(ProductBlockedException.class);
	}

	@Test
	void idempotency_same_event_id_is_rejected() {
		inventoryService.ensureProductExists("SKU-4");
		inventoryService.reponerStock(new RestockCommand("evt-restock-4", "corr-4", "SKU-4", "MAIN", 5, null));

		inventoryService.descontarStockPorItem(new DiscountStockCommand("evt-dup", "corr-4", "SKU-4", "MAIN", 1, null));
		assertThatThrownBy(() -> inventoryService.descontarStockPorItem(
				new DiscountStockCommand("evt-dup", "corr-4", "SKU-4", "MAIN", 1, null)))
				.isInstanceOf(IdempotencyViolationException.class);
	}
}
