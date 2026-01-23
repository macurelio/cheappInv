package com.cheapp.cheappInv.infra.events;

import java.time.Instant;

public record EventEnvelope<T>(
		String eventId,
		String eventType,
		int schemaVersion,
		Instant occurredAt,
		String correlationId,
		String causationId,
		T payload
) {
}
