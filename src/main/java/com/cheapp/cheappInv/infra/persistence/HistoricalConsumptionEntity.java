package com.cheapp.cheappInv.infra.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "historical_consumption", indexes = {
		@Index(name = "idx_hist_consumption_product_ts", columnList = "product_sku,created_at")
})
public class HistoricalConsumptionEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_sku", nullable = false)
	private String productSku;

	@Column(nullable = false)
	private long quantity;

	@Column(name = "comanda_id", nullable = false)
	private String comandaId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected HistoricalConsumptionEntity() {
	}

	public HistoricalConsumptionEntity(String productSku, long quantity, String comandaId, Instant createdAt) {
		this.productSku = productSku;
		this.quantity = quantity;
		this.comandaId = comandaId;
		this.createdAt = createdAt;
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public String getProductSku() {
		return productSku;
	}

	public long getQuantity() {
		return quantity;
	}
}
