package com.bobo.msb.boot_02_auto_configuration;

import org.springframework.boot.SpringApplication;

/**
 * 自动装配的原理：通过Import导入spring.factories文件中的配置类，然后解析配置类，实现自动装配。
 */
public class AutoConfigurationMain {

	/**
	 * 1、spring.factories：存放配置类，以及过滤器
	 *
	 * （1）配置类key：org.springframework.boot.autoconfigure.EnableAutoConfiguration
	 *
	 * （2）过滤器key：org.springframework.boot.autoconfigure.AutoConfigurationImportFilter
	 *
	 * 2、spring-autoconfigure-metadata.properties：存放配置类被加载的条件
	 *
	 * 3、需要写starter的场景：自己开发一个组件，需要整合到某个spring  boot开发的业务系统里面去，这个时候可以写一个starter
	 */
	public static void main(String[] args) {
		SpringApplication.run(LabelSpringBootApplicationAnnotation.class,args);
	}

}
