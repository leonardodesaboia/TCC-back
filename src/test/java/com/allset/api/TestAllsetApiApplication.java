package com.allset.api;

import org.springframework.boot.SpringApplication;

public class TestAllsetApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(AllsetApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
