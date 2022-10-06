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

package org.springframework.boot.autoconfigure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@link DeferredImportSelector} to handle {@link EnableAutoConfiguration
 * auto-configuration}. This class can also be subclassed if a custom variant of
 * {@link EnableAutoConfiguration @EnableAutoConfiguration} is needed.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Madhura Bhave
 * @since 1.3.0
 * @see EnableAutoConfiguration
 */
public class AutoConfigurationImportSelector implements DeferredImportSelector, BeanClassLoaderAware,
		ResourceLoaderAware, BeanFactoryAware, EnvironmentAware, Ordered {

	private static final AutoConfigurationEntry EMPTY_ENTRY = new AutoConfigurationEntry();

	private static final String[] NO_IMPORTS = {};

	private static final Log logger = LogFactory.getLog(AutoConfigurationImportSelector.class);

	private static final String PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE = "spring.autoconfigure.exclude";

	private ConfigurableListableBeanFactory beanFactory;

	private Environment environment;

	private ClassLoader beanClassLoader;

	private ResourceLoader resourceLoader;

	@Override
	public String[] selectImports(AnnotationMetadata annotationMetadata) {
		if (!isEnabled(annotationMetadata)) {
			return NO_IMPORTS;
		}

		AutoConfigurationMetadata autoConfigurationMetadata = AutoConfigurationMetadataLoader
				.loadMetadata(this.beanClassLoader);

		AutoConfigurationEntry autoConfigurationEntry = getAutoConfigurationEntry(autoConfigurationMetadata,
				annotationMetadata);

		return StringUtils.toStringArray(autoConfigurationEntry.getConfigurations());
	}

	/**
	 * 获取自动装配的配置类
	 *
	 * 注意：该方法由{@link AutoConfigurationImportSelector.AutoConfigurationGroup#process(AnnotationMetadata, DeferredImportSelector)}调用
	 *
	 * Return the {@link AutoConfigurationEntry} based on the {@link AnnotationMetadata}
	 * of the importing {@link Configuration @Configuration} class.
	 * @param autoConfigurationMetadata the auto-configuration metadata —— 自动配置元数据
	 *
	 *                                  PropertiesAutoConfigurationMetadata，代表(里面包含了)META-INF/spring-autoconfigure-metadata.properties文件中的内容
	 *
	 * @param annotationMetadata the annotation metadata of the configuration class
	 * @return the auto-configurations that should be imported
	 */
	protected AutoConfigurationEntry getAutoConfigurationEntry(AutoConfigurationMetadata autoConfigurationMetadata,
			AnnotationMetadata annotationMetadata) {

		if (!isEnabled(annotationMetadata)) {
			return EMPTY_ENTRY;
		}

		// 获取元注解中的属性
		AnnotationAttributes attributes = getAttributes(annotationMetadata);

		/* 1、加载当前系统下spring.factories文件中声明的配置类(配置类key：org.springframework.boot.autoconfigure.EnableAutoConfiguration) */

		/**
		 * 1、题外：为什么默认的这么多？因为spring boot火了，很多厂商想抱spring boot的大腿，就跟spring boot说，你帮我，把我的配置类在你的默认配置里面加上。
		 * 于是很多厂商，都在spring boot里面默认有配置类了。
		 * 很多厂商在spring boot当中默认有配置类的话，后续我们再去引入这些在spring boot当中有默认配置类的第三方厂商的时候，就不会再添加对应配置类了！
		 * 例如：像kafka，在spring boot里面默认有配置类：KafkaMetricsAutoConfiguration，后续在引入kafka的时候，就不会需要再添加对应配置类了！
		 */
		// 加载META-INF/spring.factories文件中所有的EnableAutoConfiguration实现类的全限定类名
		// 简称：加载当前系统下META-INF/spring.factories文件中声明的配置类（整合相关bean到SpringBoot中去的Java配置类）
		List<String> configurations = getCandidateConfigurations(annotationMetadata, attributes);

		/* 2、去重 */
		configurations = removeDuplicates(configurations);

		/* 3、移除掉显示排除的 */
		// 获取显示排除的配置类的全限定类名（exclusion属性）
		Set<String> exclusions = getExclusions(annotationMetadata, attributes);
		// 检查一下要排除的
		checkExcludedClasses(configurations, exclusions);
		// 移除掉显示排除的
		configurations.removeAll(exclusions);

		/*

		4、过滤掉当前环境下，不需要载入的配置类：
		(1)根据spring.factories文件中的AutoConfigurationImportFilter，
		(2)以及spring-autoconfigure-metadata.properties文件中"配置类被加载的条件"，过滤掉当前环境下用不到的配置类。

		*/
		/**
		 * 1、为什么要过滤呢？
		 *
		 * 当前系统环境下用不到的就过滤掉，从而降低SpringBoot的启动时间
		 *
		 * 题外：原因是很多的@Configuration其实是依托于其他的框架来加载的，如果当前的classpath环境下没有相关联的依赖，则意味着这些类没必要进行加载，
		 * 所以，通过这种条件过滤可以有效的减少@Configuration配置类的数量从而降低SpringBoot的启动时间
		 *
		 * 题外：虽然spring boot当中有很多默认的配置类，但是站在spring boot的角度，在启动的时候，不能都加载
		 * 如果都加载的话，会把spring boot拖垮，会使得spring boot启动速度很慢，以及随着加载的东西越来越多，内存越来越紧张，从而使得spring boot越来越不好用了，
		 * 所以会过滤掉当前环境下用不到的配置类
		 *
		 * 2、过滤规则
		 *
		 * 根据spring.factories文件中的AutoConfigurationImportFilter，以及spring-autoconfigure-metadata.properties文件中"配置类被加载的条件"，过滤掉当前环境下用不到的配置类
		 *
		 * 🌰例如：spring.factories文件中的配置类有：RedisAutoConfiguration
		 * >>> 由于spring.factories文件中存在AutoConfigurationImportFilter有OnClassCondition，
		 * >>> 并且在spring-autoconfigure-metadata.properties文件中存在RedisAutoConfiguration的加载条件，
		 * >>> OnClassCondition会读取到spring-autoconfigure-metadata.properties文件中RedisAutoConfiguration这个配置类被加载的条件：
		 * >>> org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.ConditionalOnClass=org.springframework.data.redis.core.RedisOperations
		 * >>> 从而导致，RedisAutoConfiguration配置类被加载的条件是系统中必须存在org.springframework.data.redis.core.RedisOperations类，才会被加载
		 * >>> 我们可以在自己系统里面建立一个RedisOperations，从而使得RedisAutoConfiguration这个配置类被加载；也可以引入redis-start依赖，同样也可以使得RedisAutoConfiguration这个配置类被加载
		 */
		// 过滤掉当前环境下，不需要载入的配置类，得到当前环境下需要加载解析的配置类
		// 根据spring.factories文件中的AutoConfigurationImportFilter类型对象，以及spring-autoconfigure-metadata.properties文件中"配置类被加载的条件"，过滤掉当前环境下用不到的配置类
		configurations = filter(configurations, autoConfigurationMetadata);

		/*

		5、广播"自动配置导入"事件
		(1)获取spring.factories文件中所有的AutoConfigurationImportListener；
		(2)然后执行所有的AutoConfigurationImportListener#onAutoConfigurationImportEvent()处理AutoConfigurationImportEvent事件

		*/
		fireAutoConfigurationImportEvents/* 触发自动配置导入事件 */(configurations, exclusions);

		/* 6、返回要加载解析的配置类 */
		return new AutoConfigurationEntry(configurations, exclusions);
	}

	@Override
	public Class<? extends Group> getImportGroup() {
		return AutoConfigurationGroup.class;
	}

	protected boolean isEnabled(AnnotationMetadata metadata) {
		if (getClass() == AutoConfigurationImportSelector.class) {
			return getEnvironment().getProperty(EnableAutoConfiguration.ENABLED_OVERRIDE_PROPERTY, Boolean.class, true);
		}
		return true;
	}

	/**
	 * Return the appropriate {@link AnnotationAttributes} from the
	 * {@link AnnotationMetadata}. By default this method will return attributes for
	 * {@link #getAnnotationClass()}.
	 * @param metadata the annotation metadata
	 * @return annotation attributes
	 */
	protected AnnotationAttributes getAttributes(AnnotationMetadata metadata) {
		String name = getAnnotationClass().getName();
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(name, true));
		Assert.notNull(attributes, () -> "No auto-configuration attributes found. Is " + metadata.getClassName()
				+ " annotated with " + ClassUtils.getShortName(name) + "?");
		return attributes;
	}

	/**
	 * Return the source annotation class used by the selector.
	 * @return the annotation class
	 */
	protected Class<?> getAnnotationClass() {
		return EnableAutoConfiguration.class;
	}

	/**
	 * 加载META-INF/spring.factories文件中所有的EnableAutoConfiguration实现类的全限定类名
	 *
	 * Return the auto-configuration class names that should be considered. By default
	 * this method will load candidates using {@link SpringFactoriesLoader} with
	 * {@link #getSpringFactoriesLoaderFactoryClass()}.
	 * @param metadata the source metadata
	 * @param attributes the {@link #getAttributes(AnnotationMetadata) annotation
	 * attributes}
	 * @return a list of candidate configurations
	 */
	protected List<String> getCandidateConfigurations(AnnotationMetadata metadata, AnnotationAttributes attributes) {
		// 加载META-INF/spring.factories文件中key=org.springframework.boot.autoconfigure.EnableAutoConfiguration的value值
		// 简称：加载META-INF/spring.factories文件中所有的EnableAutoConfiguration实现类的全限定类名
		List<String> configurations = SpringFactoriesLoader.loadFactoryNames(
				getSpringFactoriesLoaderFactoryClass()/* EnableAutoConfiguration.class */,
				getBeanClassLoader());

		Assert.notEmpty(configurations, "No auto configuration classes found in META-INF/spring.factories. If you "
				+ "are using a custom packaging, make sure that file is correct.");

		return configurations;
	}

	/**
	 * Return the class used by {@link SpringFactoriesLoader} to load configuration
	 * candidates.
	 * @return the factory class
	 */
	protected Class<?> getSpringFactoriesLoaderFactoryClass() {
		return EnableAutoConfiguration.class;
	}

	private void checkExcludedClasses(List<String> configurations, Set<String> exclusions) {
		List<String> invalidExcludes = new ArrayList<>(exclusions.size());
		for (String exclusion : exclusions) {
			if (ClassUtils.isPresent(exclusion, getClass().getClassLoader()) && !configurations.contains(exclusion)) {
				invalidExcludes.add(exclusion);
			}
		}
		if (!invalidExcludes.isEmpty()) {
			handleInvalidExcludes(invalidExcludes);
		}
	}

	/**
	 * Handle any invalid excludes that have been specified.
	 * @param invalidExcludes the list of invalid excludes (will always have at least one
	 * element)
	 */
	protected void handleInvalidExcludes(List<String> invalidExcludes) {
		StringBuilder message = new StringBuilder();
		for (String exclude : invalidExcludes) {
			message.append("\t- ").append(exclude).append(String.format("%n"));
		}
		throw new IllegalStateException(String.format(
				"The following classes could not be excluded because they are not auto-configuration classes:%n%s",
				message));
	}

	/**
	 * Return any exclusions that limit the candidate configurations.
	 * @param metadata the source metadata
	 * @param attributes the {@link #getAttributes(AnnotationMetadata) annotation
	 * attributes}
	 * @return exclusions or an empty set
	 */
	protected Set<String> getExclusions(AnnotationMetadata metadata, AnnotationAttributes attributes) {
		Set<String> excluded = new LinkedHashSet<>();
		excluded.addAll(asList(attributes, "exclude"));
		excluded.addAll(Arrays.asList(attributes.getStringArray("excludeName")));
		excluded.addAll(getExcludeAutoConfigurationsProperty());
		return excluded;
	}

	private List<String> getExcludeAutoConfigurationsProperty() {
		if (getEnvironment() instanceof ConfigurableEnvironment) {
			Binder binder = Binder.get(getEnvironment());
			return binder.bind(PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE, String[].class).map(Arrays::asList)
					.orElse(Collections.emptyList());
		}
		String[] excludes = getEnvironment().getProperty(PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE, String[].class);
		return (excludes != null) ? Arrays.asList(excludes) : Collections.emptyList();
	}

	/**
	 * 过滤掉当前环境下用不到的配置类，得到当前环境下需要加载解析的配置类
	 *
	 * 1、涉及的东西：通过spring.factories文件中所有的AutoConfigurationImportFilter类型对象，以及spring-autoconfigure-metadata.properties文件中的配置类被加载条件，
	 * 过滤掉当前环境下用不到的配置类，得到当前环境下需要加载解析的配置类
	 *
	 * 2、具体操作：获取spring.factories文件中所有的AutoConfigurationImportFilter类型对象，然后遍历AutoConfigurationImportFilter，
	 * 根据每一个AutoConfigurationImportFilter，去获取配置类在spring-autoconfigure-metadata.properties文件中特定的加载条件，
	 * 然后检查其条件是否满足，从而过滤掉当前环境下用不到的配置类
	 *
	 * 例如：OnClassCondition，就是获取spring-autoconfigure-metadata.properties文件中"配置类.ConditionalOnClass"所对应的加载条件，然后判断当前羡慕下是否存在"配置类.ConditionalOnClass"所对应的全限定类名的类
	 * 例如：OnBeanCondition，就是获取spring-autoconfigure-metadata.properties文件中"配置类.ConditionalOnBean"所对应的加载条件；然后判断当前羡慕下是否存在"配置类.ConditionalOnBean"所对应的全限定类名的类
	 *
	 * @param configurations				spring.factories文件中所有的自动装配配置类的全限定名（EnableAutoConfiguration类型的实现类的全限定名）
	 * @param autoConfigurationMetadata		PropertiesAutoConfigurationMetadata，代表(里面包含了)META-INF/spring-autoconfigure-metadata.properties文件中的内容
	 */
	private List<String> filter(List<String> configurations, AutoConfigurationMetadata autoConfigurationMetadata) {
		long startTime = System.nanoTime();

		String[] candidates = StringUtils.toStringArray(configurations);

		boolean[] skip = new boolean[candidates.length];
		// 是否存在要跳过的：false代表不存在要跳过的；true：代表存在要跳过的
		// 这个标识的作用是为了节约性能，如果不存在要跳过的，那么就直接返回从spring.factories文件中获取到的配置类；而不用去遍历每个配置类，逐一判断其是否需要跳过，因为再次遍历会很浪费性能！
		boolean skipped = false;

		/*

		1、遍历spring.factories文件中所有的AutoConfigurationImportFilter，
		根据每一个AutoConfigurationImportFilter，去spring-autoconfigure-metadata.properties文件中，获取配置类所对应的加载条件，
		然后检查其条件是否满足，从而过滤掉当前环境下用不到的配置类

		注意：⚠️只要其中一个AutoConfigurationImportFilter得出某个配置类不满足加载条件，那么这个配置类就不满足加载条件，
		即使在其它AutoConfigurationImportFilter中，这个配置类，满足其对应的加载条件也不行

		*/

		/**
		 * 1、getAutoConfigurationImportFilters()：获取spring.factories文件中所有的AutoConfigurationImportFilter类型对象
		 *
		 * （1）默认只有spring-boot-autoconfigure模块下，有3个AutoConfigurationImportFilter
		 * {@link org.springframework.boot.autoconfigure.condition.OnBeanCondition}
		 * {@link org.springframework.boot.autoconfigure.condition.OnClassCondition}
		 * {@link org.springframework.boot.autoconfigure.condition.OnWebApplicationCondition}
		 *
		 * 注意：在当前，过滤配置类这方面，OnBeanCondition与OnClassCondition的效果差不多，都是看其对应的全限定类名的类是否存在当前系统中，都存在就代表匹配，只要其中有一个不存在，就代表不匹配
		 *
		 * 注意：虽然@ConditionalOnBean功能的实现者(条件对象)是OnBeanCondition，但是在当前，过滤配置类这方面，OnBeanCondition与@ConditionalOnBean的作用点和效果是完全不一样的，是两个独立的功能！
		 * >>> 在当前，过滤配置类这方面，OnBeanCondition其的作用是：
		 * >>> 从spring-autoconfigure-metadata.properties文件中，获取【配置类.ConditionalOnBean】对应的全限定类名，然后判断这些全限定类名所对应的类，是否存在于系统当中，来决定当前配置类是否应该被过滤；
		 * >>> 而@ConditionalOnBean的作用是在解析配置类的时候，根据@ConditionalOnBean中的内容，判断是否应该跳过配置类的解析。
		 * >>>
		 * >>> 同理虽然@ConditionalOnClass功能的实现者(条件对象)是OnClassCondition，但是在当前，过滤配置类这方面，OnClassCondition与@ConditionalOnClass的作用点和效果是完全不一样的，是两个独立的功能！
		 */
		// 遍历spring.factories文件中所有的AutoConfigurationImportFilter类型对象
		for (AutoConfigurationImportFilter filter : getAutoConfigurationImportFilters()) {

			// 处理filter实现的Aware接口：如果filter实现了一些Aware接口，则设置对应的属性值
			invokeAwareMethods/* 调用感知方法 */(filter);

			// 通过AutoConfigurationImportFilter，获取spring-autoconfigure-metadata.properties文件中配置类被加载的条件，
			// 然后检查其条件是否满足，从而得出当前配置类是否需要被加载解析
			// 注意：⚠️【boolean[] match数组】与【String[] candidates数组】一一对应
			boolean[] match = /* ⚠️ */filter.match(candidates, autoConfigurationMetadata);
			for (int i = 0; i < match.length; i++) {
				// 检查当前配置类的加载条件，是否匹配
				if (!match[i]) {
					/* 不匹配 */
					// 注意：⚠️只要其中一个AutoConfigurationImportFilter得出某个配置类不满足加载条件，那么这个配置类就不满足加载条件，
					// 即使在其它AutoConfigurationImportFilter中，这个配置类，满足其对应的加载条件也不行
					skip[i] = true;
					candidates[i] = null;
					skipped = true;
				}
			}
		}

		/* 2、如果"不存在要过滤的配置类"，那么直接返回从spring.factories文件中获取到的所有配置类 */
		// 如果skipped=false，也就是"不存在要跳过的"，那么直接返回从spring.factories文件中获取到的所有配置类
		if (!skipped) {
			return configurations;
		}

		/* 3、如果"存在要过滤的配置类"，那么就筛选出不要过滤的配置类进行返回 */

		// 如果skipped=true，也就是"存在要跳过的"，那么就去遍历每个配置类，逐一判断其是否需要跳过，
		// 如果要跳过就不添加到result集合当中；只有不要跳过的，才添加到result集合当中
		List<String> result = new ArrayList<>(candidates.length);
		for (int i = 0; i < candidates.length; i++) {
			// 不要跳过，就添加到result中
			// 也就是说，是匹配的，于是就添加到result中
			if (!skip[i]) {
				result.add(candidates[i]);
			}
		}

		if (logger.isTraceEnabled()) {
			int numberFiltered = configurations.size() - result.size();
			logger.trace("Filtered " + numberFiltered + " auto configuration class in "
					+ TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + " ms");
		}

		return new ArrayList<>(result);
	}


	/**
	 * 1、获取spring.factories文件中所有的AutoConfigurationImportFilter类型对象
	 *
	 * （1）默认只有spring-boot-autoconfigure模块下，有3个AutoConfigurationImportFilter
	 * {@link org.springframework.boot.autoconfigure.condition.OnBeanCondition}
	 * {@link org.springframework.boot.autoconfigure.condition.OnClassCondition}
	 * {@link org.springframework.boot.autoconfigure.condition.OnWebApplicationCondition}
	 *
	 * @return	spring.factories文件中所有的AutoConfigurationImportFilter类型对象
	 */
	protected List<AutoConfigurationImportFilter> getAutoConfigurationImportFilters() {
		return SpringFactoriesLoader.loadFactories(AutoConfigurationImportFilter.class, this.beanClassLoader);
	}

	protected final <T> List<T> removeDuplicates(List<T> list) {
		return new ArrayList<>(new LinkedHashSet<>(list));
	}

	protected final List<String> asList(AnnotationAttributes attributes, String name) {
		String[] value = attributes.getStringArray(name);
		return Arrays.asList(value);
	}

	/**
	 * 广播"自动配置导入"事件：
	 * (1)获取spring.factories文件中所有的AutoConfigurationImportListener；
	 * (2)然后执行所有的AutoConfigurationImportListener#onAutoConfigurationImportEvent()处理AutoConfigurationImportEvent事件
	 *
	 * @param configurations			过滤完毕的，最终要导入的配置类
	 * @param exclusions				显示排除的配置类
	 */
	private void fireAutoConfigurationImportEvents(List<String> configurations, Set<String> exclusions) {
		/* 1、从spring.factories文件中获取所有的AutoConfigurationImportListener */
		List<AutoConfigurationImportListener> listeners = getAutoConfigurationImportListeners();

		// 存在AutoConfigurationImportListener
		if (!listeners.isEmpty()) {
			/* 2、创建AutoConfigurationImportEvent("自动配置导入")事件 */
			// "自动配置导入"事件
			AutoConfigurationImportEvent event = new AutoConfigurationImportEvent(this, configurations, exclusions);

			/* 3、执行所有的AutoConfigurationImportListener#onAutoConfigurationImportEvent()处理AutoConfigurationImportEvent事件 */
			for (AutoConfigurationImportListener listener : listeners) {
				// 如果AutoConfigurationImportListener实现了一些Aware接口，则设置对应的属性值。
				invokeAwareMethods(listener);
				// 执行AutoConfigurationImportListener#onAutoConfigurationImportEvent()处理"自动配置导入"事件
				listener.onAutoConfigurationImportEvent(event);
			}
		}
	}

	/**
	 * 从spring.factories文件中获取所有的AutoConfigurationImportListener
	 */
	protected List<AutoConfigurationImportListener> getAutoConfigurationImportListeners() {
		return SpringFactoriesLoader.loadFactories(AutoConfigurationImportListener.class, this.beanClassLoader);
	}

	/**
	 * 如果instance实现了一些Aware接口，则设置对应的属性值。
	 *
	 * @param instance
	 */
	private void invokeAwareMethods(Object instance) {
		if (instance instanceof Aware) {
			if (instance instanceof BeanClassLoaderAware) {
				((BeanClassLoaderAware) instance).setBeanClassLoader(this.beanClassLoader);
			}
			if (instance instanceof BeanFactoryAware) {
				((BeanFactoryAware) instance).setBeanFactory(this.beanFactory);
			}
			if (instance instanceof EnvironmentAware) {
				((EnvironmentAware) instance).setEnvironment(this.environment);
			}
			if (instance instanceof ResourceLoaderAware) {
				((ResourceLoaderAware) instance).setResourceLoader(this.resourceLoader);
			}
		}
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		Assert.isInstanceOf(ConfigurableListableBeanFactory.class, beanFactory);
		this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
	}

	protected final ConfigurableListableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	protected ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	protected final Environment getEnvironment() {
		return this.environment;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	protected final ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 1;
	}

	private static class AutoConfigurationGroup
			implements DeferredImportSelector.Group, BeanClassLoaderAware, BeanFactoryAware, ResourceLoaderAware {

		// key：最终要加载的配置类的全限定类名
		// value：标注@Import(AutoConfigurationImportSelector.class)类的注解元数据
		// >>> 由于我们是标注了@SpringBootApplication，在@SpringBootApplication里面标注了@EnableAutoConfiguration，
		// >>> 在@EnableAutoConfiguration里面标注了@Import(AutoConfigurationImportSelector.class)，
		// >>> 所以最终标注@Import(AutoConfigurationImportSelector.class)的类是@SpringBootApplication所修饰的类的注解元数据
		private final Map<String, AnnotationMetadata> entries = new LinkedHashMap<>();

		// 最终要加载的配置类
		// 题外：虽然是个集合，不过一般只有一个AutoConfigurationEntry
		private final List<AutoConfigurationEntry> autoConfigurationEntries = new ArrayList<>();

		private ClassLoader beanClassLoader;

		private BeanFactory beanFactory;

		private ResourceLoader resourceLoader;

		// PropertiesAutoConfigurationMetadata：⚠️代表(里面包含了)META-INF/spring-autoconfigure-metadata.properties文件中的内容
		private AutoConfigurationMetadata autoConfigurationMetadata;

		@Override
		public void setBeanClassLoader(ClassLoader classLoader) {
			this.beanClassLoader = classLoader;
		}

		@Override
		public void setBeanFactory(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Override
		public void setResourceLoader(ResourceLoader resourceLoader) {
			this.resourceLoader = resourceLoader;
		}

		@Override
		public void process(AnnotationMetadata annotationMetadata, DeferredImportSelector deferredImportSelector) {
			// 断言
			Assert.state(deferredImportSelector instanceof AutoConfigurationImportSelector,
					() -> String.format("Only %s implementations are supported, got %s",
							AutoConfigurationImportSelector.class.getSimpleName(),
							deferredImportSelector.getClass().getName()));

			/**
			 * 1、getAutoConfigurationMetadata()：
			 * 加载META-INF/spring-autoconfigure-metadata.properties文件中的内容，得到一个PropertiesAutoConfigurationMetadata
			 */
			// ⚠️获取所有的配置类
			AutoConfigurationEntry autoConfigurationEntry = ((AutoConfigurationImportSelector) deferredImportSelector)
					.getAutoConfigurationEntry(/* ⚠️ */getAutoConfigurationMetadata(), annotationMetadata);

			this.autoConfigurationEntries.add(autoConfigurationEntry);
			for (String importClassName : autoConfigurationEntry.getConfigurations()) {
				this.entries.putIfAbsent(importClassName, annotationMetadata);
			}
		}

		@Override
		public Iterable<Entry> selectImports() {
			if (this.autoConfigurationEntries.isEmpty()) {
				return Collections.emptyList();
			}

			// 获取"显示排除的配置类的全限定类名"
			Set<String> allExclusions = this.autoConfigurationEntries.stream()
					// 提取每个AutoConfigurationEntry中的exclusions
					.map(AutoConfigurationEntry::getExclusions).flatMap(Collection::stream).collect(Collectors.toSet());

			// 获取"要加载的配置类的全限定类名"
			Set<String> processedConfigurations = this.autoConfigurationEntries.stream()
					// 提取每个AutoConfigurationEntry中的configurations
					.map(AutoConfigurationEntry::getConfigurations).flatMap(Collection::stream)
					.collect(Collectors.toCollection(LinkedHashSet::new));

			// 移除掉显示要排除的配置类
			processedConfigurations.removeAll(allExclusions);

			// 排序之后，然后返回要加载解析的配置类，用Entry进行包裹。Entry当中包含了：1、标注@Import(AutoConfigurationImportSelector.class)类的注解元数据；2、配置类的全限定类名
			return sortAutoConfigurations(processedConfigurations, getAutoConfigurationMetadata())
					.stream()
					.map((importClassName) -> new Entry(this.entries.get(importClassName)/* 标注@Import(AutoConfigurationImportSelector.class)类的注解元数据 */, importClassName))
					.collect(Collectors.toList());
		}

		/**
		 * 加载spring-autoconfigure-metadata.properties文件中的所有内容，返回一个PropertiesAutoConfigurationMetadata对象
		 *
		 * @return  PropertiesAutoConfigurationMetadata
		 */
		private AutoConfigurationMetadata getAutoConfigurationMetadata() {
			if (this.autoConfigurationMetadata == null) {
				// ⚠️加载META-INF/spring-autoconfigure-metadata.properties文件中的内容，返回一个PropertiesAutoConfigurationMetadata对象
				this.autoConfigurationMetadata = AutoConfigurationMetadataLoader.loadMetadata(this.beanClassLoader);
			}
			return this.autoConfigurationMetadata;
		}

		/**
		 * 对配置类排序
		 *
		 * @param configurations					要加载的配置类
		 * @param autoConfigurationMetadata			spring-autoconfigure-metadata.properties文件中的所有内容所构建而成的一个PropertiesAutoConfigurationMetadata对象
		 * @return
		 */
		private List<String> sortAutoConfigurations(Set<String> configurations,
				AutoConfigurationMetadata autoConfigurationMetadata) {

			return new AutoConfigurationSorter(getMetadataReaderFactory(), autoConfigurationMetadata)
					// 排序
					.getInPriorityOrder(configurations);
		}

		private MetadataReaderFactory getMetadataReaderFactory() {
			try {
				return this.beanFactory.getBean(SharedMetadataReaderFactoryContextInitializer.BEAN_NAME,
						MetadataReaderFactory.class);
			}
			catch (NoSuchBeanDefinitionException ex) {
				return new CachingMetadataReaderFactory(this.resourceLoader);
			}
		}

	}

	protected static class AutoConfigurationEntry {

		// 要加载的配置类的全限定类名
		private final List<String> configurations;

		// 显示排除的配置类的全限定类名
		private final Set<String> exclusions;

		private AutoConfigurationEntry() {
			this.configurations = Collections.emptyList();
			this.exclusions = Collections.emptySet();
		}

		/**
		 * Create an entry with the configurations that were contributed and their
		 * exclusions.
		 * @param configurations the configurations that should be imported
		 * @param exclusions the exclusions that were applied to the original list
		 */
		AutoConfigurationEntry(Collection<String> configurations, Collection<String> exclusions) {
			this.configurations = new ArrayList<>(configurations);
			this.exclusions = new HashSet<>(exclusions);
		}

		public List<String> getConfigurations() {
			return this.configurations;
		}

		public Set<String> getExclusions() {
			return this.exclusions;
		}

	}

}
