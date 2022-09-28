package com.bobo.demo.listener;

import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;

public class MySpringApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {
	@Override
	public void onApplicationEvent(ApplicationStartingEvent event) {
		System.out.println("MySpringApplicationStartingListener--------->" + event);
	}
}
