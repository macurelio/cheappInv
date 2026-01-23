package com.cheapp.cheappInv.domain;

public class ProductBlockedException extends InventoryException {
	public ProductBlockedException(String sku) {
		super("Producto bloqueado sku=" + sku);
	}
}
