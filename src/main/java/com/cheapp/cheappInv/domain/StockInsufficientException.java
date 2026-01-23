package com.cheapp.cheappInv.domain;

public class StockInsufficientException extends InventoryException {
	public StockInsufficientException(String sku, long requested, long available) {
		super("Stock insuficiente para sku=" + sku + ". requerido=" + requested + ", disponible=" + available);
	}
}
