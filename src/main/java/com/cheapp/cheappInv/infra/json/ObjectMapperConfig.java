package com.cheapp.cheappInv.infra.json;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class ObjectMapperConfig {
	@Bean
	JsonMapper jsonMapper() {
		return JsonMapper.builder().build();
	}
}
