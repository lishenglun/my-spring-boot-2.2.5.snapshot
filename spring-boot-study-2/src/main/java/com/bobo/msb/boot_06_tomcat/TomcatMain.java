package com.bobo.msb.boot_06_tomcat;

import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description spring boot如何内嵌tomcat
 * @date 2022/9/30 11:58
 */
public class TomcatMain {

	/**
	 * 1、spring boot去内嵌tomcat，在自动配置里面要查看的2个关键点
	 *
	 * {@link EmbeddedWebServerFactoryCustomizerAutoConfiguration}
	 * {@link ServletWebServerFactoryAutoConfiguration}
	 *
	 * 2、onRefresh()的实现找：{@link org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext}
	 */

}