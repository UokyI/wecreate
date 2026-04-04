package com.wecreate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class WecreateApplication {

	public static void main(String[] args) {
		SpringApplication.run(WecreateApplication.class, args);
	}

}