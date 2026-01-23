package com.cheapp.cheappInv.application;

import com.cheapp.cheappInv.application.commands.BlockProductCommand;
import com.cheapp.cheappInv.application.commands.DiscountStockCommand;
import com.cheapp.cheappInv.application.commands.RestockCommand;
import com.cheapp.cheappInv.domain.*;
import com.cheapp.cheappInv.infra.persistence.*;
import com.cheapp.cheappInv.support.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryServiceTest {
	private ProductRepository productRepository;
	private StockRepository stockRepository;
	private InventoryMovementRepository movementRepository;
	private InboxEventRepository inboxEventRepository;
	private OutboxEventRepository outboxEventRepository;
	private JsonMapper jsonMapper;
	private Clock clock;

	private InventoryService service;

	@BeforeEach
	void setUp() {
		productRepository = mock(ProductRepository.class);
		stockRepository = mock(StockRepository.class);
		movementRepository = mock(InventoryMovementRepository.class);
		inboxEventRepository = mock(InboxEventRepository.class);
		outboxEventRepository = mock(OutboxEventRepository.class);
		jsonMapper = mock(JsonMapper.class);
		clock = TestClock.fixedUtc();

		service = new InventoryService(productRepository, stockRepository, movementRepository, inboxEventRepository, outboxEventRepository, jsonMapper, clock);
	}

	@Test
	void reponerStock_creaStockSiNoExiste_yGuardaMovimientoInboxYOutbox() throws Exception {
		var now = Instant.now(clock);
		var product = new ProductEntity("SKU-1", now);
		// set id via reflection no es necesario; para repositorio usamos el mismo objeto.

		when(inboxEventRepository.existsByEventId("evt-1")).thenReturn(false);
		when(productRepository.findBySku("SKU-1")).thenReturn(Optional.of(product));
		when(stockRepository.findForUpdate(any(), eq("MAIN"))).thenReturn(Optional.empty());
		when(stockRepository.save(any(StockEntity.class))).thenAnswer(inv -> inv.getArgument(0));
		when(jsonMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");
		when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.reponerStock(new RestockCommand("evt-1", "corr-1", "SKU-1", "MAIN", 5, "PedidoProveedorRecibido"));

		verify(stockRepository, atLeastOnce()).save(argThat(s -> s.getQuantity() == 5));
		verify(movementRepository).save(any(InventoryMovementEntity.class));
		verify(inboxEventRepository).save(any(InboxEventEntity.class));

		ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
		verify(outboxEventRepository).save(outboxCaptor.capture());
		assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("StockRepuesto");
		assertThat(outboxCaptor.getValue().getCorrelationId()).isEqualTo("corr-1");
		assertThat(outboxCaptor.getValue().getPayloadJson()).contains("ok");
	}

	@Test
	void descontarStock_lanzaStockInsufficient_siNoHaySuficiente() {
		var now = Instant.now(clock);
		var product = new ProductEntity("SKU-1", now);
		var stock = new StockEntity(product, "MAIN", 3, now);

		when(inboxEventRepository.existsByEventId("evt-1")).thenReturn(false);
		when(productRepository.findBySku("SKU-1")).thenReturn(Optional.of(product));
		when(stockRepository.findForUpdate(any(), eq("MAIN"))).thenReturn(Optional.of(stock));

		assertThatThrownBy(() -> service.descontarStockPorItem(new DiscountStockCommand("evt-1", "corr-1", "SKU-1", "MAIN", 5, "ItemAgregado")))
				.isInstanceOf(StockInsufficientException.class);

		verify(outboxEventRepository, never()).save(any());
		verify(movementRepository, never()).save(any());
		verify(inboxEventRepository, never()).save(any(InboxEventEntity.class));
	}

	@Test
	void descontarStock_lanzaProductBlocked_siProductoBloqueado() {
		var now = Instant.now(clock);
		var product = new ProductEntity("SKU-1", now);
		product.setStatus(ProductStatus.BLOCKED);

		when(inboxEventRepository.existsByEventId("evt-1")).thenReturn(false);
		when(productRepository.findBySku("SKU-1")).thenReturn(Optional.of(product));

		assertThatThrownBy(() -> service.descontarStockPorItem(new DiscountStockCommand("evt-1", "corr-1", "SKU-1", "MAIN", 1, "ItemAgregado")))
				.isInstanceOf(ProductBlockedException.class);
	}

	@Test
	void idempotencia_lanzaIdempotencyViolation_siEventIdYaProcesado() {
		when(inboxEventRepository.existsByEventId("evt-dup")).thenReturn(true);

		assertThatThrownBy(() -> service.reponerStock(new RestockCommand("evt-dup", "corr-1", "SKU-1", "MAIN", 1, "PedidoProveedorRecibido")))
				.isInstanceOf(IdempotencyViolationException.class);

		verifyNoInteractions(productRepository, stockRepository, movementRepository, outboxEventRepository);
	}

	@Test
	void bloquearProducto_publicaOutbox_yActualizaEstado() throws Exception {
		var now = Instant.now(clock);
		var product = new ProductEntity("SKU-1", now);

		when(productRepository.findBySku("SKU-1")).thenReturn(Optional.of(product));
		when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(jsonMapper.writeValueAsString(any())).thenReturn("{\"sku\":\"SKU-1\"}");
		when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.bloquearProducto(new BlockProductCommand("corr-1", "SKU-1", "retiro"));

		assertThat(product.getStatus()).isEqualTo(ProductStatus.BLOCKED);
		verify(outboxEventRepository).save(argThat(e -> e.getEventType().equals("ProductoBloqueado")));
	}

	@Test
	void cantidadInvalida_lanzaIllegalArgumentException() {
		assertThatThrownBy(() -> service.reponerStock(new RestockCommand("evt-1", "corr-1", "SKU-1", "MAIN", 0, "PedidoProveedorRecibido")))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
