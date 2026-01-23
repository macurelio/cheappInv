package com.cheapp.cheappInv.api;

import com.cheapp.cheappInv.application.InventoryService;
import com.cheapp.cheappInv.application.commands.DiscountStockCommand;
import com.cheapp.cheappInv.application.commands.RestockCommand;
import com.cheapp.cheappInv.infra.events.consumed.ItemAgregadoEvent;
import com.cheapp.cheappInv.infra.events.consumed.PedidoProveedorRecibidoEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint opcional para simular consumo de eventos (en real se conectaría a Kafka/Rabbit/SQS).
 */
@RestController
@RequestMapping("/api/events/in")
@Tag(name = "Events (in)", description = "Simulación de consumo de eventos de otros microservicios (entrada).")
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
	@Operation(
			summary = "Consumir evento: ItemAgregado",
			description = "Simula el consumo del evento ItemAgregado. Descuenta stock de forma transaccional, con locking pesimista e idempotencia por eventId.",
			responses = {
					@ApiResponse(responseCode = "202", description = "Evento aceptado/procesado"),
					@ApiResponse(responseCode = "202", description = "Evento duplicado (idempotencia)", content = @Content(schema = @Schema(implementation = RestExceptionHandler.ApiError.class))),
					@ApiResponse(responseCode = "409", description = "Conflicto (stock insuficiente o producto bloqueado)", content = @Content(schema = @Schema(implementation = RestExceptionHandler.ApiError.class))),
					@ApiResponse(responseCode = "400", description = "Request inválido", content = @Content(schema = @Schema(implementation = RestExceptionHandler.ApiError.class)))
			}
	)
	public void itemAgregado(@RequestBody @Schema(implementation = com.cheapp.cheappInv.infra.events.consumed.ItemAgregadoEvent.class) ItemAgregadoEvent event) {
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
	@Operation(
			summary = "Consumir evento: PedidoProveedorRecibido",
			description = "Simula el consumo del evento PedidoProveedorRecibido. Repone stock de forma transaccional, con locking pesimista e idempotencia por eventId.",
			responses = {
					@ApiResponse(responseCode = "202", description = "Evento aceptado/procesado"),
					@ApiResponse(responseCode = "202", description = "Evento duplicado (idempotencia)", content = @Content(schema = @Schema(implementation = RestExceptionHandler.ApiError.class))),
					@ApiResponse(responseCode = "400", description = "Request inválido", content = @Content(schema = @Schema(implementation = RestExceptionHandler.ApiError.class)))
			}
	)
	public void pedidoProveedorRecibido(@RequestBody @Schema(implementation = com.cheapp.cheappInv.infra.events.consumed.PedidoProveedorRecibidoEvent.class) PedidoProveedorRecibidoEvent event) {
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
