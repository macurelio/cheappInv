package com.cheapp.cheappInv.infra.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "stock", uniqueConstraints = {
		@UniqueConstraint(name = "uk_stock_product_wh", columnNames = {"product_id", "warehouse_id"})
})
public class StockEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private ProductEntity product;

	@Column(name = "warehouse_id", nullable = false)
	private String warehouseId;

	@Column(nullable = false)
	private long quantity;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected StockEntity() {
	}

	public StockEntity(ProductEntity product, String warehouseId, long quantity, Instant now) {
		this.product = product;
		this.warehouseId = warehouseId;
		this.quantity = quantity;
		this.updatedAt = now;
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

	public long getQuantity() {
		return quantity;
	}

	public void setQuantity(long quantity) {
		this.quantity = quantity;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
