package com.cheapp.cheappInv.domain;

public class ProductNotFoundException extends InventoryException {
	public ProductNotFoundException(String sku) {
		super("Producto no existe sku=" + sku);
	}
}
