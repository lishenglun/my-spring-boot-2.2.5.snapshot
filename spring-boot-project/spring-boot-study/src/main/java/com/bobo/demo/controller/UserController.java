package com.bobo.demo.controller;

import com.bobo.demo.event.MyEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

	@Autowired
	ApplicationContext context;

	@Value("${spring.application.name}")
	private String applicationName;

	@GetMapping("/hello")
	public String hello(){
		// 发布一个 自定义事件
		// context.publishEvent(new MyEvent(new Object()));
		return "hello-->" + applicationName ;
	}
}
