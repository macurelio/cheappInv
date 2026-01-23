package com.cheapp.cheappInv.infra.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "outbox_events", indexes = {
		@Index(name = "idx_outbox_status_created", columnList = "published,created_at")
})
public class OutboxEventEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, updatable = false, unique = true)
	private String eventId;

	@Column(name = "event_type", nullable = false, updatable = false)
	private String eventType;

	@Column(name = "schema_version", nullable = false, updatable = false)
	private int schemaVersion;

	@Column(name = "correlation_id")
	private String correlationId;

	@Lob
	@Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
	private String payloadJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private boolean published;

	@Column(name = "published_at")
	private Instant publishedAt;

	protected OutboxEventEntity() {
	}

	public OutboxEventEntity(String eventId,
							 String eventType,
							 int schemaVersion,
							 String correlationId,
							 String payloadJson,
							 Instant createdAt) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.schemaVersion = schemaVersion;
		this.correlationId = correlationId;
		this.payloadJson = payloadJson;
		this.createdAt = createdAt;
		this.published = false;
	}

	public Long getId() {
		return id;
	}

	public boolean isPublished() {
		return published;
	}

	public void markPublished(Instant when) {
		this.published = true;
		this.publishedAt = when;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayloadJson() {
		return payloadJson;
	}

	public String getEventId() {
		return eventId;
	}

	public String getCorrelationId() {
		return correlationId;
	}
}
