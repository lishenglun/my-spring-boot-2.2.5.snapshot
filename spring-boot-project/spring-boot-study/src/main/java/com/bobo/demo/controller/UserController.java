package com.bobo.demo.controller;

import com.bobo.demo.event.MyEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

	// 通过ApplicationContext发布事件
	@Autowired
	ApplicationContext context;

	@Value("${spring.application.name}")
	private String applicationName;

	// 触发hello请求的时候，触发一个事件，事件要被监听器捕获到
	@GetMapping("/hello")
	public String hello(){
		// 发布一个自定义事件
		// 在业务里面通过监听器，帮助我们处理一些行为
		 context.publishEvent(new MyEvent(new Object()));

		return "hello-->" + applicationName ;
	}

}
