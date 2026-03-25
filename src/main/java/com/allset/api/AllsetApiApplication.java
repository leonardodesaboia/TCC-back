package com.allset.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AllsetApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AllsetApiApplication.class, args);
	}

}
