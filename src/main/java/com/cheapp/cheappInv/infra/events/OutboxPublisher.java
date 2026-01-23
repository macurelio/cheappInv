package com.cheapp.cheappInv.infra.events;

public interface OutboxPublisher {
	void publish(String eventId, String eventType, String correlationId, String payloadJson);
}
