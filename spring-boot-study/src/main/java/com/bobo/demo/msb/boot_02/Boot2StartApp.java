package com.bobo.demo.msb.boot_02;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 自动装配的原理：通过Import导入spring.factories文件中的配置类，然后解析配置类，实现自动装配。
 */
@SpringBootApplication
public class Boot2StartApp {

	/**
	 * 1、spring.factories：存放配置类。
	 *
	 * 2、spring-autoconfigure-metadata.properties：存放配置类被加载的条件
	 *
	 * 3、自己开发一个组件，需要整合到业务系统里面去，这个 时候可以写一个starter
	 */
	public static void main(String[] args) {
		SpringApplication.run(Boot2StartApp.class,args);
	}

}
