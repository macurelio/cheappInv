package com.cheapp.cheappInv.infra.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Filtro de logging para:
 * - asignar/propagar correlationId
 * - enriquecer MDC (correlationId, method, path)
 * - devolver el correlationId en la respuesta
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingFilter extends OncePerRequestFilter {
	public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
	public static final String CORRELATION_ID_MDC_KEY = "correlationId";

	private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		long startNs = System.nanoTime();

		String correlationId = Optional.ofNullable(request.getHeader(CORRELATION_ID_HEADER))
				.filter(v -> !v.isBlank())
				.orElseGet(() -> UUID.randomUUID().toString());

		MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
		MDC.put("httpMethod", request.getMethod());
		MDC.put("httpPath", request.getRequestURI());

		response.setHeader(CORRELATION_ID_HEADER, correlationId);

		String query = request.getQueryString();
		log.info("http stage=request method={} path={} query={}", request.getMethod(), request.getRequestURI(), query);
		try {
			filterChain.doFilter(request, response);
		} finally {
			long tookMs = (System.nanoTime() - startNs) / 1_000_000;
			log.info("http stage=response method={} path={} status={} tookMs={}", request.getMethod(), request.getRequestURI(), response.getStatus(), tookMs);
			MDC.remove(CORRELATION_ID_MDC_KEY);
			MDC.remove("httpMethod");
			MDC.remove("httpPath");
		}
	}
}
