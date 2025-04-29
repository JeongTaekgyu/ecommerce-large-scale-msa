package com.example.timesaleservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient // Eureka 서버와 통신할 수 있도록 설정
public class TimeSaleServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TimeSaleServiceApplication.class, args);
	}

}
