package com.cheapp.cheappInv.api;

import com.cheapp.cheappInv.application.InventoryService;
import com.cheapp.cheappInv.application.commands.BlockProductCommand;
import com.cheapp.cheappInv.application.commands.ReactivateProductCommand;
import com.cheapp.cheappInv.infra.persistence.ProductEntity;
import com.cheapp.cheappInv.infra.persistence.ProductRepository;
import com.cheapp.cheappInv.infra.persistence.StockRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Inventory", description = "API REST del microservicio de inventario (producto, stock y acciones administrativas).")
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
	@Operation(
			summary = "Bloquear producto",
			description = "Marca el producto como BLOCKED. Mientras esté bloqueado, no se permiten descuentos de stock.",
			responses = {
					@ApiResponse(responseCode = "204", description = "Producto bloqueado"),
					@ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content(schema = @Schema(implementation = RestExceptionHandler.ApiError.class)))
			}
	)
	public void block(
			@Parameter(description = "SKU del producto", example = "SKU-1") @PathVariable @NotBlank String sku,
			@Parameter(description = "Correlation ID para trazabilidad", example = "corr-123") @RequestParam(required = false) String correlationId,
			@Parameter(description = "Motivo del bloqueo", example = "Producto retirado") @RequestParam(required = false) String reason
	) {
		inventoryService.bloquearProducto(new BlockProductCommand(correlationId, sku, reason));
	}

	@PostMapping("/products/{sku}/reactivate")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(
			summary = "Reactivar producto",
			description = "Marca el producto como ACTIVE.",
			responses = {
					@ApiResponse(responseCode = "204", description = "Producto reactivado"),
					@ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content(schema = @Schema(implementation = RestExceptionHandler.ApiError.class)))
			}
	)
	public void reactivate(
			@Parameter(description = "SKU del producto", example = "SKU-1") @PathVariable @NotBlank String sku,
			@Parameter(description = "Correlation ID para trazabilidad", example = "corr-123") @RequestParam(required = false) String correlationId,
			@Parameter(description = "Motivo de reactivación", example = "Vuelve a estar disponible") @RequestParam(required = false) String reason
	) {
		inventoryService.reactivarProducto(new ReactivateProductCommand(correlationId, sku, reason));
	}

	@GetMapping("/products/{sku}")
	@Operation(
			summary = "Consultar producto",
			description = "Devuelve el estado actual del producto. Nota: actualmente, si no existe, la API responde vacío (null).",
			responses = {
					@ApiResponse(responseCode = "200", description = "Producto encontrado", content = @Content(schema = @Schema(implementation = ProductView.class)))
			}
	)
	public ProductView getProduct(
			@Parameter(description = "SKU del producto", example = "SKU-1") @PathVariable String sku
	) {
		ProductEntity p = productRepository.findBySku(sku).orElse(null);
		if (p == null) {
			return null;
		}
		return new ProductView(p.getSku(), p.getStatus().name());
	}

	@GetMapping("/stock")
	@Operation(
			summary = "Consultar stock",
			description = "Devuelve el stock por SKU y warehouseId. Si el producto existe pero no hay registro de stock, devuelve 0.",
			responses = {
					@ApiResponse(responseCode = "200", description = "Stock devuelto", content = @Content(schema = @Schema(implementation = StockView.class)))
			}
	)
	public StockView getStock(
			@Parameter(description = "SKU del producto", example = "SKU-1") @RequestParam String sku,
			@Parameter(description = "Identificador del almacén", example = "MAIN") @RequestParam(defaultValue = "MAIN") String warehouseId
	) {
		ProductEntity p = productRepository.findBySku(sku).orElse(null);
		if (p == null) {
			return null;
		}
		return stockRepository.findByProductIdAndWarehouseId(p.getId(), warehouseId)
				.map(s -> new StockView(sku, warehouseId, s.getQuantity()))
				.orElse(new StockView(sku, warehouseId, 0));
	}

	public record ProductView(
			@Schema(description = "SKU del producto", example = "SKU-1") String sku,
			@Schema(description = "Estado del producto", example = "ACTIVE") String status
	) {
	}

	public record StockView(
			@Schema(description = "SKU del producto", example = "SKU-1") String sku,
			@Schema(description = "Identificador del almacén", example = "MAIN") String warehouseId,
			@Schema(description = "Cantidad disponible", example = "10") long quantity
	) {
	}
}
