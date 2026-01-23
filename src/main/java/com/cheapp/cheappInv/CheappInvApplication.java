package com.cheapp.cheappInv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CheappInvApplication {

	public static void main(String[] args) {
		SpringApplication.run(CheappInvApplication.class, args);
	}

}
