package com.cheapp.cheappInv.infra.persistence;

import com.cheapp.cheappInv.domain.ProductStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "products", indexes = {
		@Index(name = "idx_products_sku", columnList = "sku", unique = true)
})
public class ProductEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private String sku;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProductStatus status = ProductStatus.ACTIVE;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected ProductEntity() {
	}

	public ProductEntity(String sku, Instant now) {
		this.sku = sku;
		this.status = ProductStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
		if (updatedAt == null) {
			updatedAt = createdAt;
		}
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getSku() {
		return sku;
	}

	public ProductStatus getStatus() {
		return status;
	}

	public void setStatus(ProductStatus status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
