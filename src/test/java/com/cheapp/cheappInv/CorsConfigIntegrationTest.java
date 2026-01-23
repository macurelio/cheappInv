package com.cheapp.cheappInv;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsConfigIntegrationTest {
	@LocalServerPort
	int port;

	@Test
	void preflight_from_react_vite_origin_is_allowed() {
		WebTestClient webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

		webTestClient
				.method(org.springframework.http.HttpMethod.OPTIONS)
				.uri("/api/stock?sku=SKU-1&warehouseId=MAIN")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "GET")
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
	}
}
