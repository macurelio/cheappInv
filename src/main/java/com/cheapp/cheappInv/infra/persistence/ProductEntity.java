package com.cheapp.cheappInv.infra.persistence;

import com.cheapp.cheappInv.domain.ProductStatus;
import com.cheapp.cheappInv.domain.UnitCode;
import com.cheapp.cheappInv.domain.UnitType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "products", indexes = {
		@Index(name = "idx_products_sku", columnList = "sku", unique = true),
		@Index(name = "idx_products_name", columnList = "name")
})
public class ProductEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private String sku;

	@Column
	private String name;

	@Column
	private String brand;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProductStatus status = ProductStatus.ACTIVE;

	@Enumerated(EnumType.STRING)
	@Column(name = "unit_type")
	private UnitType unitType;

	@Column(name = "unit_amount")
	private BigDecimal unitAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "unit_code")
	private UnitCode unitCode;

	@Column(name = "estimated_shelf_life_days")
	private Integer estimatedShelfLifeDays;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(
			name = "product_categories",
			joinColumns = @JoinColumn(name = "product_id"),
			inverseJoinColumns = @JoinColumn(name = "category_id")
	)
	private Set<CategoryEntity> categories = new HashSet<>();

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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getBrand() {
		return brand;
	}

	public void setBrand(String brand) {
		this.brand = brand;
	}

	public ProductStatus getStatus() {
		return status;
	}

	public void setStatus(ProductStatus status) {
		this.status = status;
	}

	public UnitType getUnitType() {
		return unitType;
	}

	public void setUnitType(UnitType unitType) {
		this.unitType = unitType;
	}

	public BigDecimal getUnitAmount() {
		return unitAmount;
	}

	public void setUnitAmount(BigDecimal unitAmount) {
		this.unitAmount = unitAmount;
	}

	public UnitCode getUnitCode() {
		return unitCode;
	}

	public void setUnitCode(UnitCode unitCode) {
		this.unitCode = unitCode;
	}

	public Integer getEstimatedShelfLifeDays() {
		return estimatedShelfLifeDays;
	}

	public void setEstimatedShelfLifeDays(Integer estimatedShelfLifeDays) {
		this.estimatedShelfLifeDays = estimatedShelfLifeDays;
	}

	public Set<CategoryEntity> getCategories() {
		return categories;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
