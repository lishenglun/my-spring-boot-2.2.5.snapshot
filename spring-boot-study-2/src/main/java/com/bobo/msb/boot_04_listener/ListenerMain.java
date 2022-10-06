package com.bobo.msb.boot_04_listener;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SpringBoot中的监听机制详解：
 * 1、spring boot有自己的一套监听器体系，接口是：SpringApplicationRunListener
 * 2、spring自己的一套监听器体系，接口是：SpringApplication
 * 3、spring boot和spring的监听器所处理的事件都是同一体系，都是spring的事件体系，接口是：ApplicationEvent
 */
@SpringBootApplication
public class ListenerMain {

	public static void main(String[] args) {
		SpringApplication.run(ListenerMain.class);
	}

}
