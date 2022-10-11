package com.bobo.msb.boot_06_tomcat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description spring boot如何内嵌tomcat？
 *
 * （1）有一个自动装配类{@link ServletWebServerFactoryAutoConfiguration}，会对其中的内容，进行自动装配
 * 里面@Import了一个{@link org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryConfiguration.EmbeddedTomcat}，
 * 注册了一个TomcatServletWebServerFactory bean
 *
 * （2）然后在refresh() ——> onRefresh()的时候，会调用{@link ServletWebServerApplicationContext#onRefresh()}，
 *
 * 里面先获取一个ServletWebServerFactory，默认得到的是TomcatServletWebServerFactory
 *
 * 然后通过TomcatServletWebServerFactory，获取了一个WebServer = TomcatWebServer，在获取TomcatWebServer的过程中，里面创建了一堆tomcat相关的组件，从而完成了tomcat的嵌入
 *
 * @date 2022/9/30 11:58
 */
@SpringBootApplication
public class TomcatMain {

	/**
	 * 1、spring boot去内嵌tomcat，在自动配置里面要查看的2个关键点，执行的先后顺序是：
	 *
	 * {@link ServletWebServerFactoryAutoConfiguration}
	 * org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration
	 *
	 * {@link EmbeddedWebServerFactoryCustomizerAutoConfiguration}
	 * org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration
	 *
	 * 2、onRefresh()的实现找：{@link org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext}
	 */
	public static void main(String[] args) {
		SpringApplication.run(TomcatMain.class, args);
	}

}