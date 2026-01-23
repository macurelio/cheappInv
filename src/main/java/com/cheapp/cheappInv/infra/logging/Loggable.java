package com.cheapp.cheappInv.infra.logging;

import java.lang.annotation.*;

/**
 * Marca métodos/clases para log automático de entrada/salida.
 *
 * Nota: el body no se loguea para evitar filtrar datos o generar ruido.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Loggable {
	/**
	 * Mensaje corto para identificar la operación (opcional).
	 */
	String value() default "";
}
