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

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * {@link Condition} and {@link AutoConfigurationImportFilter} that checks for the
 * presence or absence of specific classes.
 *
 * @author Phillip Webb
 * @see ConditionalOnClass
 * @see ConditionalOnMissingClass
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class OnClassCondition extends FilteringSpringBootCondition {

	@Override
	protected final ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		// Split the work and perform half in a background thread if more than one
		// processor is available. Using a single additional thread seems to offer the
		// best performance. More threads make things worse.
		// 上面的翻译：如果有多个处理器可用，则拆分工作并在后台线程中执行一半。使用单个附加线程似乎可以提供最佳性能。更多线程会使事情变得更糟。

		/**
		 * 1、Runtime.getRuntime().availableProcessors()：
		 * 返回jvm虚拟机可用核心数。这个值有可能在虚拟机的特定调用期间更改。
		 * （1）JVM可用核心数
		 * JVM可以用来工作利用的CPU核心数。在一个多核CPU服务器上，可能安装了多个应用，JVM只是其中的一个部分，有些cpu被其他应用使用了。
		 * （2）为何返回值可变？它是如何工作的？
		 * 返回值可变这个也比较好理解，既然多核CPU服务器上多个应用公用cpu，对于不同时刻来讲可以被JVM利用的数量当然是不同的
		 * 参考：https://blog.csdn.net/wanghao112956/article/details/100878026
		 */
		// 如果jvm可以用来工作利用的cpu核心数量大于1，则多开启一个线程去执行匹配操作
		if (Runtime.getRuntime().availableProcessors/* 可用处理器 */() > 1) {
			return resolveOutcomesThreaded/* 解析结果线程 */(autoConfigurationClasses, autoConfigurationMetadata);
		}
		// 如果jvm可以用来工作利用的cpu核心数量小于1，则只用当前线程执行匹配操作
		else {
			OutcomesResolver outcomesResolver = new StandardOutcomesResolver(autoConfigurationClasses, 0,
					autoConfigurationClasses.length, autoConfigurationMetadata, getBeanClassLoader());
			return outcomesResolver.resolveOutcomes();
		}
	}

	private ConditionOutcome[] resolveOutcomesThreaded(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		int split = autoConfigurationClasses.length / 2;

		// 开启一个新的线程，执行StandardOutcomesResolver#resolveOutcomes()
		OutcomesResolver firstHalfResolver = createOutcomesResolver(autoConfigurationClasses, 0, split,
				autoConfigurationMetadata);

		// 当前线程执行StandardOutcomesResolver#resolveOutcomes()
		OutcomesResolver secondHalfResolver = new StandardOutcomesResolver(autoConfigurationClasses, split,
				autoConfigurationClasses.length, autoConfigurationMetadata, getBeanClassLoader());
		ConditionOutcome[] secondHalf = secondHalfResolver.resolveOutcomes();

		// 获取上面开启的新线程执行StandardOutcomesResolver#resolveOutcomes()的结果
		ConditionOutcome[] firstHalf = firstHalfResolver.resolveOutcomes();

		// 创建一个新的用来存放条件结果的数组
		ConditionOutcome[] outcomes = new ConditionOutcome[autoConfigurationClasses.length];
		System.arraycopy(firstHalf, 0, outcomes, 0, firstHalf.length);
		System.arraycopy(secondHalf, 0, outcomes, split, secondHalf.length);

		return outcomes;
	}

	private OutcomesResolver createOutcomesResolver(String[] autoConfigurationClasses, int start, int end,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		OutcomesResolver outcomesResolver = new StandardOutcomesResolver/* 标准结果解析器 */(autoConfigurationClasses, start, end,
				autoConfigurationMetadata, getBeanClassLoader());
		try {
			// 开启一个新的线程，执行StandardOutcomesResolver#resolveOutcomes()
			return new ThreadedOutcomesResolver(outcomesResolver);
		}
		catch (AccessControlException ex) {
			return outcomesResolver;
		}
	}

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		ClassLoader classLoader = context.getClassLoader();
		ConditionMessage matchMessage = ConditionMessage.empty();
		List<String> onClasses = getCandidates(metadata, ConditionalOnClass.class);
		if (onClasses != null) {
			List<String> missing = filter(onClasses, ClassNameFilter.MISSING, classLoader);
			if (!missing.isEmpty()) {
				return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnClass.class)
						.didNotFind("required class", "required classes").items(Style.QUOTE, missing));
			}
			matchMessage = matchMessage.andCondition(ConditionalOnClass.class)
					.found("required class", "required classes")
					.items(Style.QUOTE, filter(onClasses, ClassNameFilter.PRESENT, classLoader));
		}
		List<String> onMissingClasses = getCandidates(metadata, ConditionalOnMissingClass.class);
		if (onMissingClasses != null) {
			List<String> present = filter(onMissingClasses, ClassNameFilter.PRESENT, classLoader);
			if (!present.isEmpty()) {
				return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnMissingClass.class)
						.found("unwanted class", "unwanted classes").items(Style.QUOTE, present));
			}
			matchMessage = matchMessage.andCondition(ConditionalOnMissingClass.class)
					.didNotFind("unwanted class", "unwanted classes")
					.items(Style.QUOTE, filter(onMissingClasses, ClassNameFilter.MISSING, classLoader));
		}
		return ConditionOutcome.match(matchMessage);
	}

	private List<String> getCandidates(AnnotatedTypeMetadata metadata, Class<?> annotationType) {
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(annotationType.getName(), true);
		if (attributes == null) {
			return null;
		}
		List<String> candidates = new ArrayList<>();
		addAll(candidates, attributes.get("value"));
		addAll(candidates, attributes.get("name"));
		return candidates;
	}

	private void addAll(List<String> list, List<Object> itemsToAdd) {
		if (itemsToAdd != null) {
			for (Object item : itemsToAdd) {
				Collections.addAll(list, (String[]) item);
			}
		}
	}

	/**
	 * 结果解析器
	 */
	private interface OutcomesResolver {

		/**
		 * 解析结果值
		 */
		ConditionOutcome[] resolveOutcomes();

	}

	/**
	 * 用于开启一个新的线程，执行OutcomesResolver#resolveOutcomes()
	 */
	private static final class ThreadedOutcomesResolver/* 线程化结果解析器 */ implements OutcomesResolver {

		// 保存刚刚开启的线程
		private final Thread thread;

		// 保存OutcomesResolver#resolveOutcomes()的执行结果
		private volatile ConditionOutcome[] outcomes;

		private ThreadedOutcomesResolver(OutcomesResolver outcomesResolver) {
			this.thread = new Thread(() -> this.outcomes/* 保存执行结果 */ = outcomesResolver.resolveOutcomes());
			this.thread.start();
		}

		@Override
		public ConditionOutcome[] resolveOutcomes() {
			try {
				// 主线程等待子线程的终止。
				// 也就是说主线程的代码块中，如果碰到了t.join()，此时主线程需要等待（阻塞），等待子线程结束了，才能继续执行t.join()之后的代码块！
				this.thread.join();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

			// 返回结果
			return this.outcomes;
		}

	}

	private final class StandardOutcomesResolver/* 标准结果解析器 */ implements OutcomesResolver {

		// spring.factories文件中的配置类
		private final String[] autoConfigurationClasses;

		// autoConfigurationClasses的开始位置
		private final int start;

		// autoConfigurationClasses的结束位置
		private final int end;

		// spring-autoconfigure-metadata.properties文件中的条件类
		private final AutoConfigurationMetadata autoConfigurationMetadata;

		// 类加载器
		private final ClassLoader beanClassLoader;

		private StandardOutcomesResolver(String[] autoConfigurationClasses, int start, int end,
				AutoConfigurationMetadata autoConfigurationMetadata, ClassLoader beanClassLoader) {
			// spring.factories文件中的配置类
			this.autoConfigurationClasses = autoConfigurationClasses;
			// autoConfigurationClasses的开始位置
			this.start = start;
			// autoConfigurationClasses的结束位置
			this.end = end;
			// spring-autoconfigure-metadata.properties文件中的条件类
			this.autoConfigurationMetadata = autoConfigurationMetadata;
			// 类加载器
			this.beanClassLoader = beanClassLoader;
		}

		@Override
		public ConditionOutcome[] resolveOutcomes() {
			return getOutcomes(this.autoConfigurationClasses, this.start, this.end, this.autoConfigurationMetadata);
		}

		private ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses, int start, int end,
				AutoConfigurationMetadata autoConfigurationMetadata) {

			// 存放条件结果
			ConditionOutcome[] outcomes = new ConditionOutcome[end - start];

			for (int i = start; i < end; i++) {
				// 配置类的全限定类名
				String autoConfigurationClass = autoConfigurationClasses[i];
				if (autoConfigurationClass != null) {

					// 会拼接autoConfigurationClass.ConditionalOnClass，然后从autoConfigurationMetadata中获取对应的条件的全限定类名
					// 也就是获取spring-autoconfigure-metadata.properties文件中【配置类.ConditionalOnClass】对应的被加载的条件的全限定类名称
					// 例如：autoConfigurationClass = org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration，会拼接上.ConditionalOnClass，得到：org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration.ConditionalOnClass
					// >>> 然后去获取【org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration.ConditionalOnClass】在spring-autoconfigure-metadata.properties文件中，对应的对应的被加载的条件的全限定类名称
					// >>> 在spring-autoconfigure-metadata.properties文件中，有：org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration.ConditionalOnClass=com.rabbitmq.client.Channel,org.springframework.amqp.rabbit.core.RabbitTemplate
					// >>> 所以可以得到com.rabbitmq.client.Channel,org.springframework.amqp.rabbit.core.RabbitTemplate
					String candidates = autoConfigurationMetadata.get(autoConfigurationClass, "ConditionalOnClass");

					// 存在【配置类.ConditionalOnClass】对应的条件
					if (candidates != null) {
						// 获取是否存在className的结果
						//（1）如果不存在这个className，则构建不匹配的ConditionOutcome返回
						//（2）存在这个className，则返回null
						outcomes[i - start] = getOutcome(candidates);
					}
				}
			}

			return outcomes;
		}

		/**
		 * 获取是否存在className的结果
		 * （1）如果不存在这个className，则构建返回
		 * （2）存在这个className，则返回null
		 *
		 * @param candidates		配置类被加载的条件的全限定类名(可以配置多个，以逗号分割)
		 * 							例如：com.rabbitmq.client.Channel,org.springframework.amqp.rabbit.core.RabbitTemplate
		 */
		private ConditionOutcome getOutcome(String candidates/* className */) {
			try {
				// 如果不包含","
				if (!candidates.contains(",")) {
					return getOutcome(candidates, this.beanClassLoader);
				}

				// 包含","的话，就分割字符串，遍历配置类的ConditionalOnClass
				for (String candidate/* className */ : StringUtils.commaDelimitedListToStringArray/* 逗号分隔列表到字符串数组 */(candidates)) {
					// 只要不存在当前的className，就会返回"不匹配的ConditionOutcome"；否则返回null
					ConditionOutcome outcome = getOutcome(candidate, this.beanClassLoader);
					// ⚠️从这里可以看出，只要配置类的ConditionalOnClass中的一个className不存在，则代表该配置类不满足，
					// 所以"配置类的ConditionalOnClass中所配置的多个className"，是一个 && 的关系！
					if (outcome != null) {
						return outcome;
					}
				}
			}
			catch (Exception ex) {
				// We'll get another chance later
			}
			return null;
		}

		/**
		 * 获取是否存在className的结果
		 * （1）如果不存在这个className，则构建不匹配的ConditionOutcome返回
		 * （2）存在这个className，则返回null
		 */
		private ConditionOutcome getOutcome(String className, ClassLoader classLoader) {
			/* 1、如果不存在这个className，则构建不匹配的ConditionOutcome返回 */
			// 如果不存在这个className
			if (ClassNameFilter.MISSING.matches(className, classLoader)) {

				// 构建条件消息
				ConditionMessage requiredClass = ConditionMessage.forCondition(ConditionalOnClass.class)
						.didNotFind("required class").items(Style.QUOTE, className);

				// 则构建不匹配的ConditionOutcome
				return ConditionOutcome.noMatch(requiredClass);
			}

			/* 2、存在这个className，则返回null */
			return null;
		}

	}

}
