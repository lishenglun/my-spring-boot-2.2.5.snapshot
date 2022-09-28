package com.bobo.demo.event;

import org.springframework.context.ApplicationEvent;

/**
 * 自定义的事件类型 --- 标志
 * 不局限于服务的启动，而是在特定的场景下面触发
 */
public class MyEvent extends ApplicationEvent {
	/**
	 * Create a new {@code ApplicationEvent}.
	 *
	 * @param source the object on which the event initially occurred or with
	 *               which the event is associated (never {@code null})
	 */
	public MyEvent(Object source) {
		super(source);
	}
}
