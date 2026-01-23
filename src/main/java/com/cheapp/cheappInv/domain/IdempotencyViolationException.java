package com.cheapp.cheappInv.domain;

public class IdempotencyViolationException extends InventoryException {
	public IdempotencyViolationException(String eventId) {
		super("Evento ya procesado eventId=" + eventId);
	}
}
