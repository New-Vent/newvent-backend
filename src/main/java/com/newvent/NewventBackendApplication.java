package com.newvent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class NewventBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(NewventBackendApplication.class, args);
	}

}
