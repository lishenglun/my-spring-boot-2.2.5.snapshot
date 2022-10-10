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

package org.springframework.boot.autoconfigure.condition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * Abstract base class for a {@link SpringBootCondition} that also implements
 * {@link AutoConfigurationImportFilter}.
 *
 * 还实现了 {@link AutoConfigurationImportFilter} 的 {@link SpringBootCondition} 的抽象基类。
 *
 * @author Phillip Webb
 */
abstract class FilteringSpringBootCondition extends SpringBootCondition
		implements AutoConfigurationImportFilter, BeanFactoryAware, BeanClassLoaderAware {

	private BeanFactory beanFactory;

	private ClassLoader beanClassLoader;

	/**
	 * 判断配置类是否匹配，返回是否匹配的布尔值结果
	 *
	 * 题外：默认只有spring-boot-autoconfigure模块下，有3个AutoConfigurationImportFilter
	 * {@link org.springframework.boot.autoconfigure.condition.OnBeanCondition}
	 * {@link org.springframework.boot.autoconfigure.condition.OnClassCondition}
	 * {@link org.springframework.boot.autoconfigure.condition.OnWebApplicationCondition}
	 *
	 * @param autoConfigurationClasses 		从spring.factories中获取到的配置类
	 * @param autoConfigurationMetadata 	从spring-autoconfigure-metadata.properties中获取到的"配置类被加载的条件"
	 */
	@Override
	public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
		ConditionEvaluationReport/* 条件评估报告 */ report = ConditionEvaluationReport.find(this.beanFactory);

		// ⚠️获取是否匹配的结果
		// 题外：留给子类实现的
		ConditionOutcome[] outcomes = getOutcomes(autoConfigurationClasses, autoConfigurationMetadata);

		// 当前配置类是否匹配的布尔值
		boolean[] match = new boolean[outcomes.length];

		for (int i = 0; i < outcomes.length; i++) {
			/* 得出当前配置类是否匹配的布尔值 */
			match[i] = (outcomes[i] == null/* 代表匹配 */ || outcomes[i].isMatch()/* 是否匹配 */);

			/* "不匹配 && 存在ConditionOutcome"，则记录一下日志 */
			if (!match[i] && outcomes[i] != null) {
				logOutcome/* 记录结果 */(autoConfigurationClasses[i], outcomes[i]);
				if (report != null) {
					report.recordConditionEvaluation/* 记录条件评估 */(autoConfigurationClasses[i], this, outcomes[i]);
				}
			}
		}

		return match;
	}

	protected abstract ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata);

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	protected final BeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	protected final ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	/**
	 * 过滤出满足条件的全限定类名
	 *
	 * @param classNames
	 * @param classNameFilter
	 * @param classLoader
	 * @return
	 */
	protected final List<String> filter(Collection<String> classNames, ClassNameFilter classNameFilter,
			ClassLoader classLoader) {

		if (CollectionUtils.isEmpty(classNames)) {
			return Collections.emptyList();
		}

		// 存放满足过滤条件的全限定类名
		List<String> matches = new ArrayList<>(classNames.size());
		for (String candidate : classNames) {
			// 过滤。如果条件满足，就放入matches集合中。
			if (classNameFilter.matches(candidate, classLoader)) {
				matches.add(candidate);
			}
		}

		return matches;
	}

	/**
	 * 获取全限定类名对应的Class对象
	 *
	 * Slightly faster variant of {@link ClassUtils#forName(String, ClassLoader)} that
	 * doesn't deal with primitives, arrays or inner types.
	 *
	 * @param className the class name to resolve
	 *                  全限定类名
	 *
	 * @param classLoader the class loader to use
	 *                  类加载器
	 *
	 * @return a resolved class
	 * @throws ClassNotFoundException if the class cannot be found
	 */
	protected static Class<?> resolve(String className, ClassLoader classLoader) throws ClassNotFoundException {
		// 1、存在类加载器，就用类加载器进行加载
		if (classLoader != null) {
			return classLoader.loadClass(className);
		}

		// 2、否则，用Class.forName()，也就是采用默认加载器进行加载
		return Class.forName(className);
	}

	protected enum ClassNameFilter {

		// 判断className是否存在：1、存在的话，返回true；2、不存在的话，返回false
		PRESENT {

			@Override
			public boolean matches(String className, ClassLoader classLoader) {
				return isPresent(className, classLoader);
			}

		},

		// 判断className是否不存在：1、存在的话，返回false；2、不存在的话，返回true
		MISSING {

			@Override
			public boolean matches(String className, ClassLoader classLoader) {
				return !isPresent(className, classLoader);
			}

		};

		// 抽象方法
		abstract boolean matches(String className, ClassLoader classLoader);

		/**
		 * 加载className对应的类
		 * （1）如果加载成功，代表存在className对应的类，则返回true
		 * （2）如果加载失败，代表不存在className对应的类，则返回false
		 *
		 * @param className			类的全限定类名
		 * @param classLoader		类加载器
		 * @return
		 */
		static boolean isPresent(String className, ClassLoader classLoader) {
			if (classLoader == null) {
				// 获取默认的类加载器
				classLoader = ClassUtils.getDefaultClassLoader();
			}

			try {
				// 加载className对应的类
				resolve(className, classLoader);
				// 如果加载成功，则返回true，代表存在className对应的类
				return true;
			}
			catch (Throwable ex) {
				// 如果加载失败，则返回false
				return false;
			}
		}

	}

}
