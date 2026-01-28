package com.cheapp.cheappInv.application;

import com.cheapp.cheappInv.application.commands.ConsumeComandaCommand;
import com.cheapp.cheappInv.domain.ComandaAlreadyProcessedException;
import com.cheapp.cheappInv.domain.StockInsufficientException;
import com.cheapp.cheappInv.domain.recipes.RecipeStatus;
import com.cheapp.cheappInv.infra.persistence.*;
import com.cheapp.cheappInv.support.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsumeComandaUseCaseTest {
	ProductRepository productRepository;
	StockRepository stockRepository;
	InventoryMovementRepository movementRepository;
	InboxEventRepository inboxEventRepository;
	OutboxEventRepository outboxEventRepository;
	RecipesRepository recipesRepository;
	ProcessedComandaRepository processedComandaRepository;
	HistoricalConsumptionRepository historicalConsumptionRepository;
	JsonMapper jsonMapper;
	Clock clock;
	InventoryService service;

	@BeforeEach
	void setUp() {
		productRepository = mock(ProductRepository.class);
		stockRepository = mock(StockRepository.class);
		movementRepository = mock(InventoryMovementRepository.class);
		inboxEventRepository = mock(InboxEventRepository.class);
		outboxEventRepository = mock(OutboxEventRepository.class);
		recipesRepository = mock(RecipesRepository.class);
		processedComandaRepository = mock(ProcessedComandaRepository.class);
		historicalConsumptionRepository = mock(HistoricalConsumptionRepository.class);
		jsonMapper = mock(JsonMapper.class);
		clock = TestClock.fixedUtc();

		service = new InventoryService(productRepository, stockRepository, movementRepository, inboxEventRepository, outboxEventRepository,
				jsonMapper, clock, recipesRepository, processedComandaRepository, historicalConsumptionRepository);
	}

	@Test
	void comanda_idempotent_by_comandaId() {
		when(processedComandaRepository.existsByComandaId("c-1")).thenReturn(true);

		assertThatThrownBy(() -> service.descontarStockPorReceta(new ConsumeComandaCommand(
				eq("evt-1"), eq("corr"), eq("c-1"), List.of(new ConsumeComandaCommand.ComandaDishLine("D-1", 1)), "MAIN"
		))).isInstanceOf(ComandaAlreadyProcessedException.class);

		verifyNoInteractions(stockRepository);
	}

	@Test
	void throws_when_stock_insufficient() {
		Instant now = Instant.now(clock);
		var product = new ProductEntity("ING-1", now);
		var stock = new StockEntity(product, "MAIN", 1, now);
		var recipe = new RecipeEntity("r-1", "D-1", 1, RecipeStatus.ACTIVE, now);
		recipe.addIngredient(new RecipeIngredientEntity(recipe, "ING-1", 3, null));

		when(processedComandaRepository.existsByComandaId("c-1")).thenReturn(false);
		when(inboxEventRepository.existsByEventId(any())).thenReturn(false);
		when(recipesRepository.findActiveByDishSku("D-1")).thenReturn(Optional.of(recipe));
		when(productRepository.findBySku("ING-1")).thenReturn(Optional.of(product));
		when(stockRepository.findForUpdate(any(), eq("MAIN"))).thenReturn(Optional.of(stock));
		when(jsonMapper.writeValueAsString(any())).thenReturn("{}");

		assertThatThrownBy(() -> service.descontarStockPorReceta(new ConsumeComandaCommand(
				"evt-1", "corr", "c-1", List.of(new ConsumeComandaCommand.ComandaDishLine("D-1", 1)), "MAIN"
		))).isInstanceOf(StockInsufficientException.class);

		verify(outboxEventRepository, atLeastOnce()).save(any());
		verify(processedComandaRepository, never()).save(any());
	}
}
