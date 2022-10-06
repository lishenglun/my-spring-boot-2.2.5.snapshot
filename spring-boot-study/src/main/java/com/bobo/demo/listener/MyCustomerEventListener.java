package com.bobo.demo.listener;

import com.bobo.demo.event.MyEvent;
import org.springframework.context.ApplicationListener;

/**
 * 自定义监听器
 *    监听自定义的事件
 */
public class MyCustomerEventListener implements ApplicationListener<MyEvent> {
	@Override
	public void onApplicationEvent(MyEvent event) {
		System.out.println("MyCustomerEventListener ---》自定义事件触发" + event);
		// 触发对应的事件后 业务处理
		new Thread(()->{
			// 业务....
		}).start();
	}
}
