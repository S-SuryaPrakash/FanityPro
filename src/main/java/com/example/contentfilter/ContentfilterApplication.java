package com.example.contentfilter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.example.contentfilter")
public class ContentfilterApplication {

	public static void main(String[] args) {
		SpringApplication.run(ContentfilterApplication.class, args);
	}

}

