package com.bobo.demo.listener;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

/**
 * 自定义的监听器
 * 1.必须实现 ApplicationListener接口
 *    监听器监听的事件类型 泛型中如果制定的是 ApplicationEvent 表示监听所有的事件
 *
 * 2.需要把自定义的监听器添加到spring.factories文件中
 */
public class MySpringApplicationListener
		implements ApplicationListener<ApplicationEvent> {
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		System.out.println("自定义监听器--->" + event);

	}
}
