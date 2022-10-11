package com.bobo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 一、SpringBoot中配置文件的加载原理分析
 *
 * 二、application.properties和bootstrap.properties的关系和区别
 *
 * 1、application.properties文件是spring boot中的配置文件，一般是配置当前项目下的一些配置，比如连接数据库，mybatis配置
 *
 * 2、bootstrap.properties文件是spring-cloud中的配置文件，由于【bootstrap.properties会优先于application.properties加载】，所以bootstrap.properties里面配置的，一般是连接配置中心的信息，
 * 这样在系统的时候，会先加载配置中心的配置，然后再加载application.properties文件中的配置
 *
 * 3、关系
 * application.properties对应一个SpringApplication容器，bootstrap.properties也对应一个SpringApplication容器，
 * 它们之间有一个父子关系，bootstrap.properties的容器是application.properties的容器的父容器
 *
 * 4、区别
 *
 * （1）bootstrap.properties会优先于application.properties加载
 *
 * （2）spring boot里面只支持application.properties，并不支持bootstrap.properties的；
 * bootstrap.properties是spring-cloud-context模块支持的，也就是说必须在spring cloud里面才能够进行使用。
 *
 * {@link org.springframework.boot.context.config.ConfigFileApplicationListener}处理application.properties
 * {@link org.springframework.cloud.bootstrap.BootstrapApplicationListener}处理bootstrap.properties
 *
 */
@SpringBootApplication
public class SpringBootBootstrapDemoApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext run = SpringApplication.run(SpringBootBootstrapDemoApplication.class, args);
        System.out.println(run);
        //ApplicationContext parent = run.getParent();
        //for (String beanDefinitionName : parent.getBeanDefinitionNames()) {
        //    System.out.println("beanDefinitionName = " + beanDefinitionName);
        //}
    }

}
