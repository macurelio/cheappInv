package com.cheapp.cheappInv.infra.persistence;

import com.cheapp.cheappInv.domain.MovementType;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "inventory_movements", indexes = {
		@Index(name = "idx_movements_product_ts", columnList = "product_id,created_at"),
		@Index(name = "idx_movements_product_wh_type_ts", columnList = "product_id,warehouse_id,type,created_at")
})
public class InventoryMovementEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private ProductEntity product;

	@Column(name = "warehouse_id", nullable = false)
	private String warehouseId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MovementType type;

	@Column(nullable = false)
	private long quantity;

	@Column(nullable = false)
	private String reason;

	@Column(name = "external_event_id")
	private String externalEventId;

	@Column(name = "correlation_id")
	private String correlationId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected InventoryMovementEntity() {
	}

	public InventoryMovementEntity(ProductEntity product,
								 String warehouseId,
								 MovementType type,
								 long quantity,
								 String reason,
								 String externalEventId,
								 String correlationId,
								 Instant createdAt) {
		this.product = product;
		this.warehouseId = warehouseId;
		this.type = type;
		this.quantity = quantity;
		this.reason = reason;
		this.externalEventId = externalEventId;
		this.correlationId = correlationId;
		this.createdAt = createdAt;
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public ProductEntity getProduct() {
		return product;
	}

	public String getWarehouseId() {
		return warehouseId;
	}

	public MovementType getType() {
		return type;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
