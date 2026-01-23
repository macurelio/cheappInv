package com.cheapp.cheappInv.infra.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
public class LoggingAspect {

	@Around("@within(com.cheapp.cheappInv.infra.logging.Loggable) || @annotation(com.cheapp.cheappInv.infra.logging.Loggable)")
	public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
		long startNs = System.nanoTime();

		MethodSignature sig = (MethodSignature) pjp.getSignature();
		Method method = sig.getMethod();
		Logger log = LoggerFactory.getLogger(sig.getDeclaringType());

		Loggable ann = method.getAnnotation(Loggable.class);
		if (ann == null) {
			ann = ((Class<?>) sig.getDeclaringType()).getAnnotation(Loggable.class);
		}
		String op = (ann != null && !ann.value().isBlank()) ? ann.value() : sig.getDeclaringType().getSimpleName() + "." + method.getName();

		Map<String, Object> args = extractArgs(sig.getParameterNames(), pjp.getArgs());
		log.info("op={} stage=start args={}", op, args);

		try {
			Object result = pjp.proceed();
			long tookMs = (System.nanoTime() - startNs) / 1_000_000;
			log.info("op={} stage=success tookMs={}", op, tookMs);
			return result;
		} catch (Exception ex) {
			long tookMs = (System.nanoTime() - startNs) / 1_000_000;
			log.warn("op={} stage=error tookMs={} errorType={} message={}", op, tookMs, ex.getClass().getSimpleName(), ex.getMessage());
			throw ex;
		}
	}

	private Map<String, Object> extractArgs(String[] names, Object[] values) {
		Map<String, Object> out = new LinkedHashMap<>();
		if (values == null || values.length == 0) {
			return out;
		}

		for (int i = 0; i < values.length; i++) {
			String name = (names != null && i < names.length && names[i] != null) ? names[i] : ("arg" + i);
			Object v = values[i];

			// Evitar loguear bodies grandes o sensibles: si es un objeto que no sea primitivo/string/number/bool, marcamos.
			if (v != null && !(v instanceof String || v instanceof Number || v instanceof Boolean || v.getClass().isPrimitive())) {
				out.put(name, "<" + v.getClass().getSimpleName() + ">");
			} else {
				out.put(name, v);
			}
		}
		return out;
	}
}
