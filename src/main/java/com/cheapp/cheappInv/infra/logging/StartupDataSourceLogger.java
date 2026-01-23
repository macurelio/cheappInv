package com.cheapp.cheappInv.infra.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Loguea al arranque qué datasource/URL está usando la app.
 * Esto ayuda a detectar rápido si estás corriendo contra H2 (perfil dev) o Postgres (perfil postgres).
 */
@Component
public class StartupDataSourceLogger implements ApplicationRunner {
	private static final Logger log = LoggerFactory.getLogger(StartupDataSourceLogger.class);

	private final DataSource dataSource;
	private final Environment env;

	public StartupDataSourceLogger(DataSource dataSource, Environment env) {
		this.dataSource = dataSource;
		this.env = env;
	}

	@Override
	public void run(ApplicationArguments args) {
		String[] profiles = env.getActiveProfiles();
		log.info("Active profiles: {}", profiles.length == 0 ? "<none>" : String.join(",", profiles));
		log.info("Configured spring.datasource.url: {}", env.getProperty("spring.datasource.url"));

		try (Connection c = dataSource.getConnection()) {
			String url = c.getMetaData().getURL();
			String user = c.getMetaData().getUserName();
			String product = c.getMetaData().getDatabaseProductName();
			String version = c.getMetaData().getDatabaseProductVersion();
			log.info("Connected to DB: product={} version={} url={} user={}", product, version, url, user);
		} catch (Exception e) {
			log.error("No se pudo abrir conexión a la BD al arranque: {}", e.getMessage(), e);
		}
	}
}
