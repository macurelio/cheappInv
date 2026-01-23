package com.cheapp.cheappInv.domain;

public abstract class InventoryException extends RuntimeException {
	protected InventoryException(String message) {
		super(message);
	}
}
