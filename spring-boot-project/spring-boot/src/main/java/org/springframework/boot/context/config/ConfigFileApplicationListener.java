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
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.RandomValuePropertySource;
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
	private static final String DEFAULT_SEARCH_LOCATIONS = "classpath:/,classpath:/config/,file:./,file:./config/";

	// 默认的属性文件的名称
	private static final String DEFAULT_NAMES = "application";

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
	 * The "active profiles" property name.
	 */
	public static final String ACTIVE_PROFILES_PROPERTY = "spring.profiles.active";

	/**
	 * The "includes profiles" property name.
	 */
	public static final String INCLUDE_PROFILES_PROPERTY = "spring.profiles.include";

	/**
	 * The "config name" property name.
	 * 可以自定义属性文件的名称-并不一定是application
	 */
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
	 * @param event 事件
	 */
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		// 如果是 ApplicationEnvironmentPreparedEvent则处理
		if (event instanceof ApplicationEnvironmentPreparedEvent) {
			onApplicationEnvironmentPreparedEvent((ApplicationEnvironmentPreparedEvent) event);
		}
		// 如果是 ApplicationPreparedEvent 则处理
		if (event instanceof ApplicationPreparedEvent) {
			onApplicationPreparedEvent(event);
		}
	}

	private void onApplicationEnvironmentPreparedEvent(ApplicationEnvironmentPreparedEvent event) {
		// 加载系统提供的环境配置的后置处理器
		List<EnvironmentPostProcessor> postProcessors = loadPostProcessors();
		// 添加自身即 ConfigFileApplicationListener 为后置处理器
		postProcessors.add(this);
		// 原来有4个 现在加了一个需要重新排序
		AnnotationAwareOrderComparator.sort(postProcessors);
		for (EnvironmentPostProcessor postProcessor : postProcessors) {
			// 系统提供那4个不是重点，重点是 ConfigFileApplicationListener 中的这个方法处理
			postProcessor.postProcessEnvironment(event.getEnvironment(), event.getSpringApplication());
		}
	}

	List<EnvironmentPostProcessor> loadPostProcessors() {
		return SpringFactoriesLoader.loadFactories(EnvironmentPostProcessor.class, getClass().getClassLoader());
	}


	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		// 单个方法直接进入
		addPropertySources(environment, application.getResourceLoader());
	}

	private void onApplicationPreparedEvent(ApplicationEvent event) {
		this.logger.switchTo(ConfigFileApplicationListener.class);
		addPostProcessors(((ApplicationPreparedEvent) event).getApplicationContext());
	}

	/**
	 * Add config file property sources to the specified environment.
	 * @param environment the environment to add source to
	 * @param resourceLoader the resource loader
	 * @see #addPostProcessors(ConfigurableApplicationContext)
	 */
	protected void addPropertySources(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
		// 处理我们在属性文件中配置的 ${random.value} ${random.long} ${random.uuid}
		RandomValuePropertySource.addToEnvironment(environment);
		// 创建Loader对象同时会完成属性加载器的加载 同时调用load方法  boge_java
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

		private final List<PropertySourceLoader> propertySourceLoaders;

		private Deque<Profile> profiles;

		private List<Profile> processedProfiles;

		private boolean activatedProfiles;

		private Map<Profile, MutablePropertySources> loaded;

		private Map<DocumentsCacheKey, List<Document>> loadDocumentsCache = new HashMap<>();

		Loader(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
			// environment对象的赋值
			this.environment = environment;
			// 占位符的处理器
			this.placeholdersResolver = new PropertySourcesPlaceholdersResolver(this.environment);
			// 资源的加载器
			this.resourceLoader = (resourceLoader != null) ? resourceLoader : new DefaultResourceLoader();
			// 重点： 获取spring.factories 中配置的 属性加载器 用来加载解析properties文件或者yml文件
			this.propertySourceLoaders = SpringFactoriesLoader.loadFactories(PropertySourceLoader.class,
					getClass().getClassLoader());
		}

		void load() {
			FilteredPropertySource.apply(this.environment, DEFAULT_PROPERTIES, LOAD_FILTERED_PROPERTY,
					(defaultProperties) -> {
						// 创建默认的profile 链表
						this.profiles = new LinkedList<>();
						// 创建已经处理过的profile 类别
						this.processedProfiles = new LinkedList<>();
						// 默认设置为未激活
						this.activatedProfiles = false;
						// 创建loaded对象
						this.loaded = new LinkedHashMap<>();
						// 加载配置 profile 的信息，默认为 default
						initializeProfiles();
						// 遍历 Profiles，并加载解析
						while (!this.profiles.isEmpty()) {
							// 从双向链表中获取一个profile对象
							Profile profile = this.profiles.poll();
							// 非默认的就加入，进去看源码即可清楚
							if (isDefaultProfile(profile)) {
								addProfileToEnvironment(profile.getName());
							}
							load(profile, this::getPositiveProfileFilter,
									addToLoaded(MutablePropertySources::addLast, false));
							this.processedProfiles.add(profile);
						}
						// 解析 profile
						load(null, this::getNegativeProfileFilter, addToLoaded(MutablePropertySources::addFirst, true));
						// 加载默认的属性文件 application.properties
						addLoadedPropertySources();
						applyActiveProfiles(defaultProperties);
					});
		}

		/**
		 * Initialize profile information from both the {@link Environment} active
		 * profiles and any {@code spring.profiles.active}/{@code spring.profiles.include}
		 * properties that are already set.
		 */
		private void initializeProfiles() {
			// The default profile for these purposes is represented as null. We add it
			// first so that it is processed first and has lowest priority.
			// 首先添加default profile，确保首先被执行，并且优先级最低
			this.profiles.add(null);
			// 查找环境中 spring.profiles.active 属性配置的profile
			Set<Profile> activatedViaProperty = getProfilesFromProperty(ACTIVE_PROFILES_PROPERTY);
			// 查找环境中 spring.profiles.include 属性配置的profile
			Set<Profile> includedViaProperty = getProfilesFromProperty(INCLUDE_PROFILES_PROPERTY);
			//查找环境中除以上两类之外的其他属性配置的 Profile
			List<Profile> otherActiveProfiles = getOtherActiveProfiles(activatedViaProperty, includedViaProperty);
			//其他属性配置添加到 profiles 队列中
			this.profiles.addAll(otherActiveProfiles);
			// Any pre-existing active profiles set via property sources (e.g.
			// System properties) take precedence over those added in config files.
			//将 included 属 性添加到队列中
			this.profiles.addAll(includedViaProperty);
			//将 activatedViaProperty 添加入 profiles 队列， 并没置 activatedProfiles 为激活状态
			addActiveProfiles(activatedViaProperty);
			//如果没有任何 profile 配置，也 就是默认只添加了一个 null,则执行内部逻辑
			if (this.profiles.size() == 1) { // only has null profile
				// AbstractEnvironment 中有默认的 default 属性， 则将 default profile 添加到 profiles 中
				for (String defaultProfileName : this.environment.getDefaultProfiles()) {
					Profile defaultProfile = new Profile(defaultProfileName, true);
					this.profiles.add(defaultProfile);
				}
			}
		}

		private Set<Profile> getProfilesFromProperty(String profilesProperty) {
			if (!this.environment.containsProperty(profilesProperty)) {
				return Collections.emptySet();
			}
			Binder binder = Binder.get(this.environment);
			Set<Profile> profiles = getProfiles(binder, profilesProperty);
			return new LinkedHashSet<>(profiles);
		}

		private List<Profile> getOtherActiveProfiles(Set<Profile> activatedViaProperty,
				Set<Profile> includedViaProperty) {
			return Arrays.stream(this.environment.getActiveProfiles()).map(Profile::new).filter(
					(profile) -> !activatedViaProperty.contains(profile) && !includedViaProperty.contains(profile))
					.collect(Collectors.toList());
		}

		void addActiveProfiles(Set<Profile> profiles) {
			if (profiles.isEmpty()) {
				return;
			}
			if (this.activatedProfiles) {
				if (this.logger.isDebugEnabled()) {
					this.logger.debug("Profiles already activated, '" + profiles + "' will not be applied");
				}
				return;
			}
			this.profiles.addAll(profiles);
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Activated activeProfiles " + StringUtils.collectionToCommaDelimitedString(profiles));
			}
			this.activatedProfiles = true;
			removeUnprocessedDefaultProfiles();
		}

		private void removeUnprocessedDefaultProfiles() {
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
			// 获得默认的扫描路径，如果没有特殊指定 ，
			// 就采用常量 DEFAULT_ SEARCH_ LOCATIONS中定义的4个路 径 。
			// 而getSearchNames 方 法获得的就是 application 这个默认的配置文件名。
			// 然后，逐一遍历加载目录路径及其指定文件名的文件。
			// file:./config/ file:./ classpath:/config/ classpath:/ 默认的四个路径
			getSearchLocations().forEach((location) -> {
				boolean isFolder = location.endsWith("/");
				// 去对应的路径下获取属性文件 默认的文件名称为 application
				Set<String> names = isFolder ? getSearchNames() : NO_SEARCH_NAMES;
				names.forEach((name) -> load(location, name, profile, filterFactory, consumer));
			});
		}

		private void load(String location, String name, Profile profile, DocumentFilterFactory filterFactory,
				DocumentConsumer consumer) {
			if (!StringUtils.hasText(name)) {
				for (PropertySourceLoader loader : this.propertySourceLoaders) {
					if (canLoadFileExtension(loader, location)) {
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
			// 获取properties和yml的资源加载器
			for (PropertySourceLoader loader : this.propertySourceLoaders) {
				// 获取对应的加载器加载的后缀 properties xml yml ymal
				for (String fileExtension : loader.getFileExtensions()) {
					if (processed.add(fileExtension)) {
						// 加载文件 application.properties application.xml
						// application.yml application.yaml
						loadForFileExtension(loader, location + name, "." + fileExtension, profile, filterFactory,
								consumer);
					}
				}
			}
		}

		private boolean canLoadFileExtension(PropertySourceLoader loader, String name) {
			return Arrays.stream(loader.getFileExtensions())
					.anyMatch((fileExtension) -> StringUtils.endsWithIgnoreCase(name, fileExtension));
		}

		private void loadForFileExtension(PropertySourceLoader loader, String prefix, String fileExtension,
				Profile profile, DocumentFilterFactory filterFactory, DocumentConsumer consumer) {
			DocumentFilter defaultFilter = filterFactory.getDocumentFilter(null);
			DocumentFilter profileFilter = filterFactory.getDocumentFilter(profile);
			if (profile != null) {
				// 如果有profile的情况比如 dev --> application-dev.properties
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
			// 加载正常的情况的属性文件 application.properties
			load(loader, prefix + fileExtension, profile, profileFilter, consumer);
		}

		private void load(PropertySourceLoader loader, String location, Profile profile, DocumentFilter filter,
				DocumentConsumer consumer) {
			try {
				Resource resource = this.resourceLoader.getResource(location);
				if (resource == null || !resource.exists()) {
					if (this.logger.isTraceEnabled()) {
						StringBuilder description = getDescription("Skipped missing config ", location, resource,
								profile);
						this.logger.trace(description);
					}
					return;
				}
				if (!StringUtils.hasText(StringUtils.getFilenameExtension(resource.getFilename()))) {
					if (this.logger.isTraceEnabled()) {
						StringBuilder description = getDescription("Skipped empty config extension ", location,
								resource, profile);
						this.logger.trace(description);
					}
					return;
				}
				String name = "applicationConfig: [" + location + "]";
				// 加载属性文件信息
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
						addActiveProfiles(document.getActiveProfiles());
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

		private void addIncludedProfiles(Set<Profile> includeProfiles) {
			LinkedList<Profile> existingProfiles = new LinkedList<>(this.profiles);
			this.profiles.clear();
			this.profiles.addAll(includeProfiles);
			this.profiles.removeAll(this.processedProfiles);
			this.profiles.addAll(existingProfiles);
		}

		private List<Document> loadDocuments(PropertySourceLoader loader, String name, Resource resource)
				throws IOException {
			// 文档缓存的主键对象
			DocumentsCacheKey cacheKey = new DocumentsCacheKey(loader, resource);
			// 获取缓存的数据
			List<Document> documents = this.loadDocumentsCache.get(cacheKey);
			if (documents == null) {
				// 加载属性文件信息
				List<PropertySource<?>> loaded = loader.load(name, resource);
				documents = asDocuments(loaded);
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
				return new Document(propertySource, binder.bind("spring.profiles", STRING_ARRAY).orElse(null),
						getProfiles(binder, ACTIVE_PROFILES_PROPERTY), getProfiles(binder, INCLUDE_PROFILES_PROPERTY));
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

		private void addProfileToEnvironment(String profile) {
			for (String activeProfile : this.environment.getActiveProfiles()) {
				if (activeProfile.equals(profile)) {
					return;
				}
			}
			this.environment.addActiveProfile(profile);
		}

		private Set<String> getSearchLocations() {
			// 如果有自定义的路径就使用自定义的
			if (this.environment.containsProperty(CONFIG_LOCATION_PROPERTY)) {
				return getSearchLocations(CONFIG_LOCATION_PROPERTY);
			}
			// 否则使用默认的4个路径 file:./config/ file:./ classpath:/config/ classpath:/
			Set<String> locations = getSearchLocations(CONFIG_ADDITIONAL_LOCATION_PROPERTY);
			locations.addAll(
					asResolvedSet(ConfigFileApplicationListener.this.searchLocations, DEFAULT_SEARCH_LOCATIONS));
			return locations;
		}

		private Set<String> getSearchLocations(String propertyName) {
			Set<String> locations = new LinkedHashSet<>();
			if (this.environment.containsProperty(propertyName)) {
				for (String path : asResolvedSet(this.environment.getProperty(propertyName), null)) {
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

		private Set<String> getSearchNames() {
			// 如果我们有自定义配置文件的名称就加载自定义的文件否则就加载默认的属性文件
			if (this.environment.containsProperty(CONFIG_NAME_PROPERTY)) {
				String property = this.environment.getProperty(CONFIG_NAME_PROPERTY);
				return asResolvedSet(property, null);
			}
			// 加载默认的属性文件 application
			return asResolvedSet(ConfigFileApplicationListener.this.names, DEFAULT_NAMES);
		}

		private Set<String> asResolvedSet(String value, String fallback) {
			List<String> list = Arrays.asList(StringUtils.trimArrayElements(StringUtils.commaDelimitedListToStringArray(
					(value != null) ? this.environment.resolvePlaceholders(value) : fallback)));
			Collections.reverse(list);
			return new LinkedHashSet<>(list);
		}

		private void addLoadedPropertySources() {
			MutablePropertySources destination = this.environment.getPropertySources();
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

		private boolean isDefaultProfile(Profile profile) {
			return profile != null && !profile.isDefaultProfile();
		}

		private List<String> getDefaultProfiles(Binder binder, String property) {
			return binder.bind(property, STRING_LIST).orElse(Collections.emptyList());
		}

	}

	/**
	 * A Spring Profile that can be loaded.
	 */
	private static class Profile {

		private final String name;

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
	 * A single document loaded by a {@link PropertySourceLoader}.
	 */
	private static class Document {

		private final PropertySource<?> propertySource;

		private String[] profiles;

		private final Set<Profile> activeProfiles;

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

		Set<Profile> getActiveProfiles() {
			return this.activeProfiles;
		}

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
