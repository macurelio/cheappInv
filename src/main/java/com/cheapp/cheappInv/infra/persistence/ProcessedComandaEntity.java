package com.cheapp.cheappInv.infra.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "processed_comandas", uniqueConstraints = {
		@UniqueConstraint(name = "uk_processed_comanda_id", columnNames = {"comanda_id"})
})
public class ProcessedComandaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "comanda_id", nullable = false, updatable = false)
	private String comandaId;

	@Column(name = "processed_at", nullable = false, updatable = false)
	private Instant processedAt;

	protected ProcessedComandaEntity() {
	}

	public ProcessedComandaEntity(String comandaId, Instant processedAt) {
		this.comandaId = comandaId;
		this.processedAt = processedAt;
	}

	public String getComandaId() {
		return comandaId;
	}
}
