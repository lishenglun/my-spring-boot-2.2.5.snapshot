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

package org.springframework.boot.context.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.*;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * {@link EnvironmentPostProcessor} that configures the context environment by loading
 * properties from well known file locations. By default properties will be loaded from
 * 'application.properties' and/or 'application.yml' files in the following locations:
 * <ul>
 * <li>file:./config/</li>
 * <li>file:./</li>
 * <li>classpath:config/</li>
 * <li>classpath:</li>
 * </ul>
 * The list is ordered by precedence (properties defined in locations higher in the list
 * override those defined in lower locations).
 * <p>
 * Alternative search locations and names can be specified using
 * {@link #setSearchLocations(String)} and {@link #setSearchNames(String)}.
 * <p>
 * Additional files will also be loaded based on active profiles. For example if a 'web'
 * profile is active 'application-web.properties' and 'application-web.yml' will be
 * considered.
 * <p>
 * The 'spring.config.name' property can be used to specify an alternative name to load
 * and the 'spring.config.location' property can be used to specify alternative search
 * locations or specific files.
 * <p>
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @author Eddú Meléndez
 * @author Madhura Bhave
 * @since 1.0.0
 */
public class ConfigFileApplicationListener implements EnvironmentPostProcessor, SmartApplicationListener, Ordered {

	private static final String DEFAULT_PROPERTIES = "defaultProperties";

	// Note the order is from least to most specific (last one wins)
	// 这四个目录是默认的查找属性文件的位置
	private static final String DEFAULT_SEARCH_LOCATIONS = "classpath:/,classpath:/config/,file:./,file:./config/";

	// 默认的配置文件名称
	private static final String DEFAULT_NAMES = "application";

	// 没有配置文件名称
	private static final Set<String> NO_SEARCH_NAMES = Collections.singleton(null);

	private static final Bindable<String[]> STRING_ARRAY = Bindable.of(String[].class);

	private static final Bindable<List<String>> STRING_LIST = Bindable.listOf(String.class);

	private static final Set<String> LOAD_FILTERED_PROPERTY;

	static {
		Set<String> filteredProperties = new HashSet<>();
		filteredProperties.add("spring.profiles.active");
		filteredProperties.add("spring.profiles.include");

		LOAD_FILTERED_PROPERTY = Collections.unmodifiableSet(filteredProperties);
	}

	/**
	 * The "active profiles" property name. —— "活动配置文件"属性名称
	 */
	// 激活的Profile
	public static final String ACTIVE_PROFILES_PROPERTY = "spring.profiles.active";

	/**
	 * The "includes profiles" property name. —— "包括配置文件"属性名称
	 */
	// 作用：叠加激活新的Profile
	public static final String INCLUDE_PROFILES_PROPERTY = "spring.profiles.include";

	/**
	 * The "config name" property name.
	 * 可以自定义属性文件的名称-并不一定是application
	 */
	// 自定义的属性文件名称
	public static final String CONFIG_NAME_PROPERTY = "spring.config.name";

	/**
	 * The "config location" property name.
	 */
	public static final String CONFIG_LOCATION_PROPERTY = "spring.config.location";

	/**
	 * The "config additional location" property name.
	 */
	public static final String CONFIG_ADDITIONAL_LOCATION_PROPERTY = "spring.config.additional-location";

	/**
	 * The default order for the processor.
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

	private final DeferredLog logger = new DeferredLog();

	private String searchLocations;

	private String names;

	private int order = DEFAULT_ORDER;

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return ApplicationEnvironmentPreparedEvent.class.isAssignableFrom(eventType)
				|| ApplicationPreparedEvent.class.isAssignableFrom(eventType);
	}

	/**
	 * 默认的是监听所有的事件.
	 *
	 * @param event 事件
	 */
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		/* 1、加载解析属性文件 */
		// 如果是ApplicationEnvironmentPreparedEvent事件，则加载解析属性文件
		if (event instanceof ApplicationEnvironmentPreparedEvent) {
			// 加载解析属性文件
			// 题外：为什么属性文件的加载解析要放到监听器里面？如果所有的功能都放入到run()里面，就没法保证扩展性。而通过监听机制，我就可以方便的扩展更多的功能。
			onApplicationEnvironmentPreparedEvent((ApplicationEnvironmentPreparedEvent) event);
		}

		/* 2、 */
		// 如果是 ApplicationPreparedEvent 则处理
		if (event instanceof ApplicationPreparedEvent) {
			onApplicationPreparedEvent(event);
		}
	}

	/**
	 * 属性文件的加载和解析
	 *
	 * @param event
	 */
	private void onApplicationEnvironmentPreparedEvent(ApplicationEnvironmentPreparedEvent event) {
		/* 1、获取spring.factories中所有的EnvironmentPostProcessor */
		// 获取spring.factories中所有的EnvironmentPostProcessor(环境配置的后置处理器)
		List<EnvironmentPostProcessor> postProcessors = loadPostProcessors();
		// 添加自身，即ConfigFileApplicationListener。因为ConfigFileApplicationListener implements EnvironmentPostProcessor，也是一个EnvironmentPostProcessor。
		postProcessors.add(this);
		// 排序
		AnnotationAwareOrderComparator.sort(postProcessors);

		/* 2、执行EnvironmentPostProcessor#postProcessEnvironment() */
		/**
		 * 系统提供那4个不是重点，重点是{@link ConfigFileApplicationListener#postProcessEnvironment}中的这个方法处理，加载和解析了属性文件
		 */
		// 遍历EnvironmentPostProcessor，执行EnvironmentPostProcessor#postProcessEnvironment()
		for (EnvironmentPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessEnvironment(event.getEnvironment(), event.getSpringApplication());
		}
	}

	/**
	 * 获取spring.factories中所有的EnvironmentPostProcessor实例
	 */
	List<EnvironmentPostProcessor> loadPostProcessors() {
		return SpringFactoriesLoader.loadFactories(EnvironmentPostProcessor.class, getClass().getClassLoader());
	}


	/**
	 * 加载解析属性文件
	 *
	 * @param environment the environment to post-process
	 * @param application the application to which the environment belongs
	 */
	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		// 添加属性源
		addPropertySources(environment, application.getResourceLoader());
	}

	private void onApplicationPreparedEvent(ApplicationEvent event) {
		this.logger.switchTo(ConfigFileApplicationListener.class);
		addPostProcessors(((ApplicationPreparedEvent) event).getApplicationContext());
	}

	/**
	 * Add config file property sources to the specified environment. —— 将配置文件属性源添加到指定环境。
	 * @param environment the environment to add source to
	 * @param resourceLoader the resource loader
	 * @see #addPostProcessors(ConfigurableApplicationContext)
	 */
	protected void addPropertySources(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
		/* 1、处理Environment中配置的"随机值表达"，生成对应随机值 */
		// 例如：我们配置的环境属性值：${random.value} ${random.long} ${random.uuid}，这些就是随机值表达式，
		// >>> 如果我们配置了这些随机值表达式，就可以帮助我们在环境配置中获取随机值
		RandomValuePropertySource/* 随机值属性源 */.addToEnvironment(environment);

		/* 2、获取spring.factories中配置的"属性资源加载器"，进行加载解析属性文件 */
		/**
		 * 1、new Loader(environment, resourceLoader)：
		 * 里面，获取了spring.factories中配置的PropertySourceLoader(属性资源加载器)，后续用来加载解析属性文件
		 */
		new Loader(environment, resourceLoader).load();
	}

	/**
	 * Add appropriate post-processors to post-configure the property-sources.
	 * @param context the context to configure
	 */
	protected void addPostProcessors(ConfigurableApplicationContext context) {
		context.addBeanFactoryPostProcessor(new PropertySourceOrderingPostProcessor(context));
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Set the search locations that will be considered as a comma-separated list. Each
	 * search location should be a directory path (ending in "/") and it will be prefixed
	 * by the file names constructed from {@link #setSearchNames(String) search names} and
	 * profiles (if any) plus file extensions supported by the properties loaders.
	 * Locations are considered in the order specified, with later items taking precedence
	 * (like a map merge).
	 * @param locations the search locations
	 */
	public void setSearchLocations(String locations) {
		Assert.hasLength(locations, "Locations must not be empty");
		this.searchLocations = locations;
	}

	/**
	 * Sets the names of the files that should be loaded (excluding file extension) as a
	 * comma-separated list.
	 * @param names the names to load
	 */
	public void setSearchNames(String names) {
		Assert.hasLength(names, "Names must not be empty");
		this.names = names;
	}

	/**
	 * {@link BeanFactoryPostProcessor} to re-order our property sources below any
	 * {@code @PropertySource} items added by the {@link ConfigurationClassPostProcessor}.
	 */
	private static class PropertySourceOrderingPostProcessor implements BeanFactoryPostProcessor, Ordered {

		private ConfigurableApplicationContext context;

		PropertySourceOrderingPostProcessor(ConfigurableApplicationContext context) {
			this.context = context;
		}

		@Override
		public int getOrder() {
			return Ordered.HIGHEST_PRECEDENCE;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
			reorderSources(this.context.getEnvironment());
		}

		private void reorderSources(ConfigurableEnvironment environment) {
			PropertySource<?> defaultProperties = environment.getPropertySources().remove(DEFAULT_PROPERTIES);
			if (defaultProperties != null) {
				environment.getPropertySources().addLast(defaultProperties);
			}
		}

	}

	/**
	 * Loads candidate property sources and configures the active profiles.
	 */
	private class Loader {

		private final Log logger = ConfigFileApplicationListener.this.logger;

		private final ConfigurableEnvironment environment;

		private final PropertySourcesPlaceholdersResolver placeholdersResolver;

		private final ResourceLoader resourceLoader;

		/**
		 * spring boot中，默认提供有：PropertiesPropertySourceLoader、YamPropertySourceLoader
		 */
		// spring.factories中配置的"属性资源加载器"
		private final List<PropertySourceLoader> propertySourceLoaders;

		// Profile集合
		private Deque<Profile> profiles;

		// 处理过的Profile
		private List<Profile> processedProfiles;

		// 是否"已经确定了当前系统激活使用的配置文件"的标识
		private boolean activatedProfiles/* 激活的配置文件 */;

		private Map<Profile, MutablePropertySources> loaded;

		private Map<DocumentsCacheKey, List<Document>> loadDocumentsCache = new HashMap<>();

		Loader(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
			// environment对象的赋值
			this.environment = environment;

			// 占位符的处理器
			this.placeholdersResolver = new PropertySourcesPlaceholdersResolver(this.environment);

			// 资源的加载器
			this.resourceLoader = (resourceLoader != null) ? resourceLoader : new DefaultResourceLoader();

			/**
			 * spring boot中，默认提供有：PropertiesPropertySourceLoader、YamPropertySourceLoader
			 */
			// ⚠️获取spring.factories中配置的PropertySourceLoader(属性资源加载器)，后续用来加载解析属性文件(例如：application.properties)
			this.propertySourceLoaders = SpringFactoriesLoader.loadFactories(PropertySourceLoader.class,
					getClass().getClassLoader());
		}

		void load() {
			FilteredPropertySource/* 过滤的属性源 */.apply(this.environment,
					DEFAULT_PROPERTIES/* defaultProperties */,
					LOAD_FILTERED_PROPERTY/* spring.profiles.active、spring.profiles.include */,
					(defaultProperties/* 一般为null */) -> {

						// 创建默认的Profile集合
						this.profiles = new LinkedList<>();
						// 创建已经处理过的Profile集合
						this.processedProfiles = new LinkedList<>();
						// 默认设置为"未确定当前系统激活使用的配置文件"
						this.activatedProfiles = false;
						// 存储已加载的配置文件
						this.loaded/* 已加载 */ = new LinkedHashMap<>();

						/*

						1、处理多环境配置，确定当前系统激活的环境。

						其实也就是获取Environment中配置的Profile。固定添加一个null Profile；以及由于默认情况下Environment中是没有配置Profile（没有配置激活的环境），所以会得到一个default Profile

						null Profile代表公共环境、公共配置，default Profile代表默认环境

						题外：确定当前系统激活的环境，从而也确定系统中激活使用的配置文件 —— Profile

						*/
						// (如果我们Environment中没有配置任何一个Profile，那么得到的是一个默认的Profile：default Profile)
						initializeProfiles();

						/*

						2、加载指定环境的配置文件

						题外：如果没有环境（Profile = null），那么就是加载默认的配置文件，例如：application.properties
						题外：如果有环境（Profile = dev），那么就加载对应环境的配置文件，例如：application-dev.properties

						 */
						// 遍历Profiles，并加载解析
						while (!this.profiles.isEmpty()) {
							Profile profile = this.profiles.poll();

							// 判断是不是默认的Profile(配置文件)，
							// （1）不是默认Profile，返回true；
							// （2）Profile=null，或者是默认配置文件，返回false
							if (isDefaultProfile(profile)) {
								// ⚠️存在Profile，并且不是默认Profile，就往Environment中添加当前系统激活使用的环境名
								addProfileToEnvironment(profile.getName());
							}

							// ⚠️加载解析对应的配置文件
							load(profile, this::getPositiveProfileFilter, addToLoaded(MutablePropertySources::addLast, false));

							// 记录已经处理过的Profile
							this.processedProfiles.add(profile);
						}

						/**
						 * 1、null代表加载无配置环境区分的默认配置文件，例如：application.properties，
						 * 不过在上面的while循环中，已经加载过了，唯一的区别，就是checkForExisting的区别，上面的是false，这里的是true
						 */
						// ⚠️加载解析对应的配置文件
						load(null, this::getNegativeProfileFilter, addToLoaded(MutablePropertySources::addFirst, true));

						// 添加加载的属性源
						addLoadedPropertySources();

						applyActiveProfiles(defaultProperties);
					});
		}

		/**
		 * 处理多环境配置。获取Environment中配置的Profile，确定系统中激活使用的Profile(配置文件)
		 *
		 * 不过默认情况下，Environment中是没有配置的Profile，没有配置激活使用的Profile(配置文件)，得到的是一个默认的Profile：default Profile
		 *
		 * Initialize profile information from both the {@link Environment} active
		 * profiles and any {@code spring.profiles.active}/{@code spring.profiles.include}
		 * properties that are already set.
		 *
		 * 从{@link Environment}活动配置文件和任何已设置的{@code spring.profiles.active}{@code spring.profiles.include}属性，初始化配置文件信息。
		 */
		private void initializeProfiles/* 初始化配置文件 */() {
			// The default profile for these purposes is represented as null. We add it
			// first so that it is processed first and has lowest priority.
			// 上面翻译：用于这些目的的默认配置文件表示为null。我们首先添加它，以便首先处理它，并具有最低优先级。

			/**
			 * 1、null Profile代表公共环境、公共配置，加载无配置环境区分的公共配置文件，例如：application.properties
			 *
			 * 2、放在第一位，代表，首先加载无环境区分的默认配置文件
			 */
			// 首先添加default profile，确保首先被执行，优先级最高

			this.profiles.add(null);

			/* 一、获取Profile */

			/*

			1、获取Environment中"spring.profiles.active"属性值对应的Profile

			题外：默认为null

			*/
			// 查找Environment中"spring.profiles.active"属性配置的profile
			Set<Profile> activatedViaProperty = getProfilesFromProperty(ACTIVE_PROFILES_PROPERTY/* spring.profiles.active */);

			/*

			2、获取Environment中"spring.profiles.include"属性值对应的Profile

			题外：默认为null

			*/
			// 查找Environment中"spring.profiles.include"属性配置的profile
			Set<Profile> includedViaProperty = getProfilesFromProperty(INCLUDE_PROFILES_PROPERTY/* spring.profiles.include */);

			/*

			3、获取除了上诉两个属性之外的，其它属性配置的Profile

			题外：默认为null

			*/
			// 查找Environment中除以上两类之外的其他属性配置的profile
			List<Profile> otherActiveProfiles = getOtherActiveProfiles(activatedViaProperty, includedViaProperty);

			/* 二、添加当前系统激活使用的Profile */

			/*

			4、其它属性配置的Profile添加到profiles队列中

			题外：由于默认为null，所以默认情况下，什么都没有添加

			*/
			this.profiles.addAll(otherActiveProfiles);
			// Any pre-existing active profiles set via property sources (e.g.
			// System properties) take precedence over those added in config files.
			// 上面翻译：通过属性源（例如系统属性）设置的任何预先存在的活动配置文件优先于配置文件中添加的配置文件。

			/*

			5、"spring.profiles.include"属性对应的Profile，添加到profiles队列中

			题外：由于默认为null，所以默认情况下，什么都没有添加

			*/
			this.profiles.addAll(includedViaProperty);

			/*

			6、"spring.profiles.active"属性对应的Profile，添加到profiles队列中；并设置"已确认当前系统激活使用的配置文件"

			题外：由于默认为null，所以默认情况下，什么都没有添加；并且也不会设置"已确认当前系统激活使用的配置文件"

			*/
			addActiveProfiles/* 添加活动配置文件 */(activatedViaProperty);

			/* 三、Environment中没有配置属性值对应的Profile，则获取默认的Profile：default */

			/* 7、如果没有从Environment中获取到任何Profile，就从Environment中获取默认的Profile：default */
			// 如果是空配置(没有任何profile配置)，也就是默认只添加了一个null
			if (this.profiles.size() == 1) { // only has null profile —— 只有空配置文件
				// 获取Environment中默认的配置文件名称。Environment中有一个默认的配置文件名称是：default
				for (String defaultProfileName/* 默认配置文件名称 *//* default */ : this.environment.getDefaultProfiles()) {
					Profile defaultProfile = new Profile(defaultProfileName, true/* 标识这是一个默认的配置文件名称 */);
					this.profiles.add(defaultProfile);
				}
			}
		}

		/**
		 * 获取Environment中对应属性名的属性值，然后构建成一个Profile返回
		 *
		 * @param profilesProperty	属性值
		 */
		private Set<Profile> getProfilesFromProperty(String profilesProperty) {
			/* 1、如果Environment中不包含profilesProperty属性名的Profile，则返回null */
			if (!this.environment.containsProperty(profilesProperty)) {
				return Collections.emptySet();
			}

			/* 2、获取Environment中profilesProperty属性名的Profile */
			Binder binder = Binder.get(this.environment);
			// 获取Environment中profilesProperty名称的属性
			Set<Profile> profiles = getProfiles(binder, profilesProperty);
			return new LinkedHashSet<>(profiles);
		}

		private List<Profile> getOtherActiveProfiles(Set<Profile> activatedViaProperty,
				Set<Profile> includedViaProperty) {
			return Arrays.stream(this.environment.getActiveProfiles()).map(Profile::new).filter(
					(profile) -> !activatedViaProperty.contains(profile) && !includedViaProperty.contains(profile))
					.collect(Collectors.toList());
		}

		/**
		 * 添加当前系统激活使用的配置文件
		 *
		 * 题外：若是由{@link Loader#load()} ——> {@link Loader#initializeProfiles()}
		 * 调用过来，默认情况下，profiles为null，所以什么事都不会干
		 *
		 * 题外：若是由{@link Loader#load(PropertySourceLoader, String, Profile, DocumentFilter, DocumentConsumer)}
		 * 调用过来，如果我们在classpath:/application.properties配置文件中配置了spring.profiles.active属性值，
		 * 那么profiles不会为null，会往下干活
		 *
		 * @param profiles			配置文件
		 */
		void addActiveProfiles/* 添加活动配置文件 */(Set<Profile> profiles) {

			// 注意：⚠️Profile虽然含义是激活的配置文件，但是并不是真正意义上的配置文件，并不包含配置文件的内容，完全的位置、名称，等实际信息

			/* 1、没有Profile，则不处理 */
			if (profiles.isEmpty()) {
				return;
			}

			/* 2、如果已经确定了当前系统激活使用的配置文件，那么则不再添加任何配置文件 */
			if (this.activatedProfiles) {
				if (this.logger.isDebugEnabled()) {
					// 配置文件已激活，'" + profiles + "' 将不会被应用
					this.logger.debug("Profiles already activated, '" + profiles/* 配置文件 */ + "' will not be applied");
				}
				return;
			}

			/* 3、添加配置文件 */
			this.profiles.addAll(profiles);
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Activated activeProfiles " + StringUtils.collectionToCommaDelimitedString(profiles));
			}

			/* 4、设置"已确认当前系统激活使用的配置文件"，后续不再添加任何的Profile */
			this.activatedProfiles = true;

			/* 5、⚠️删除未处理的默认配置文件(默认情况下，是name=default的Profile是默认的Profile，所以删除的是name=default的Profile) */
			removeUnprocessedDefaultProfiles();
		}

		/**
		 * ⚠️删除未处理的默认配置文件（默认情况下，是name=default的Profile是默认的Profile，所以删除的是name=default的Profile）
		 */
		private void removeUnprocessedDefaultProfiles/* ️删除未处理的默认配置文件 */() {
			// 删除未处理的默认配置文件
			// 默认情况下，是name=default的Profile是默认的Profile，所以删除的是name=default的Profile
			this.profiles.removeIf((profile) -> (profile != null && profile.isDefaultProfile()));
		}

		private DocumentFilter getPositiveProfileFilter(Profile profile) {
			return (Document document) -> {
				if (profile == null) {
					return ObjectUtils.isEmpty(document.getProfiles());
				}
				return ObjectUtils.containsElement(document.getProfiles(), profile.getName())
						&& this.environment.acceptsProfiles(Profiles.of(document.getProfiles()));
			};
		}

		private DocumentFilter getNegativeProfileFilter(Profile profile) {
			return (Document document) -> (profile == null && !ObjectUtils.isEmpty(document.getProfiles())
					&& this.environment.acceptsProfiles(Profiles.of(document.getProfiles())));
		}

		private DocumentConsumer addToLoaded(BiConsumer<MutablePropertySources, PropertySource<?>> addMethod,
				boolean checkForExisting) {
			return (profile, document) -> {

				if (checkForExisting) {
					for (MutablePropertySources merged : this.loaded.values()) {
						if (merged.contains(document.getPropertySource().getName())) {
							return;
						}
					}
				}

				MutablePropertySources merged = this.loaded.computeIfAbsent(profile,
						(k) -> new MutablePropertySources());
				addMethod.accept(merged, document.getPropertySource());
			};
		}

		private void load(Profile profile, DocumentFilterFactory filterFactory, DocumentConsumer consumer) {
			/* 1、先获取要搜索的目录，也就是要在哪个文件夹下搜索配置文件。然后逐一遍历要搜索的目录，查找指定文件名称的配置文件 */
			/**
			 * 1、getSearchLocations()：获取要查找配置文件的目录（也就是说，要去哪个目录下面查找属性文件）
			 * （1）先去获取自定义的位置，如果有，就用自定义的
			 * （2）如果没有自定义的，那么就获取额外自定义的位置以及默认的位置
			 *
			 * 一般自定义和额外自定义的位置都为null，所以一般得到的是默认的4个路径：【file:./config/】、【 file:./】、【classpath:/config/】、【classpath:/】
			 */
			// 获取要查找配置文件的目录位置，然后遍历目录位置，在每个目录下，逐一查找指定文件名的配置文件
			// 如果没有特殊指定，默认要查找配置文件的目录位置只有4个：【file:./config/】、【 file:./】、【classpath:/config/】、【classpath:/】
			getSearchLocations().forEach((location/* 目录 */) -> {
				/* （1）获取配置文件名称（可以自定义多个，默认的为：application） */
				// 判断是不是以"/"结尾，是的话，则代表location是一个目录
				boolean isFolder = location.endsWith("/");
				/**
				 * 1、getSearchNames()：获取配置文件名称
				 * （1）有自定义的，就获取environment中自定义的配置文件名称
				 * （2）没有自定义的，获取默认的配置文件名称：application
				 */
				// location是一个目录，则获取配置文件名称；否则配置文件名称为null
				// 注意：⚠️一般都是默认的配置文件名称：application
				Set<String> names = isFolder ? getSearchNames() : NO_SEARCH_NAMES/* 没有配置文件名称的意思 */;

				/* （2）在当前目录下，逐一加载和解析对应文件名的配置文件 */
				// 遍历配置文件名称，逐一加载和解析
				names.forEach((name/* 配置文件名称 */) -> /* ⚠️ */load(location/* 查找配置文件的目录 */, name, profile, filterFactory, consumer));
			});
		}

		/**
		 * 加载解析配置文件
		 *
		 * @param location					查找配置文件的目录
		 * @param name						配置文件名称
		 * @param profile
		 * @param filterFactory
		 * @param consumer
		 */
		private void load(String location, String name, Profile profile, DocumentFilterFactory filterFactory,
				DocumentConsumer consumer) {

			// 如果不存在配置文件名称
			if (!StringUtils.hasText(name)) {
				// 遍历"属性源加载器"
				for (PropertySourceLoader loader : this.propertySourceLoaders) {
					if (canLoadFileExtension/* 可以加载文件扩展名 */(loader, location)) {

						// 去对应的文件夹下加载属性文件 applicaiton.properties application.yml
						load(loader, location, profile, filterFactory.getDocumentFilter(profile), consumer);
						return;
					}
				}
				throw new IllegalStateException("File extension of config file location '" + location
						+ "' is not known to any PropertySourceLoader. If the location is meant to reference "
						+ "a directory, it must end in '/'");
			}
			Set<String> processed = new HashSet<>();


			/* 1、遍历PropertySourceLoader */

			/**
			 * 1、spring boot中，spring.factories文件里面，默认提供的PropertySourceLoader有：
			 *
			 * （1）{@link PropertiesPropertySourceLoader}：加载解析【.properties】、【.xml】后缀的配置文件
			 *
			 * （2）{@link YamlPropertySourceLoader}：加载解析【.yml】、【.ymal】后缀的配置文件
			 *
			 * 2、this.propertySourceLoaders的顺序是按照spring.factories文件中的配置顺序，是：PropertiesPropertySourceLoader、YamPropertySourceLoader
			 *
			 * 3、注意：⚠️4个不同后缀的配置文件的加载顺序在这里体现，顺序依次是：properties、xml、yml、ymal
			 * 也就是说，默认按顺序，依次加载application.properties、application.xml、application.yml、application.ymal
			 */
			// 遍历属性资源加载器，加载解析属性文件
			for (PropertySourceLoader loader : this.propertySourceLoaders) {

				/*

				2、获取PropertySourceLoader支持加载的文件后缀，进行遍历，逐一拼接文件名和文件后缀，然后加载对应配置文件

				默认的配置文件名称是application，默认的PropertySourceLoader有两个，总共支持4种后缀：.properties、.xml、.yml、.ymal，
				所以默认加载的配置文件是：application.properties、application.xml、application.yml、application.yaml

				*/
				/**
				 * 1、loader.getFileExtensions()：获取属性资源加载器中，支持加载的"文件后缀"
				 */
				// 遍历当前属性资源加载器，支持加载的"文件后缀"
				for (String fileExtension/* 属性文件后缀 */ : loader.getFileExtensions()) {
					/**
					 * 1、processed是一个set集合，为的是防重。只有添加成功了，说明之前没处理过该后缀的属性文件，才进行加载处理该后缀的属性文件，
					 * 也就是说：只处理，没有处理过当前后缀的属性文件；处理过了，就不处理
					 */
					// 添加后缀
					if (processed.add(fileExtension)) {
						/**
						 * 1、配置文件，例如：application.properties、application.xml、application.yml、application.yaml
						 */
						// ⚠️加载解析配置文件
						loadForFileExtension(loader, location + name, "." + fileExtension/* 属性文件后缀 */, profile, filterFactory,
								consumer);
					}
				}
			}
		}

		/**
		 * 可以加载文件扩展名
		 *
		 * @param loader
		 * @param name
		 */
		private boolean canLoadFileExtension(PropertySourceLoader loader, String name) {
			return Arrays.stream(loader.getFileExtensions()/* 获取支持加载的文件扩展名 */)
					.anyMatch((fileExtension) -> StringUtils.endsWithIgnoreCase(name, fileExtension));
		}

		/**
		 * 加载解析配置文件
		 *
		 * @param loader					属性资源加载器
		 * @param prefix					配置文件全路径名
		 *                                  例如：file:./config/application
		 * @param fileExtension				配置文件后缀
		 *                                  例如：.properties
		 * @param profile
		 * @param filterFactory
		 * @param consumer
		 */
		private void loadForFileExtension(PropertySourceLoader loader, String prefix, String fileExtension,
				Profile profile, DocumentFilterFactory filterFactory, DocumentConsumer consumer) {

			// 默认的文档过滤器
			// ConfigFileApplicationListener.Load#getDocumentFilter()
			DocumentFilter defaultFilter = filterFactory.getDocumentFilter(null);

			DocumentFilter profileFilter = filterFactory.getDocumentFilter(profile);

			if (profile != null) {
				// ⚠️如果有profile的情况，就拼接上profile。比如dev ——> application-dev.properties
				// 题外：从这里可以论证，profile是作为环境来使用的！
				String profileSpecificFile = prefix + "-" + profile + fileExtension;

				load(loader, profileSpecificFile, profile, defaultFilter, consumer);
				load(loader, profileSpecificFile, profile, profileFilter, consumer);
				// Try profile specific sections in files we've already processed
				for (Profile processedProfile : this.processedProfiles) {
					if (processedProfile != null) {
						String previouslyLoaded = prefix + "-" + processedProfile + fileExtension;
						load(loader, previouslyLoaded, profile, profileFilter, consumer);
					}
				}
			}

			// 加载配置文件中的配置信息
			load(loader, prefix + fileExtension/* 拼接，得到一个完整的配置文件路径，例如：classpath:/application.properties */, profile, profileFilter, consumer);
		}

		/**
		 * 获取配置文件中的配置信息
		 *
		 * @param loader
		 * @param location				配置文件名的完整名
		 *                              例如：classpath:/application.properties
		 * @param profile
		 * @param filter
		 * @param consumer
		 */
		private void load(PropertySourceLoader loader, String location, Profile profile, DocumentFilter filter,
				DocumentConsumer consumer) {
			try {

				// 根据配置文件地址，获取配置文件资源
				Resource resource = this.resourceLoader.getResource(location);

				// 不存在该配置文件，就跳过
				if (resource == null || !resource.exists()) {
					if (this.logger.isTraceEnabled()) {
						StringBuilder description = getDescription("Skipped missing config ", location, resource,
								profile);
						this.logger.trace(description);
					}
					return;
				}

				// 不存在文件扩展名，跳过
				if (!StringUtils.hasText(StringUtils.getFilenameExtension/* 获取文件扩展名 */(resource.getFilename()))) {
					if (this.logger.isTraceEnabled()) {
						StringBuilder description = getDescription("Skipped empty config extension ", location,
								resource, profile);
						this.logger.trace(description);
					}
					return;
				}
				String name = "applicationConfig: [" + location + "]";

				/* ⚠️️获取配置文件中的配置信息 */
				List<Document> documents = loadDocuments(loader, name, resource);

				if (CollectionUtils.isEmpty(documents)) {
					if (this.logger.isTraceEnabled()) {
						StringBuilder description = getDescription("Skipped unloaded config ", location, resource,
								profile);
						this.logger.trace(description);
					}
					return;
				}

				List<Document> loaded = new ArrayList<>();
				for (Document document : documents) {
					if (filter.match(document)) {
						/* 添加当前系统激活使用的配置文件 */
						/**
						 * 注意：默认情况下，{@link Loader#load()}中的while循环时，Profile有两个：一个是null Profile，一个是default Profile，
						 * 当while循环处理第一个null Profile时，如果我配置了classpath:/application.properties，那么会找到这个classpath:/application.properties，
						 * 进入到当前方法逻辑，得到一个Document，如果我们在classpath:/application.properties中配置了spring.profiles.active属性值，
						 * 那么document.getActiveProfiles()是可以获取得到spring.profiles.active属性值的，从而在执行addActiveProfiles()逻辑时，
						 * ⚠️里面会添加spring.profiles.active属性值的Profile，并把name=default的Profile删除了，
						 * 所以当我们再回到{@link Loader#load()}中的下一个while循环时，发现default Profile没有了，而是多了spring.profiles.active属性值的Profile，
						 *
						 * 不过戏剧性的是，多了的spring.profiles.active属性值的Profile，最终并不是由addActiveProfiles()添加进去的！
						 * 因为在addIncludedProfiles()当中，会把清空所有的Profiles，然后再重新添加
						 */
						// 添加当前系统激活使用的配置文件（添加配置文件中spring.profiles.active属性值的Profile，以及设置"已经激活当前系统中的配置文件"）
						addActiveProfiles(document.getActiveProfiles());

						/* 添加包含的配置文件 */
						// 添加包含的Profile（添加配置文件中spring.profiles.include属性值的Profile）
						addIncludedProfiles(document.getIncludeProfiles());

						loaded.add(document);
					}
				}

				Collections.reverse(loaded);

				if (!loaded.isEmpty()) {
					loaded.forEach((document) -> consumer.accept(profile, document));
					if (this.logger.isDebugEnabled()) {
						StringBuilder description = getDescription("Loaded config file ", location, resource, profile);
						this.logger.debug(description);
					}
				}
			}
			catch (Exception ex) {
				throw new IllegalStateException("Failed to load property source from location '" + location + "'", ex);
			}
		}

		/**
		 * 添加配置文件中spring.profiles.include属性值的Profile
		 *
		 * @param includeProfiles		获取当前配置文件中配置的spring.profiles.include属性的值
		 */
		private void addIncludedProfiles(Set<Profile> includeProfiles) {
			// ⚠️先保存原先的Profile
			LinkedList<Profile> existingProfiles/* 现有配置文件 */ = new LinkedList<>(this.profiles);
			// 清空所有的Profile
			this.profiles.clear();
			// 添加"配置文件中配置的spring.profiles.include属性值Profile"
			this.profiles.addAll(includeProfiles);
			// 删除调已经处理过的Profile
			this.profiles.removeAll(this.processedProfiles);
			// 合并
			this.profiles.addAll(existingProfiles);
		}

		/**
		 * ️获取配置文件中的配置信息
		 *
		 * @param loader
		 * @param name
		 * @param resource
		 * @return
		 * @throws IOException
		 */
		private List<Document> loadDocuments(PropertySourceLoader loader, String name, Resource resource)
				throws IOException {
			// 加载资源的文档缓存key
			DocumentsCacheKey cacheKey = new DocumentsCacheKey(loader, resource);
			// 先从缓存中获取
			List<Document> documents = this.loadDocumentsCache.get(cacheKey);

			if (documents == null) {

				/* ⚠️获取配置文件中的配置信息 */
				List<PropertySource<?>> loaded = loader.load(name, resource);

				/* ⚠️将配置文件中的配置信息，转换为Document，文档格式 */
				documents = asDocuments(loaded);

				// 放入缓存
				this.loadDocumentsCache.put(cacheKey, documents);
			}

			return documents;
		}

		private List<Document> asDocuments(List<PropertySource<?>> loaded) {
			if (loaded == null) {
				return Collections.emptyList();
			}

			return loaded.stream().map((propertySource) -> {

				Binder binder = new Binder(ConfigurationPropertySources.from(propertySource),
						this.placeholdersResolver);

				return new Document(propertySource,
						binder.bind("spring.profiles", STRING_ARRAY).orElse(null),
						// ⚠️
						getProfiles(binder, ACTIVE_PROFILES_PROPERTY/* spring.profiles.active */),
						// ⚠️
						getProfiles(binder, INCLUDE_PROFILES_PROPERTY/* spring.profiles.include */));

			}).collect(Collectors.toList());
		}

		private StringBuilder getDescription(String prefix, String location, Resource resource, Profile profile) {
			StringBuilder result = new StringBuilder(prefix);
			try {
				if (resource != null) {
					String uri = resource.getURI().toASCIIString();
					result.append("'");
					result.append(uri);
					result.append("' (");
					result.append(location);
					result.append(")");
				}
			}
			catch (IOException ex) {
				result.append(location);
			}
			if (profile != null) {
				result.append(" for profile ");
				result.append(profile);
			}
			return result;
		}

		private Set<Profile> getProfiles(Binder binder, String name) {
			return binder.bind(name, STRING_ARRAY).map(this::asProfileSet).orElse(Collections.emptySet());
		}

		private Set<Profile> asProfileSet(String[] profileNames) {
			List<Profile> profiles = new ArrayList<>();
			for (String profileName : profileNames) {
				profiles.add(new Profile(profileName));
			}
			return new LinkedHashSet<>(profiles);
		}

		/**
		 * 往environment中添加当前系统激活使用的环境
		 *
		 * @param profile			环境名，例如：dev
		 */
		private void addProfileToEnvironment(String profile) {
			/* 1、检验，environment中是不是已经存在了当前环境名，如果存在就不添加 */
			// 遍历当前系统环境中，激活使用的环境名
			for (String activeProfile : this.environment.getActiveProfiles()) {
				// 如果存在，就不重复添加，退出当前方法
				if (activeProfile.equals(profile)) {
					return;
				}
			}

			/* 2、不存在，往environment中添加当前系统激活使用的环境名 */
			// ⚠️往environment中添加当前系统激活使用的环境名
			this.environment.addActiveProfile(profile);
		}

		/**
		 * 获取查找配置文件的位置（也就是说，要去哪个目录下面查找属性文件）
		 * （1）先去获取自定义的位置，如果有，就用自定义的
		 * （2）如果没有自定义的，那么就获取额外自定义的位置以及默认的位置
		 *
		 * 一般自定义和额外自定义的位置都为null，所以一般得到的是默认的4个路径：【file:./config/】、【 file:./】、【classpath:/config/】、【classpath:/】
		 */
		private Set<String> getSearchLocations() {
			// 如果自定义了配置文件的存放位置，那么就用自定义的
			if (this.environment.containsProperty(CONFIG_LOCATION_PROPERTY/* spring.config.location */)) {
				return getSearchLocations(CONFIG_LOCATION_PROPERTY);
			}

			// 获取自定义的额外配置文件路径
			// 题外：一般为null
			Set<String> locations = getSearchLocations(CONFIG_ADDITIONAL_LOCATION_PROPERTY/* spring.config.additional-location */);

			// 添加默认的4个路径（这四个目录是默认的查找属性文件的位置）：【file:./config/】、【 file:./】、【classpath:/config/】、【classpath:/】
			locations.addAll(asResolvedSet(ConfigFileApplicationListener.this.searchLocations,
							DEFAULT_SEARCH_LOCATIONS/* classpath:/,classpath:/config/,file:./,file:./config/ */));

			return locations;
		}

		private Set<String> getSearchLocations(String propertyName) {
			Set<String> locations = new LinkedHashSet<>();

			// 判断environment中是否包含propertyName属性名称对应的属性值
			if (this.environment.containsProperty(propertyName)) {
				// 从environment中，获取对应属性名的属性值，变为一个set集合
				for (String path : asResolvedSet(this.environment.getProperty(propertyName)/* 获取属性值 */, null)) {
					// 不包含"$"
					if (!path.contains("$")) {
						path = StringUtils.cleanPath(path);
						if (!ResourceUtils.isUrl(path)) {
							path = ResourceUtils.FILE_URL_PREFIX + path;
						}
					}
					locations.add(path);
				}
			}

			return locations;
		}

		/**
		 * 获取配置文件名称
		 * （1）有自定义的，就获取自定义的配置文件名称
		 * （2）没有自定义的，获取默认的配置文件名称：application
		 */
		private Set<String> getSearchNames() {

			/* 1、获取environment中自定义的配置文件名称 */
			// 如果我们有自定义的配置文件名称，就获取自定义的配置文件名称
			if (this.environment.containsProperty(CONFIG_NAME_PROPERTY/* spring.config.name */)) {
				String property = this.environment.getProperty(CONFIG_NAME_PROPERTY);
				return asResolvedSet(property, null);
			}

			/* 2、获取默认的配置文件名称：application */
			return asResolvedSet(ConfigFileApplicationListener.this.names, DEFAULT_NAMES/* application */);
		}

		private Set<String> asResolvedSet(String value, String fallback) {
			List<String> list = Arrays.asList(StringUtils.trimArrayElements(
					StringUtils.commaDelimitedListToStringArray/* 逗号分隔列表到字符串数组 */((value != null) ? this.environment.resolvePlaceholders/* 解决占位符 */(value) : fallback))
			);
			Collections.reverse(list);
			return new LinkedHashSet<>(list);
		}

		private void addLoadedPropertySources() {
			MutablePropertySources destination/* 目的地 */ = this.environment.getPropertySources();
			List<MutablePropertySources> loaded = new ArrayList<>(this.loaded.values());
			Collections.reverse(loaded);
			String lastAdded = null;
			Set<String> added = new HashSet<>();
			for (MutablePropertySources sources : loaded) {
				for (PropertySource<?> source : sources) {
					if (added.add(source.getName())) {
						addLoadedPropertySource(destination, lastAdded, source);
						lastAdded = source.getName();
					}
				}
			}
		}

		private void addLoadedPropertySource(MutablePropertySources destination, String lastAdded,
				PropertySource<?> source) {
			if (lastAdded == null) {
				if (destination.contains(DEFAULT_PROPERTIES)) {
					destination.addBefore(DEFAULT_PROPERTIES, source);
				}
				else {
					destination.addLast(source);
				}
			}
			else {
				destination.addAfter(lastAdded, source);
			}
		}

		private void applyActiveProfiles(PropertySource<?> defaultProperties) {
			List<String> activeProfiles = new ArrayList<>();

			if (defaultProperties != null) {
				Binder binder = new Binder(ConfigurationPropertySources.from(defaultProperties),
						new PropertySourcesPlaceholdersResolver(this.environment));

				activeProfiles.addAll(getDefaultProfiles(binder, "spring.profiles.include"));
				if (!this.activatedProfiles) {
					activeProfiles.addAll(getDefaultProfiles(binder, "spring.profiles.active"));
				}
			}

			this.processedProfiles.stream().filter(this::isDefaultProfile).map(Profile::getName)
					.forEach(activeProfiles::add);

			this.environment.setActiveProfiles(activeProfiles.toArray(new String[0]));
		}

		/**
		 * 判断是不是默认配置文件
		 *
		 * 不是默认配置文件，返回true；
		 * 配置文件为null，或者是默认配置文件，返回false
		 *
		 * @param profile
		 * @return
		 */
		private boolean isDefaultProfile(Profile profile) {
			// profile != null && 不是默认配置文件
			return profile != null && !profile.isDefaultProfile();
		}

		private List<String> getDefaultProfiles(Binder binder, String property) {
			return binder.bind(property, STRING_LIST).orElse(Collections.emptyList());
		}

	}

	/**
	 * 激活的配置文件。里面包含：配置文件中的环境名，当前环境名是否是默认配置文件。
	 *
	 * 注意：⚠️Profile虽然含义是激活的配置文件，但是并不是真正意义上的配置文件，并不包含配置文件的内容，完全的位置、名称，等实际信息
	 *
	 * A Spring Profile that can be loaded. —— 可以加载的Spring Profile
	 */
	private static class Profile/* 可以直接翻译为"配置文件"的意思 */ {

		// 配置文件中的环境名
		// 例如：spring.profiles.active=dev，中的dev
		// 例如：spring.profiles.include=devDb，中的devDb
		// 注意：⚠️只是配置文件的环境名，不是整个配置文件名称
		private final String name;

		// 当前环境名是否是默认配置文件
		private final boolean defaultProfile;

		Profile(String name) {
			this(name, false);
		}

		Profile(String name, boolean defaultProfile) {
			Assert.notNull(name, "Name must not be null");
			this.name = name;
			this.defaultProfile = defaultProfile;
		}

		String getName() {
			return this.name;
		}

		/**
		 * 题外：默认情况下，是name=default的Profile是默认的Profile，所以删除的是name=default的Profile
		 */
		boolean isDefaultProfile() {
			return this.defaultProfile;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj == null || obj.getClass() != getClass()) {
				return false;
			}
			return ((Profile) obj).name.equals(this.name);
		}

		@Override
		public int hashCode() {
			return this.name.hashCode();
		}

		@Override
		public String toString() {
			return this.name;
		}

	}

	/**
	 * Cache key used to save loading the same document multiple times.
	 */
	private static class DocumentsCacheKey {

		private final PropertySourceLoader loader;

		private final Resource resource;

		DocumentsCacheKey(PropertySourceLoader loader, Resource resource) {
			this.loader = loader;
			this.resource = resource;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			DocumentsCacheKey other = (DocumentsCacheKey) obj;
			return this.loader.equals(other.loader) && this.resource.equals(other.resource);
		}

		@Override
		public int hashCode() {
			return this.loader.hashCode() * 31 + this.resource.hashCode();
		}

	}

	/**
	 * 单个配置文件
	 *
	 * A single document loaded by a {@link PropertySourceLoader}. —— {@link PropertySourceLoader} 加载的单个文档
	 */
	private static class Document {

		private final PropertySource<?> propertySource;

		private String[] profiles;

		// 配置文件中，spring.profiles.active属性的值
		// 例如：application.properties文件中，配置spring.profiles.active=dev,dev2；那么activeProfiles中的内容就等于dev,dev2，就有2个Profile
		private final Set<Profile> activeProfiles;

		// 配置文件中，spring.profiles.include属性的值
		private final Set<Profile> includeProfiles;

		Document(PropertySource<?> propertySource, String[] profiles, Set<Profile> activeProfiles,
				Set<Profile> includeProfiles) {
			this.propertySource = propertySource;
			this.profiles = profiles;
			this.activeProfiles = activeProfiles;
			this.includeProfiles = includeProfiles;
		}

		PropertySource<?> getPropertySource() {
			return this.propertySource;
		}

		String[] getProfiles() {
			return this.profiles;
		}

		/**
		 * 获取当前配置文件中配置的spring.profiles.active属性值
		 */
		Set<Profile> getActiveProfiles() {
			return this.activeProfiles;
		}

		/**
		 * 获取当前配置文件中配置的spring.profiles.include属性的值
		 */
		Set<Profile> getIncludeProfiles() {
			return this.includeProfiles;
		}

		@Override
		public String toString() {
			return this.propertySource.toString();
		}

	}

	/**
	 * Factory used to create a {@link DocumentFilter}.
	 */
	@FunctionalInterface
	private interface DocumentFilterFactory {

		/**
		 * Create a filter for the given profile.
		 * @param profile the profile or {@code null}
		 * @return the filter
		 */
		DocumentFilter getDocumentFilter(Profile profile);

	}

	/**
	 * Filter used to restrict when a {@link Document} is loaded.
	 */
	@FunctionalInterface
	private interface DocumentFilter {

		boolean match(Document document);

	}

	/**
	 * Consumer used to handle a loaded {@link Document}.
	 */
	@FunctionalInterface
	private interface DocumentConsumer {

		void accept(Profile profile, Document document);

	}

}
