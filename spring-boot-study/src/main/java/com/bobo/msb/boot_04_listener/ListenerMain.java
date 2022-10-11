package com.bobo.msb.boot_04_listener;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SpringBoot中的监听机制详解：
 *
 * 1、spring boot有自己的一套监听器体系，接口是：SpringApplicationRunListener
 *
 * 题外：spring boot启动时的广播器是：SpringApplicationRunListeners
 *
 * 2、spring自己的一套监听器体系，接口是：SpringApplication
 *
 * 3、虽然spring boot和spring它们各自都有一套自己的监听器体系，但是spring boot监听器体系，最终走的是spring的监听器体系。
 * 之所以可以走spring的监听器体系，是因为spring boot和spring的监听器所处理的事件都是同一体系，都是spring的事件体系，接口是：ApplicationEvent
 */
@SpringBootApplication
public class ListenerMain {

	public static void main(String[] args) {
		SpringApplication.run(ListenerMain.class);
	}

}
