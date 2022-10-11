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

package org.springframework.boot.context.event;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ErrorHandler;

/**
 * {@link SpringApplicationRunListener} to publish {@link SpringApplicationEvent}s.
 * <p>
 * Uses an internal {@link ApplicationEventMulticaster} for the events that are fired
 * before the context is actually refreshed.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @author Artsiom Yudovin
 * @since 1.0.0
 */
public class EventPublishingRunListener/* 事件发布运行监听器 */ implements SpringApplicationRunListener, Ordered {

	private final SpringApplication application;

	private final String[] args;

	// 广播器
	private final SimpleApplicationEventMulticaster/* 简单应用事件多播 */ initialMulticaster;

	public EventPublishingRunListener(SpringApplication application, String[] args) {
		this.application = application;
		this.args = args;
		/**
		 * 1、疑问：⚠️SpringApplicationRunListeners已经作为一个广播器了，为什么这里还要创建一个广播器？
		 * （1）SpringApplicationRunListener是spring boot的监听器接口，与spring无关，是spring boot的一套监听器体系，
		 * 	而SpringApplicationRunListeners也只是作为spring boot的广播器，广播事件触发的是spring boot体系内的监听器，也就是：SpringApplicationRunListener
		 * （2）而ApplicationListener是spring体系内的监听器，所以需要创建一个spring体系的广播器，去广播事件触发spring体系内的监听器！
		 *
		 * 题外：spring boot和spring它们各自都有一套自己的监听器体系，但是spring boot监听器体系，最终走的是spring的监听器体系
		 * 题外：之所以spring boot监听器体系，可以走spring的监听器体系，是因为：spring boot和spring的监听器所处理的事件都是同一体系，都是spring的事件体系，接口是：ApplicationEvent
		 */
		/* 1、创建spring的广播器(SimpleApplicationEventMulticaster) */
		// 题外：题外：spring在容器中没有自定义的广播器时，spring默认使用的广播器也是SimpleApplicationEventMulticaster
		this.initialMulticaster = new SimpleApplicationEventMulticaster();

		/* 2、获取所有的spring监听器，注入到spring广播器当中 */
		/**
		 * 1、application.getListeners()：获取所有的spring监听器。这些监听器是在new SpringApplication()时进行的初始化。
		 *
		 * 题外：默认springboot项目中的spring.factories文件中有11个监听器
		 * {@link org.springframework.boot.cloud.CloudFoundryVcapEnvironmentPostProcessor}
		 * {@link org.springframework.boot.context.config.ConfigFileApplicationListener}
		 * {@link org.springframework.boot.context.config.AnsiOutputApplicationListener}
		 * {@link org.springframework.boot.context.logging.LoggingApplicationListener}
		 * {@link org.springframework.boot.context.logging.ClasspathLoggingApplicationListener}
		 * {@link org.springframework.boot.autoconfigure.BackgroundPreinitializer}
		 * {@link org.springframework.boot.context.config.DelegatingApplicationListener}
		 * {@link org.springframework.boot.builder.ParentContextCloserApplicationListener}
		 * {@link org.springframework.boot.ClearCachesApplicationListener}
		 * {@link org.springframework.boot.context.FileEncodingApplicationListener}
		 * {@link org.springframework.boot.liquibase.LiquibaseServiceLocatorApplicationListener}
		 */
		for (ApplicationListener<?>/* 应用程序监听器 */ listener : application.getListeners()) {
			// 往广播器里面，注册监听器
			this.initialMulticaster.addApplicationListener(listener);
		}

	}

	@Override
	public int getOrder() {
		return 0;
	}

	/**
	 * 发布应用启动事件，到"监听应用启动事件的监听器"里面去
	 */
	@Override
	public void starting() {
		// System.out.println("EventPublishingRunListener ----> starting ");

		// 发布应用启动事件，到"监听当前事件的监听器"里面去
		// 注意：⚠️里面会获取到"监听当前事件的监听器"，然后广播事件到"监听当前事件的监听器"里面去
		this.initialMulticaster/* 事件广播器 */.multicastEvent(new ApplicationStartingEvent/* 应用启动事件 */(this.application, this.args));
	}

	@Override
	public void environmentPrepared(ConfigurableEnvironment environment) {
		this.initialMulticaster
				.multicastEvent(new ApplicationEnvironmentPreparedEvent(this.application, this.args, environment));
	}

	@Override
	public void contextPrepared(ConfigurableApplicationContext context) {
		this.initialMulticaster
				.multicastEvent(new ApplicationContextInitializedEvent(this.application, this.args, context));
	}

	@Override
	public void contextLoaded(ConfigurableApplicationContext context) {
		for (ApplicationListener<?> listener : this.application.getListeners()) {
			if (listener instanceof ApplicationContextAware) {
				((ApplicationContextAware) listener).setApplicationContext(context);
			}
			context.addApplicationListener(listener);
		}
		this.initialMulticaster.multicastEvent(new ApplicationPreparedEvent(this.application, this.args, context));
	}

	@Override
	public void started(ConfigurableApplicationContext context) {
		context.publishEvent(new ApplicationStartedEvent(this.application, this.args, context));
	}

	@Override
	public void running(ConfigurableApplicationContext context) {
		context.publishEvent(new ApplicationReadyEvent(this.application, this.args, context));
	}

	@Override
	public void failed(ConfigurableApplicationContext context, Throwable exception) {
		ApplicationFailedEvent event = new ApplicationFailedEvent(this.application, this.args, context, exception);
		if (context != null && context.isActive()) {
			// Listeners have been registered to the application context so we should
			// use it at this point if we can
			context.publishEvent(event);
		}
		else {
			// An inactive context may not have a multicaster so we use our multicaster to
			// call all of the context's listeners instead
			if (context instanceof AbstractApplicationContext) {
				for (ApplicationListener<?> listener : ((AbstractApplicationContext) context)
						.getApplicationListeners()) {
					this.initialMulticaster.addApplicationListener(listener);
				}
			}
			this.initialMulticaster.setErrorHandler(new LoggingErrorHandler());
			this.initialMulticaster.multicastEvent(event);
		}
	}

	private static class LoggingErrorHandler implements ErrorHandler {

		private static final Log logger = LogFactory.getLog(EventPublishingRunListener.class);

		@Override
		public void handleError(Throwable throwable) {
			logger.warn("Error calling ApplicationEventListener", throwable);
		}

	}

}
