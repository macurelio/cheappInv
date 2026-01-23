package com.cheapp.cheappInv.infra.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockProvider {
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
