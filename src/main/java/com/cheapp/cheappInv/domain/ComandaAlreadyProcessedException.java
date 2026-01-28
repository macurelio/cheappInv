package com.cheapp.cheappInv.domain;

public class ComandaAlreadyProcessedException extends InventoryException {
	public ComandaAlreadyProcessedException(String comandaId) {
		super("Comanda ya procesada: " + comandaId);
	}
}
