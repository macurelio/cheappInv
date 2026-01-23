package com.cheapp.cheappInv.infra.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingOutboxPublisher implements OutboxPublisher {
	private static final Logger log = LoggerFactory.getLogger(LoggingOutboxPublisher.class);

	@Override
	public void publish(String eventId, String eventType, String correlationId, String payloadJson) {
		log.info("OUTBOX publish eventType={} eventId={} correlationId={} payload={}", eventType, eventId, correlationId, payloadJson);
	}
}
