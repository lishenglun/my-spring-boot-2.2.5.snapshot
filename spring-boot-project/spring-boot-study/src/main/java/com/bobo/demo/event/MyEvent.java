package com.bobo.demo.event;

import org.springframework.context.ApplicationEvent;

/**
 * 自定义的事件类型 --- 它就是一个标志，所以里面可以不用去写特定的逻辑
 *
 * 题外：这个自定义的事件，不局限于服务的启动，而是在特定的场景下面触发
 *
 * 脱离原来的，原来提供的是系统启动中的一些事件，而现在是一个自定义事件，自定义事件要在不同的时间点触发
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
