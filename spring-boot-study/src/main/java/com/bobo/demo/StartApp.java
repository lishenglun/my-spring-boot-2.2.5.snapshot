package com.bobo.demo;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
//@Configuration
public class StartApp {

	public static void main(String[] args) {
		// SpringBoot启动，就是一个Spring容器初始化的过程
		SpringApplication.run(StartApp.class, args);
	}

}
