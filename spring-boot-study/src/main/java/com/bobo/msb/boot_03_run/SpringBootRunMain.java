package com.bobo.msb.boot_03_run;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 *
 * 一、SpringBoot初始化"核心流程"源码分析 —— 也就是SpringApplication.run()的整体的核心流程分析
 *
 * 1、首先new SpringApplication()。
 * 在new SpringApplication()里面：
 * （1）记录run()传入的配置类
 * （2）根据判断当前项目中是否存在一些"全限定类名"对应的类，推导出当前Web项目的类型。一共有3个：：servlet web项目 / reactive web项目 / 不是一个web项目
 * （3）初始化spring.factories文件中配置的的"初始化器" —— ApplicationContextInitializer
 * （4）初始化spring.factories文件中配置的spring体系的"监听器" —— ApplicationListener
 * （5）通过堆栈信息反推main方法所在的Class对象
 * 具体做法：挨个比对堆栈的方法名称是不是main，是的话就证明找到了main方法了，然后获取main方法所在的Class
 *
 * 2、然后调用SpringApplication#run()：
 * （1）创建一个计时器，记录系统启动的时长
 * （2）创建一个spring boot的广播器(SpringApplicationRunListeners)，里面初始化了spring.factories文件中spring boot体系的监听器(SpringApplicationRunListener)
 * 题外：默认只有一个spring boot的监听器：EventPublishingRunListener。在创建EventPublishingRunListener对象的时候，里面创建了spring的广播器(SimpleApplicationEventMulticaster)，以及获取了所有的spring监听器(new SpringApplication()时初始化的)，注入到spring广播器当中
 * （3）创建一个"应用程序参数"的持有对象，持有应用程序参数
 * （4）准备环境信息：(1)创建环境对象，加载系统环境参数，(2)发布环境准备事件
 * （5）打印Banner信息（Banner信息也就是Spring图标）
 * （6）根据当前web项目的类型，创建对应的应用上下文（Spring容器对象）
 * （7）获取spring.factories文件中的异常报告器 —— SpringBootExceptionReporter
 * （8）在刷新容器前，准备应用上下文
 * 注意：⚠️️里面注册了SpringApplication.run(class...)中的Class bd
 * （9）⚠️刷新应用上下文，也就是完成Spring容器的初始化
 * （10）刷新容器后做的一些操作（留给用户扩展实现）
 * （11）调用Runner执行器，也就是：执行容器中的ApplicationRunner#run()、CommandLineRunner#run()
 * （12）在整个SpringApplication#run()过程中会发布一些事件
 *
 * @date 2022/10/6 16:14
 */
@SpringBootApplication
public class SpringBootRunMain {

	public static void main(String[] args) {
		SpringApplication.run(SpringBootRunMain.class, args);
	}

}