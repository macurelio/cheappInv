package com.cheapp.cheappInv.api;

import com.cheapp.cheappInv.application.InventoryService;
import com.cheapp.cheappInv.application.commands.BlockProductCommand;
import com.cheapp.cheappInv.application.commands.ReactivateProductCommand;
import com.cheapp.cheappInv.infra.persistence.ProductEntity;
import com.cheapp.cheappInv.infra.persistence.ProductRepository;
import com.cheapp.cheappInv.infra.persistence.StockRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class InventoryController {
	private final InventoryService inventoryService;
	private final ProductRepository productRepository;
	private final StockRepository stockRepository;

	public InventoryController(InventoryService inventoryService,
						  ProductRepository productRepository,
						  StockRepository stockRepository) {
		this.inventoryService = inventoryService;
		this.productRepository = productRepository;
		this.stockRepository = stockRepository;
	}

	@PostMapping("/products/{sku}/block")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void block(@PathVariable @NotBlank String sku,
					 @RequestParam(required = false) String correlationId,
					 @RequestParam(required = false) String reason) {
		inventoryService.bloquearProducto(new BlockProductCommand(correlationId, sku, reason));
	}

	@PostMapping("/products/{sku}/reactivate")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reactivate(@PathVariable @NotBlank String sku,
						@RequestParam(required = false) String correlationId,
						@RequestParam(required = false) String reason) {
		inventoryService.reactivarProducto(new ReactivateProductCommand(correlationId, sku, reason));
	}

	@GetMapping("/products/{sku}")
	public ProductView getProduct(@PathVariable String sku) {
		ProductEntity p = productRepository.findBySku(sku).orElse(null);
		if (p == null) {
			return null;
		}
		return new ProductView(p.getSku(), p.getStatus().name());
	}

	@GetMapping("/stock")
	public StockView getStock(@RequestParam String sku, @RequestParam(defaultValue = "MAIN") String warehouseId) {
		ProductEntity p = productRepository.findBySku(sku).orElse(null);
		if (p == null) {
			return null;
		}
		return stockRepository.findByProductIdAndWarehouseId(p.getId(), warehouseId)
				.map(s -> new StockView(sku, warehouseId, s.getQuantity()))
				.orElse(new StockView(sku, warehouseId, 0));
	}

	public record ProductView(String sku, String status) {
	}

	public record StockView(String sku, String warehouseId, long quantity) {
	}
}
