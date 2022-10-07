package com.bobo.msb.boot_02_auto_configuration;

import org.springframework.boot.SpringApplication;

/**
 * 自动装配的原理：通过Import导入spring.factories文件中的配置类，然后解析配置类，实现自动装配。
 *
 * 具体过程：
 * （1）会把SpringApplication.run()参数中的Class bd注册到beanFactory中
 * （2）然后在invokeBeanFactoryPostProcessors()时，会执行ConfigurationClassPostProcessor#postProcessBeanDefinitionRegistry()
 * （3）然后会检测到，由于SpringApplication.run()参数中的Class bd携带了@SpringBootApplication，内部写到了@Configuration，所以会作为一个配置类进行解析
 * （4）然后解析@SpringBootApplication里面的@EnableAutoConfiguration里面的@Import(AutoConfigurationImportSelector.class)
 * （5）AutoConfigurationImportSelector implements DeferredImportSelector，并且有返回Group
 * （6）所以，在所有的配置类解析完毕之后，会执行AutoConfigurationImportSelector.AutoConfigurationGroup#process()，里面会执行AutoConfigurationImportSelector#getAutoConfigurationEntry()
 * （7）在AutoConfigurationImportSelector#getAutoConfigurationEntry()里面，获取了spring.factories文件中的配置类，以及过滤之类的，得到最终的配置类
 * （8）然后解析配置类，实现自动装配
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
		// 会把SpringApplication.run()参数中的Class bd注册到beanFactory中，main所在的Class不会注册！
		SpringApplication.run(LabelSpringBootApplicationAnnotation.class,args);
	}

}
