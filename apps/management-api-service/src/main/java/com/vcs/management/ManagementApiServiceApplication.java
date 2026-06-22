package com.vcs.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ManagementApiServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ManagementApiServiceApplication.class, args);
	}

}
