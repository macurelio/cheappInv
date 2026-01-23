package com.cheapp.cheappInv.api;

import com.cheapp.cheappInv.application.InventoryService;
import com.cheapp.cheappInv.application.commands.DiscountStockCommand;
import com.cheapp.cheappInv.application.commands.RestockCommand;
import com.cheapp.cheappInv.infra.events.consumed.ItemAgregadoEvent;
import com.cheapp.cheappInv.infra.events.consumed.PedidoProveedorRecibidoEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint opcional para simular consumo de eventos (en real se conectaría a Kafka/Rabbit/SQS).
 */
@RestController
@RequestMapping("/api/events/in")
public class EventsInController {
	private final InventoryService inventoryService;
	private final String defaultWarehouseId;

	public EventsInController(InventoryService inventoryService,
						 @Value("${inventory.default-warehouse-id:MAIN}") String defaultWarehouseId) {
		this.inventoryService = inventoryService;
		this.defaultWarehouseId = defaultWarehouseId;
	}

	@PostMapping("/item-agregado")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void itemAgregado(@RequestBody ItemAgregadoEvent event) {
		inventoryService.ensureProductExists(event.sku());
		inventoryService.descontarStockPorItem(new DiscountStockCommand(
				event.eventId(),
				event.correlationId(),
				event.sku(),
				event.warehouseId() == null ? defaultWarehouseId : event.warehouseId(),
				event.quantity(),
				"ItemAgregado"
		));
	}

	@PostMapping("/pedido-proveedor-recibido")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void pedidoProveedorRecibido(@RequestBody PedidoProveedorRecibidoEvent event) {
		inventoryService.ensureProductExists(event.sku());
		inventoryService.reponerStock(new RestockCommand(
				event.eventId(),
				event.correlationId(),
				event.sku(),
				event.warehouseId() == null ? defaultWarehouseId : event.warehouseId(),
				event.quantity(),
				"PedidoProveedorRecibido"
		));
	}
}
