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

import org.springframework.util.ClassUtils;

/**
 * An enumeration of possible types of web application.
 *
 * @author Andy Wilkinson
 * @author Brian Clozel
 * @since 2.0.0
 */
public enum WebApplicationType/* 网络应用程序类型 */ {

	/**
	 * 当前程序不是web程序，不会启动内嵌的web服务器
	 *
	 * The application should not run as a web application and should not start an
	 * embedded web server.
	 *
	 * 该应用程序不应作为Web应用程序运行，也不应启动嵌入式Web服务器。
	 */
	NONE,

	/**
	 * 当前程序是servlet web程序，会启动内嵌的servlet web服务器
	 *
	 * The application should run as a servlet-based web application and should start an
	 * embedded servlet web server.
	 *
	 * 该应用程序应作为基于servlet的Web应用程序运行，并应启动嵌入式servlet Web服务器。
	 */
	SERVLET,

	/**
	 * 当前程序是响应式web程序，会启动内嵌的响应式web服务器
	 *
	 * The application should run as a reactive web application and should start an
	 * embedded reactive web server.
	 *
	 * 该应用程序应作为响应式Web应用程序运行，并应启动嵌入式响应式Web服务器。
	 */
	REACTIVE;

	private static final String[] SERVLET_INDICATOR_CLASSES = { "javax.servlet.Servlet",
			"org.springframework.web.context.ConfigurableWebApplicationContext" };

	private static final String WEBMVC_INDICATOR_CLASS = "org.springframework.web.servlet.DispatcherServlet";

	private static final String WEBFLUX_INDICATOR_CLASS = "org.springframework.web.reactive.DispatcherHandler";

	private static final String JERSEY_INDICATOR_CLASS = "org.glassfish.jersey.servlet.ServletContainer";

	private static final String SERVLET_APPLICATION_CONTEXT_CLASS = "org.springframework.web.context.WebApplicationContext";

	private static final String REACTIVE_APPLICATION_CONTEXT_CLASS = "org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext";

	/**
	 * 判断项目中是否存在"一些类的全限定类名"所对应的类，推导出当前Web项目的类型：1、servlet web项目 / 2、reactive web项目 / 3、不是一个web项目
	 */
	static WebApplicationType/* 网络应用程序类型 */ deduceFromClasspath() {
		/* 1、存在DispatcherHandler，并且不存在DispatcherServlet，并且不存在ServletContainer，则代表：当前程序是响应式web程序，会启动内嵌的响应式web服务器 */
		if (ClassUtils.isPresent(WEBFLUX_INDICATOR_CLASS/* org.springframework.web.reactive.DispatcherHandler */, null)
				&& !ClassUtils.isPresent(WEBMVC_INDICATOR_CLASS/* org.springframework.web.servlet.DispatcherServlet */, null)
				&& !ClassUtils.isPresent(JERSEY_INDICATOR_CLASS/* org.glassfish.jersey.servlet.ServletContainer */, null)) {
			// 当前程序是响应式web程序，会启动内嵌的响应式web服务器
			return WebApplicationType.REACTIVE;
		}

		/* 2、如果不存在Servlet，或者不存在ConfigurableWebApplicationContext，则代表：当前程序不是web程序，不会启动内嵌的web服务器 */
		for (String className : SERVLET_INDICATOR_CLASSES/* javax.servlet.Servlet,org.springframework.web.context.ConfigurableWebApplicationContext */) {
			if (!ClassUtils.isPresent(className, null)) {
				// 当前程序不是web程序，不会启动内嵌的web服务器
				return WebApplicationType.NONE;
			}
		}

		/* 3、以上都不成立，则代表：当前程序是servlet web程序，会启动内嵌的servlet web服务器 */
		return WebApplicationType.SERVLET;
	}

	static WebApplicationType deduceFromApplicationContext(Class<?> applicationContextClass) {
		if (isAssignable(SERVLET_APPLICATION_CONTEXT_CLASS, applicationContextClass)) {
			return WebApplicationType.SERVLET;
		}
		if (isAssignable(REACTIVE_APPLICATION_CONTEXT_CLASS, applicationContextClass)) {
			return WebApplicationType.REACTIVE;
		}
		return WebApplicationType.NONE;
	}

	private static boolean isAssignable(String target, Class<?> type) {
		try {
			return ClassUtils.resolveClassName(target, null).isAssignableFrom(type);
		}
		catch (Throwable ex) {
			return false;
		}
	}

}
