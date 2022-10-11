/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ReflectionUtils;

/**
 * 存储SpringApplicationRunListener集合，相当于一个广播器
 *
 * 题外：SpringApplicationRunListeners里面是对spring boot的监听器做了一个包装，本质上是一个spring boot体系内的"广播器/事件发布器"，用来发布事件时，触发spring boot体系内的监听器，也就是触发SpringApplicationRunListener
 *
 * A collection of {@link SpringApplicationRunListener}. —— {@link SpringApplicationRunListener} 的集合。
 *
 * @author Phillip Webb
 */
class SpringApplicationRunListeners {

	private final Log log;

	/**
	 * 默认从spring.factories文件中，获取到的SpringApplicationRunListener只有{@link org.springframework.boot.context.event.EventPublishingRunListener}这1个，
	 * 里面获取了事件广播器，以及所有的ApplicationListener
	 */
	// spring boot的监听器集合
	// 题外：SpringApplicationRunListeners相当于一个广播器，所以广播器里面具备所有监听器的实例
	private final List<SpringApplicationRunListener> listeners;

	/**
	 * @param log
	 * @param listeners			spring.factories文件中所有的SpringApplicationRunListener类型的对象
	 */
	SpringApplicationRunListeners(Log log, Collection<? extends SpringApplicationRunListener> listeners) {
		this.log = log;
		this.listeners = new ArrayList<>(listeners);
	}

	/**
	 * 发布启动事件
	 */
	void starting() {
		// 调用内部发所有的监听器
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.starting();
		}
	}

	void environmentPrepared(ConfigurableEnvironment environment) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.environmentPrepared(environment);
		}
	}

	void contextPrepared(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.contextPrepared(context);
		}
	}

	void contextLoaded(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.contextLoaded(context);
		}
	}

	void started(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.started(context);
		}
	}

	void running(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.running(context);
		}
	}

	void failed(ConfigurableApplicationContext context, Throwable exception) {
		for (SpringApplicationRunListener listener : this.listeners) {
			callFailedListener(listener, context, exception);
		}
	}

	private void callFailedListener(SpringApplicationRunListener listener, ConfigurableApplicationContext context,
			Throwable exception) {
		try {
			listener.failed(context, exception);
		}
		catch (Throwable ex) {
			if (exception == null) {
				ReflectionUtils.rethrowRuntimeException(ex);
			}
			if (this.log.isDebugEnabled()) {
				this.log.error("Error handling failed", ex);
			}
			else {
				String message = ex.getMessage();
				message = (message != null) ? message : "no error message";
				this.log.warn("Error handling failed (" + message + ")");
			}
		}
	}

}
