package com.cheapp.cheappInv.infra.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "inbox_events", indexes = {
		@Index(name = "idx_inbox_received_at", columnList = "received_at")
}, uniqueConstraints = {
		@UniqueConstraint(name = "uk_inbox_event_id", columnNames = {"event_id"})
})
public class InboxEventEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, updatable = false)
	private String eventId;

	@Column(name = "event_type", nullable = false, updatable = false)
	private String eventType;

	@Column(name = "received_at", nullable = false, updatable = false)
	private Instant receivedAt;

	protected InboxEventEntity() {
	}

	public InboxEventEntity(String eventId, String eventType, Instant receivedAt) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.receivedAt = receivedAt;
	}
}
