package com.cheapp.cheappInv.api;

import com.cheapp.cheappInv.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {
	@ExceptionHandler(ProductNotFoundException.class)
	public ResponseEntity<ApiError> notFound(ProductNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("PRODUCT_NOT_FOUND", ex.getMessage()));
	}

	@ExceptionHandler(ProductBlockedException.class)
	public ResponseEntity<ApiError> blocked(ProductBlockedException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("PRODUCT_BLOCKED", ex.getMessage()));
	}

	@ExceptionHandler(StockInsufficientException.class)
	public ResponseEntity<ApiError> stock(StockInsufficientException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("STOCK_INSUFFICIENT", ex.getMessage()));
	}

	@ExceptionHandler(IdempotencyViolationException.class)
	public ResponseEntity<ApiError> idem(IdempotencyViolationException ex) {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiError("DUPLICATE_EVENT", ex.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiError> badRequest(IllegalArgumentException ex) {
		return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", ex.getMessage()));
	}

	public record ApiError(String code, String message) {
	}
}
