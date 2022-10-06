package com.bobo.msb.boot_05_config_file;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SpringBoot中的属性文件加载原理分析
 *
 * application.yml和bootstrap.properties的关系和区别
 *
 * ⚠️参考【spring-boot-study-bootstrap】模块
 */
@SpringBootApplication
public class ConfigurationFileMain {

	public static void main(String[] args) {
		SpringApplication.run(ConfigurationFileMain.class);
	}

}
