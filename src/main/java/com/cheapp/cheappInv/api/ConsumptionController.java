package com.cheapp.cheappInv.api;

import com.cheapp.cheappInv.infra.logging.Loggable;
import com.cheapp.cheappInv.infra.persistence.HistoricalConsumptionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api")
@Tag(name = "Consumption", description = "Consultas de consumo historico (solo lectura).")
@Loggable("consumption")
public class ConsumptionController {
	private final HistoricalConsumptionRepository repository;
	private final Clock clock;
	private final int defaultWindowDays;

	public ConsumptionController(HistoricalConsumptionRepository repository,
						   Clock clock,
						   @Value("${inventory.consumption.window-days:30}") int defaultWindowDays) {
		this.repository = repository;
		this.clock = clock;
		this.defaultWindowDays = defaultWindowDays;
	}

	@GetMapping("/consumption/average")
	@Operation(
			summary = "Consumo promedio por producto",
			description = "Calcula consumo promedio diario en una ventana configurable basada en historical_consumption.",
			responses = {
					@ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AverageConsumptionView.class)))
			}
	)
	public AverageConsumptionView average(@RequestParam String sku, @RequestParam(required = false) Integer windowDays) {
		int days = (windowDays == null || windowDays <= 0) ? defaultWindowDays : windowDays;
		Instant from = Instant.now(clock).minus(days, ChronoUnit.DAYS);
		long total = repository.sumQuantitySince(sku, from).orElse(0L);
		double avgPerDay = days == 0 ? 0d : ((double) total) / (double) days;
		return new AverageConsumptionView(sku, days, total, avgPerDay);
	}

	public record AverageConsumptionView(String sku, int windowDays, long totalConsumed, double averagePerDay) {
	}
}
