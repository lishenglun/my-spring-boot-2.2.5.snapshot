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

import java.lang.reflect.Constructor;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.web.reactive.context.StandardReactiveWebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * Class that can be used to bootstrap and launch a Spring application from a Java main
 * method. By default class will perform the following steps to bootstrap your
 * application:
 *
 * <ul>
 * <li>Create an appropriate {@link ApplicationContext} instance (depending on your
 * classpath)</li>
 * <li>Register a {@link CommandLinePropertySource} to expose command line arguments as
 * Spring properties</li>
 * <li>Refresh the application context, loading all singleton beans</li>
 * <li>Trigger any {@link CommandLineRunner} beans</li>
 * </ul>
 *
 * In most circumstances the static {@link #run(Class, String[])} method can be called
 * directly from your {@literal main} method to bootstrap your application:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableAutoConfiguration
 * public class MyApplication  {
 *
 *   // ... Bean definitions
 *
 *   public static void main(String[] args) {
 *     SpringApplication.run(MyApplication.class, args);
 *   }
 * }
 * </pre>
 *
 * <p>
 * For more advanced configuration a {@link SpringApplication} instance can be created and
 * customized before being run:
 *
 * <pre class="code">
 * public static void main(String[] args) {
 *   SpringApplication application = new SpringApplication(MyApplication.class);
 *   // ... customize application settings here
 *   application.run(args)
 * }
 * </pre>
 *
 * {@link SpringApplication}s can read beans from a variety of different sources. It is
 * generally recommended that a single {@code @Configuration} class is used to bootstrap
 * your application, however, you may also set {@link #getSources() sources} from:
 * <ul>
 * <li>The fully qualified class name to be loaded by
 * {@link AnnotatedBeanDefinitionReader}</li>
 * <li>The location of an XML resource to be loaded by {@link XmlBeanDefinitionReader}, or
 * a groovy script to be loaded by {@link GroovyBeanDefinitionReader}</li>
 * <li>The name of a package to be scanned by {@link ClassPathBeanDefinitionScanner}</li>
 * </ul>
 *
 * Configuration properties are also bound to the {@link SpringApplication}. This makes it
 * possible to set {@link SpringApplication} properties dynamically, like additional
 * sources ("spring.main.sources" - a CSV list) the flag to indicate a web environment
 * ("spring.main.web-application-type=none") or the flag to switch off the banner
 * ("spring.main.banner-mode=off").
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Christian Dupuis
 * @author Stephane Nicoll
 * @author Jeremy Rickard
 * @author Craig Burke
 * @author Michael Simons
 * @author Madhura Bhave
 * @author Brian Clozel
 * @author Ethan Rubinson
 * @since 1.0.0
 * @see #run(Class, String[])
 * @see #run(Class[], String[])
 * @see #SpringApplication(Class...)
 */
public class SpringApplication {

	/**
	 * The class name of application context that will be used by default for non-web
	 * environments.
	 */
	public static final String DEFAULT_CONTEXT_CLASS = "org.springframework.context."
			+ "annotation.AnnotationConfigApplicationContext";

	/**
	 * The class name of application context that will be used by default for web
	 * environments.
	 */
	public static final String DEFAULT_SERVLET_WEB_CONTEXT_CLASS = "org.springframework.boot."
			+ "web.servlet.context.AnnotationConfigServletWebServerApplicationContext";

	/**
	 * The class name of application context that will be used by default for reactive web
	 * environments.
	 */
	public static final String DEFAULT_REACTIVE_WEB_CONTEXT_CLASS = "org.springframework."
			+ "boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext";

	/**
	 * Default banner location.
	 */
	public static final String BANNER_LOCATION_PROPERTY_VALUE = SpringApplicationBannerPrinter.DEFAULT_BANNER_LOCATION;

	/**
	 * Banner location property key.
	 */
	public static final String BANNER_LOCATION_PROPERTY = SpringApplicationBannerPrinter.BANNER_LOCATION_PROPERTY;

	private static final String SYSTEM_PROPERTY_JAVA_AWT_HEADLESS = "java.awt.headless";

	private static final Log logger = LogFactory.getLog(SpringApplication.class);

	// 记录run()传入的配置类
	// 例如：SpringApplication.run(Boot2StartApp.class)中的Boot2StartApp
	// 注意：⚠️primarySources和mainApplicationClass的区别：
	// >>> primarySources是我们在调用SpringApplication.run()时参数传入的Class，可以传入多个；
	// >>> 而mainApplicationClass是根据堆栈信息推导出来的main方法所在的Class；
	// >>> 两者可以不一样，但是，由于我们在SpringApplication.run()传入的参数一般是main方法所在的Class，所以两者一般相同！
	private Set<Class<?>> primarySources;

	private Set<String> sources = new LinkedHashSet<>();

	// 通过堆栈信息推导出的main方法所在的Class
	private Class<?> mainApplicationClass;

	private Banner.Mode bannerMode = Banner.Mode.CONSOLE;

	// 是否记录启动信息（true：记录；false：不记录）
	private boolean logStartupInfo = true;

	private boolean addCommandLineProperties = true;

	// 是否需要添加转换服务
	private boolean addConversionService = true;

	private Banner banner;

	// 资源加载器
	private ResourceLoader resourceLoader;

	// beanName生成器
	private BeanNameGenerator beanNameGenerator;

	private ConfigurableEnvironment environment;

	private Class<? extends ConfigurableApplicationContext> applicationContextClass;

	// 当前"Web项目的类型"
	private WebApplicationType webApplicationType;

	private boolean headless = true;

	private boolean registerShutdownHook = true;

	/**
	 * 默认有7个，分别是：
	 * （1）spring-boot模块中spring.factories中的5个：
	 * {@link org.springframework.boot.context.ConfigurationWarningsApplicationContextInitializer}
	 * {@link org.springframework.boot.context.ContextIdApplicationContextInitializer}
	 * {@link org.springframework.boot.context.config.DelegatingApplicationContextInitializer}
	 * {@link org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer}
	 * {@link org.springframework.boot.web.context.ServerPortInfoApplicationContextInitializer}
	 *
	 * （2）spring-boot-autoconfigure模块中spring.factories中的2个：
	 * {@link org.springframework.boot.autoconfigure.SharedMetadataReaderFactoryContextInitializer}
	 * {@link org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener}
	 */
	// 存储spring.factories文件中的"ApplicationContextInitializer(初始化器)"对象
	private List<ApplicationContextInitializer<?>> initializers;

	/**
	 * 题外：默认springboot项目中的spring.factories文件中有11个监听器
	 *
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
	// 存储spring.factories文件中的"监听器"（实例化好的）
	// 题外：这些监听器是在SpringApplication.run() ——> new SpringApplication()时进行的初始化
	private List<ApplicationListener<?>> listeners;

	private Map<String, Object> defaultProperties;

	private Set<String> additionalProfiles = new HashSet<>();

	private boolean allowBeanDefinitionOverriding;

	private boolean isCustomEnvironment = false;

	// 是否是懒加载的
	private boolean lazyInitialization = false;

	/**
	 * Create a new {@link SpringApplication} instance. The application context will load
	 * beans from the specified primary sources (see {@link SpringApplication class-level}
	 * documentation for details. The instance can be customized before calling
	 * {@link #run(String...)}.
	 * @param primarySources the primary bean sources
	 * @see #run(Class, String[])
	 * @see #SpringApplication(ResourceLoader, Class...)
	 * @see #setSources(Set)
	 */
	public SpringApplication(Class<?>... primarySources) {
		// 调用其他的构造方法
		this(null, primarySources);
	}

	/**
	 * 本方法中完成了几个核心操作：
	 * （1）记录run()传入的配置类
	 * （2）根据判断当前项目中是否存在一些"全限定类名"对应的类，推导出当前Web项目的类型。一共有3个：：servlet web项目 / reactive web项目 / 不是一个web项目
	 * （3）初始化spring.factories文件中配置的的"初始化器" —— ApplicationContextInitializer
	 * （4）初始化spring.factories文件中配置的spring体系的"监听器" —— ApplicationListener
	 * （5）通过堆栈信息反推main方法所在的Class对象
	 * 具体做法：挨个比对堆栈的方法名称是不是main，是的话就证明找到了main方法了，然后获取main方法所在的Class
	 *
	 * Create a new {@link SpringApplication} instance. The application context will load
	 * beans from the specified primary sources (see {@link SpringApplication class-level}
	 * documentation for details. The instance can be customized before calling
	 * {@link #run(String...)}.
	 * @param resourceLoader the resource loader to use
	 * @param primarySources the primary bean sources
	 * @see #run(Class, String[])
	 * @see #setSources(Set)
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
		// 传递的resourceLoader为null
		this.resourceLoader = resourceLoader;

		Assert.notNull(primarySources, "PrimarySources must not be null");

		/* 1、记录run()传入的配置类 */
		this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));

		/* 2、根据判断当前项目中是否存在一些"全限定类名"对应的类，推导出当前Web项目的类型。一共有3个：：1、servlet web项目 / 2、reactive web项目 / 3、不是一个web项目 */
		this.webApplicationType = WebApplicationType.deduceFromClasspath/* 从类路径推断 */();

		/**
		 * 💡提示：当前Spring boot项目下，只有spring-boot、spring-boot-autoconfigure这2个模块下，存在spring.factories文件
		 */

		/* 3、初始化spring.factories文件中配置的"初始化器" —— ApplicationContextInitializer */
		// 加载配置在spring.factories文件中的ApplicationContextInitializer实现类的全限定类名，并通过反射实例化对象，然后存储在initializers成员变量中
		setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));

		/* 4、初始化spring.factories文件中配置的spring体系的"监听器" —— ApplicationListener */
		// 加载配置在spring.factories文件中的监听器并实例化对象，然后将监听器对象存储在了listeners成员变量中
		setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));

		/*

		5、通过堆栈信息反推main方法所在的Class对象：
		具体做法：挨个比对堆栈的方法名称是不是main，是的话就证明找到了main方法了，然后获取main方法所在的Class

		*/
		this.mainApplicationClass = deduceMainApplicationClass()/* 反推main方法所在的Class对象 */;
	}

	/**
	 * 通过堆栈信息，推导main方法所在的Class
	 * 具体做法：挨个比对堆栈的方法名称是不是main，是的话就证明找到了main方法了，然后获取main方法所在的Class
	 */
	private Class<?> deduceMainApplicationClass() {
		try {
			/**
			 * 1、StackTrace：我们在学习函数调用时，都知道每个函数都拥有自己的栈空间。
			 * 一个函数被调用时，就创建一个新的栈空间。那么通过函数的嵌套调用最后就形成了一个函数调用堆栈。
			 */
			// 获取当前run方法执行的堆栈信息
			StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();

			// 遍历当前run方法执行的堆栈信息
			for (StackTraceElement stackTraceElement : stackTrace) {
				// 比对堆栈的方法名称是不是main，是的话就证明找到了main方法了，
				// 然后获取main方法所在的Class
				if ("main".equals(stackTraceElement.getMethodName())) {
					return Class.forName(stackTraceElement.getClassName());
				}
			}
		}
		catch (ClassNotFoundException ex) {
			// Swallow and continue —— 吞下并继续
		}
		return null;
	}

	/**
	 * 题外：spring boot启动过程中的核心节点，都会发布相关事件
	 *
	 * Run the Spring application, creating and refreshing a new
	 * {@link ApplicationContext}.
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return a running {@link ApplicationContext}
	 */
	public ConfigurableApplicationContext run(String... args) {
		/* 1、创建一个计时器，记录系统启动的时长 */
		// 创建一个任务执行观察器，统计启动的时间
		StopWatch/* 秒表 */ stopWatch = new StopWatch();
		// 开始执行记录执行时间
		stopWatch.start();

		// 要返回的容器对象（应用程序上下文 / Spring容器对象）
		ConfigurableApplicationContext context = null;

		/**
		 * SpringBootExceptionReporter：启动错误的回调接口。
		 */
		// 创建存储SpringBootExceptionReporter的集合
		// 记录服务启动时出现的一些异常的一些报告的回调接口
		Collection<SpringBootExceptionReporter> exceptionReporters = new ArrayList<>();

		// 设置了一个名为java.awt.headless的系统属性
		// 为的是，让当前应用程序，在即使没有检测到显示器的情况下，也允许其启动，因为我们的代码一般在服务器里面，对于服务器来说，是不需要显示器的，所以要这样设置
		configureHeadlessProperty();

		/*

		2、创建一个spring boot的广播器(SpringApplicationRunListeners)，里面初始化了spring.factories文件中spring boot体系的监听器(SpringApplicationRunListener)

		题外：默认只有一个spring boot的监听器：EventPublishingRunListener。在创建EventPublishingRunListener对象的时候，
		>>> 里面创建了spring的广播器(SimpleApplicationEventMulticaster)，以及获取了所有的spring监听器(new SpringApplication()时初始化的)，注入到spring广播器当中

		*/

		/**
		 * 1、默认从spring.factories文件中，获取到的SpringApplicationRunListener只有{@link org.springframework.boot.context.event.EventPublishingRunListener}这1个。
		 *
		 * （1）EventPublishingRunListener里面：获取了spring的事件广播器，以及spring体系内所有的的ApplicationListener
		 *
		 * 2、题外：SpringApplicationRunListeners本质上是一个spring boot体系内的"广播器/事件发布器"；用来发布事件时，触发spring boot体系内所有的监听器(SpringApplicationRunListener)
		 *
		 * 3、题外：⚠️事件设计，使得可以在系统启动的每一个关键节点（系统启动的不同的生命周期阶段），让我们都可以在对应的监听器里面去扩展一些行为！监听器可以监听我们想要的任一阶段的行为
		 */
		// 创建一个SpringApplicationRunListeners，读取和实例化spring.factories文件中所有的SpringApplicationRunListener(运行监听器)对象，存储在SpringApplicationRunListener里面
		// 简单概括：创建一个spring boot的广播器，然后获取spring boot的所有运行监听器，放入到广播器当中（后续广播器发布事件时，就可以触发所有spring boot的运行监听器）—— SpringApplicationRunListeners就相当于一个spring boot的广播器
		// 题外：SpringApplicationRunListeners发布事件时，内部调用的都是SpringApplicationRunListener对应的发布事件方法。
		SpringApplicationRunListeners listeners = getRunListeners(args);

		// 🚥发布启动事件
		// 题外：会触发调用"监听starting事件的监听器"
		listeners.starting();
		try {
			/* 3、创建一个"应用程序参数"的持有对象，持有应用程序参数 */
			ApplicationArguments/* 应用程序参数 */ applicationArguments = new DefaultApplicationArguments(args/* 一般为null */);

			/* 4、准备环境信息：(1)创建环境对象，加载系统环境参数，(2)发布环境准备事件 */
			// 读取配置环境，创建配置环境对象（会加载加载当前jvm(例如：jdk路径，jvm变量)、当前系统的环境(例如：当前系统的用户名)、以及自定义的属性信息）
			// 题外：里面也会发布事件
			ConfigurableEnvironment environment = prepareEnvironment(listeners, applicationArguments);

			// 配置需要忽略的BeanInfo信息
			configureIgnoreBeanInfo(environment);

			/* 5、打印Banner信息（Banner信息也就是Spring图标） */
			Banner printedBanner = printBanner(environment);

			/* ️6、根据当前web项目的类型，创建对应的应用上下文（Spring容器对象） */
			context = createApplicationContext();

			/* 7、获取spring.factories文件中的异常报告器 —— SpringBootExceptionReporter */

			// 获取spring.factories文件中的异常报告器 —— SpringBootExceptionReporter
			// 题外：启动异常处理器？
			exceptionReporters = getSpringFactoriesInstances(SpringBootExceptionReporter/* SpringBoot异常报告器 */.class,
					new Class[] { ConfigurableApplicationContext.class }, context);

			/*

			8、在刷新容器前，准备应用上下文

			注意：⚠️️里面注册了SpringApplication.run(class...)中的Class bd

			*/
			// 刷新容器前做的一些操作：对容器做的一些准备工作，准备容器（不是留给用户扩展的）
			// 题外：里面也会发布事件
			prepareContext/* 准备上下文 */(context, environment, listeners, applicationArguments, printedBanner);

			/* 9、⚠️刷新应用上下文，也就是完成Spring容器的初始化 */
			refreshContext(context);

			/* 10、刷新容器后做的一些操作（留给用户扩展实现） */
			afterRefresh(context, applicationArguments);

			// 启动结束，记录启动耗时
			stopWatch.stop();
			if (this.logStartupInfo) {
				new StartupInfoLogger(this.mainApplicationClass).logStarted(getApplicationLog(), stopWatch);
			}

			// 🚥发布启动完成事件
			listeners.started(context);

			/* 11、调用Runner执行器，也就是：执行容器中的ApplicationRunner#run()、CommandLineRunner#run() */
			callRunners(context, applicationArguments);
		}
		catch (Throwable ex) {
			// 事件广播启动出错了
			// 题外：里面也会发布事件
			handleRunFailure(context, ex, exceptionReporters, listeners);
			throw new IllegalStateException(ex);
		}
		try {
			// 🚥发布容器运行中的事件
			listeners.running(context);
		}
		catch (Throwable ex) {
			handleRunFailure(context, ex, exceptionReporters, null);
			throw new IllegalStateException(ex);
		}

		// 返回应用程序上下文对象（Spring容器对象）
		return context;
	}

	/**
	 * 准备环境
	 * （1）创建环境对象，加载系统参数
	 * （2）发布环境准备事件
	 *
	 * @param listeners
	 * @param applicationArguments
	 * @return
	 */
	private ConfigurableEnvironment prepareEnvironment/* 准备环境 */(SpringApplicationRunListeners listeners,
			ApplicationArguments applicationArguments) {
		/* 1、创建环境对象，加载系统参数 */
		// Create and configure the environment —— 创建和配置环境

		/* 以下是得到系统的一些配置信息，比如jvm的一些参数，系统的账号，当前登陆的用户 */

		// 创建并且配置Environment
		ConfigurableEnvironment environment = getOrCreateEnvironment/* 获取或创建环境 */();
		// 配置PropertySources和activeProfiles
		configureEnvironment(environment, applicationArguments.getSourceArgs());
		ConfigurationPropertySources.attach(environment);

		/* 2、🚥发布环境准备事件 */
		/**
		 * {@link org.springframework.boot.context.config.ConfigFileApplicationListener}解析了spring boot中的配置文件，例如：application.properties文件
		 */
		// 在准备环境信息的时候，会发布一个环境准备事件，
		listeners.environmentPrepared(environment);

		// 把相关的配置信息绑定到Spring容器中
		bindToSpringApplication(environment);
		if (!this.isCustomEnvironment) {
			environment = new EnvironmentConverter(getClassLoader()).convertEnvironmentIfNecessary(environment,
					deduceEnvironmentClass());
		}
		// 配置PropertySources对它自己的递归依赖
		ConfigurationPropertySources.attach(environment);

		return environment;
	}

	private Class<? extends StandardEnvironment> deduceEnvironmentClass() {
		switch (this.webApplicationType) {
		case SERVLET:
			return StandardServletEnvironment.class;
		case REACTIVE:
			return StandardReactiveWebEnvironment.class;
		default:
			return StandardEnvironment.class;
		}
	}

	/**
	 * 容器刷新前，对容器做的一些准备工作
	 *
	 * @param context									上下文
	 * @param environment								ConfigurableEnvironment
	 * @param listeners									spring.factories文件中的所有SpringApplicationRunListener
	 * @param applicationArguments						应用程序的参数持有对象
	 * @param printedBanner								Banner
	 */
	private void prepareContext/* 准备容器 */(ConfigurableApplicationContext context/* 上下文 */, ConfigurableEnvironment environment,
			SpringApplicationRunListeners listeners, ApplicationArguments applicationArguments, Banner printedBanner) {

		/* 1、往context中设置Environment */
		context.setEnvironment(environment);

		/* 2、如果存在，则往context中添加beanNameGenerator、resourceLoader、ConversionService(ApplicationConversionService) */
		postProcessApplicationContext(context);

		/* 3、调用spring.factories文件中所有的(初始化器)ApplicationContextInitializer#initialize()，处理ConfigurableApplicationContext */
		applyInitializers(context);

		/* 4、🚥️发布准备上下文事件 */
		listeners.contextPrepared(context);

		/* 5、记录启动信息和配置文件信息 */
		if (this.logStartupInfo/* 是否记录启动信息 */) {
			// 记录启动信息
			logStartupInfo(context.getParent() == null);
			// 记录启动配置文件信息
			logStartupProfileInfo(context);
		}

		/* 6、获取context中的beanFactory，往beanFactory中注册"spring应用程序参数对象"、Banner对象、是否允许bd覆盖的标识 */
		// Add boot specific singleton beans —— 添加引导特定的单例bean

		// (1)获取context中的beanFactory
		// GenericApplicationContext#getBeanFactory()
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		// (2)注册"spring应用程序的参数持有对象"
		beanFactory.registerSingleton("springApplicationArguments"/* spring应用程序参数 */, applicationArguments);
		// (3)注册springBootBanner对象
		if (printedBanner != null) {
			beanFactory.registerSingleton("springBootBanner", printedBanner);
		}
		// (4)设置是否允许bd覆盖的标识
		if (beanFactory instanceof DefaultListableBeanFactory) {
			((DefaultListableBeanFactory) beanFactory)
					.setAllowBeanDefinitionOverriding/* 设置允许bd覆盖 */(this.allowBeanDefinitionOverriding);
		}

		/* 7、如果是懒加载的，则往context中添加一个LazyInitializationBeanFactoryPostProcessor */
		if (this.lazyInitialization/* 是否是懒加载的 */) {
			context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
		}

		/* 8、⚠️注册了SpringApplication.run(class...)中的Class bd */
		// Load the sources —— 加载资源

		// ⚠️获取SpringApplication.run(class...)中传入的Class
		Set<Object> sources = getAllSources();
		Assert.notEmpty(sources, "Sources must not be empty");
		// ⚠️注册source对应的bd（也就是注册了SpringApplication.run(class...)中的Class bd）
		load(context, sources.toArray(new Object[0]));

		/* 9、🚥发布上下文加载完成事件 */
		listeners.contextLoaded(context);
	}

	private void refreshContext(ConfigurableApplicationContext context) {
		refresh(context);
		if (this.registerShutdownHook) {
			try {
				context.registerShutdownHook();
			}
			catch (AccessControlException ex) {
				// Not allowed in some environments.
			}
		}
	}

	private void configureHeadlessProperty() {
		System.setProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS/* java.awt.headless */,
				System.getProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS/* java.awt.headless */, Boolean.toString(this.headless/* 默认为true */)));
	}

	/**
	 * 读取spring.factories文件中所有的SpringApplicationRunListener类型的对象，
	 * 然后创建个SpringApplicationRunListeners，把SpringApplicationRunListener类型的对象放入其中
	 * SpringApplicationRunListeners发布事件时，内部调用的都是SpringApplicationRunListener对应的发布事件方法。
	 *
	 * @param args
	 * @return
	 */
	private SpringApplicationRunListeners getRunListeners(String[] args) {
		Class<?>[] types = new Class<?>[] { SpringApplication.class, String[].class };

		return new SpringApplicationRunListeners(logger,
				/**
				 * 题外：只有{@link org.springframework.boot.context.event.EventPublishingRunListener}这1个
				 */
				// 读取spring.factories文件中所有的SpringApplicationRunListener类型的对象
				getSpringFactoriesInstances(
						// 要从spring.factories文件中读取和构建对象的类型
						SpringApplicationRunListener.class,
						// 构造器参数类型
						types,
						// 构造器参数值
						this/* ⚠️这个是作为参数，把当前SpringApplication对象作为参数 */, args));
	}

	/**
	 * 加载spring.factories文件中配置的对应类型的所有实现类的全限定类名，然后通过反射实例化对象，最终返回实例化好的对象
	 *
	 * 💡提示：当前Spring boot项目下，只有spring-boot、spring-boot-autoconfigure这2个模块下，存在spring.factories文件
	 *
	 * @param type 类型
	 * @param <T> 泛型
	 * @return 返回实例
	 */
	private <T> Collection<T> getSpringFactoriesInstances(Class<T> type) {

		return getSpringFactoriesInstances(type, new Class<?>[] {});
	}

	/**
	 * 加载spring.factories文件中配置的对应类型的所有实现类的全限定类名，然后通过反射实例化对象，最终返回实例化好的对象
	 *
	 * 💡提示：当前Spring boot项目下，只有spring-boot、spring-boot-autoconfigure这2个模块下，存在spring.factories文件
	 *
	 * @param type 类型
	 * @param parameterTypes 参数类型
	 * @param args 参数
	 * @param <T> 泛型
	 * @return  返回实例
	 */
	private <T> Collection<T> getSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes, Object... args) {
		// 获取当前上下文类加载器
		ClassLoader classLoader = getClassLoader();

		/* 1、获取spring.factories文件中，对应类型的全限定类名 */
		// 加载spring.factories文件中的所有信息到内存中，然后根据类型，获取相关的实现类的全限定类名
		Set<String> names = new LinkedHashSet<>(SpringFactoriesLoader.loadFactoryNames(type, classLoader));

		/* 2、根据类型的全限定类名，通过反射实例化对象 */
		List<T> instances = createSpringFactoriesInstances(type, parameterTypes, classLoader, args, names);

		/* 3、排序 */
		AnnotationAwareOrderComparator.sort(instances);

		return instances;
	}

	/**
	 * 根据类型的全限定类名，通过反射实例化对象
	 *
	 * @param type
	 * @param parameterTypes
	 * @param classLoader
	 * @param args
	 * @param names				type所有的实现类的全限定类名
	 * @param <T>
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private <T> List<T> createSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes,
			ClassLoader classLoader, Object[] args, Set<String> names) {

		// 实例集合
		List<T> instances = new ArrayList<>(names.size());

		// 遍历type的所有实现类的全限定类名
		for (String name : names) {
			try {
				// 通过反射进行实例化
				Class<?> instanceClass = ClassUtils.forName(name, classLoader);
				Assert.isAssignable(type, instanceClass);
				Constructor<?> constructor = instanceClass.getDeclaredConstructor(parameterTypes);
				T instance = (T) BeanUtils.instantiateClass(constructor, args);
				instances.add(instance);
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Cannot instantiate " + type + " : " + name, ex);
			}
		}
		return instances;
	}

	private ConfigurableEnvironment getOrCreateEnvironment() {
		if (this.environment != null) {
			return this.environment;
		}
		switch (this.webApplicationType) {
		case SERVLET:
			return new StandardServletEnvironment();
		case REACTIVE:
			return new StandardReactiveWebEnvironment();
		default:
			return new StandardEnvironment();
		}
	}

	/**
	 * Template method delegating to
	 * {@link #configurePropertySources(ConfigurableEnvironment, String[])} and
	 * {@link #configureProfiles(ConfigurableEnvironment, String[])} in that order.
	 * Override this method for complete control over Environment customization, or one of
	 * the above for fine-grained control over property sources or profiles, respectively.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureProfiles(ConfigurableEnvironment, String[])
	 * @see #configurePropertySources(ConfigurableEnvironment, String[])
	 */
	protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
		if (this.addConversionService) {
			ConversionService conversionService = ApplicationConversionService.getSharedInstance();
			environment.setConversionService((ConfigurableConversionService) conversionService);
		}
		configurePropertySources(environment, args);
		configureProfiles(environment, args);
	}

	/**
	 * Add, remove or re-order any {@link PropertySource}s in this application's
	 * environment.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureEnvironment(ConfigurableEnvironment, String[])
	 */
	protected void configurePropertySources(ConfigurableEnvironment environment, String[] args) {
		MutablePropertySources sources = environment.getPropertySources();
		if (this.defaultProperties != null && !this.defaultProperties.isEmpty()) {
			sources.addLast(new MapPropertySource("defaultProperties", this.defaultProperties));
		}
		if (this.addCommandLineProperties && args.length > 0) {
			String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
			if (sources.contains(name)) {
				PropertySource<?> source = sources.get(name);
				CompositePropertySource composite = new CompositePropertySource(name);
				composite.addPropertySource(
						new SimpleCommandLinePropertySource("springApplicationCommandLineArgs", args));
				composite.addPropertySource(source);
				sources.replace(name, composite);
			}
			else {
				sources.addFirst(new SimpleCommandLinePropertySource(args));
			}
		}
	}

	/**
	 * Configure which profiles are active (or active by default) for this application
	 * environment. Additional profiles may be activated during configuration file
	 * processing via the {@code spring.profiles.active} property.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureEnvironment(ConfigurableEnvironment, String[])
	 * @see org.springframework.boot.context.config.ConfigFileApplicationListener
	 */
	protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
		Set<String> profiles = new LinkedHashSet<>(this.additionalProfiles);
		profiles.addAll(Arrays.asList(environment.getActiveProfiles()));
		environment.setActiveProfiles(StringUtils.toStringArray(profiles));
	}

	private void configureIgnoreBeanInfo(ConfigurableEnvironment environment) {
		if (System.getProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME) == null) {
			Boolean ignore = environment.getProperty("spring.beaninfo.ignore", Boolean.class, Boolean.TRUE);
			System.setProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME, ignore.toString());
		}
	}

	/**
	 * Bind the environment to the {@link SpringApplication}.
	 * @param environment the environment to bind
	 */
	protected void bindToSpringApplication(ConfigurableEnvironment environment) {
		try {
			Binder.get(environment).bind("spring.main", Bindable.ofInstance(this));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Cannot bind to SpringApplication", ex);
		}
	}

	private Banner printBanner(ConfigurableEnvironment environment) {
		if (this.bannerMode == Banner.Mode.OFF) {
			return null;
		}
		ResourceLoader resourceLoader = (this.resourceLoader != null) ? this.resourceLoader
				: new DefaultResourceLoader(getClassLoader());
		SpringApplicationBannerPrinter bannerPrinter = new SpringApplicationBannerPrinter(resourceLoader, this.banner);
		if (this.bannerMode == Mode.LOG) {
			return bannerPrinter.print(environment, this.mainApplicationClass, logger);
		}
		return bannerPrinter.print(environment, this.mainApplicationClass, System.out);
	}

	/**
	 * 根据当前web项目的类型，创建对应的应用程序上下文对象
	 *
	 * Strategy method used to create the {@link ApplicationContext}. By default this
	 * method will respect any explicitly set application context or application context
	 * class before falling back to a suitable default.
	 * @return the application context (not yet refreshed)
	 * @see #setApplicationContextClass(Class)
	 */
	protected ConfigurableApplicationContext createApplicationContext() {
		Class<?> contextClass = this.applicationContextClass;
		/* 1、根据当前"Web项目的类型"，获取对应的容器类型 */
		if (contextClass == null) {
			try {
				switch (this.webApplicationType) {
				/* （1）servlet：AnnotationConfigServletWebServerApplicationContext */
				case SERVLET:
					contextClass = Class.forName(DEFAULT_SERVLET_WEB_CONTEXT_CLASS/* AnnotationConfigServletWebServerApplicationContext */);
					break;
				/* （2）reactive：AnnotationConfigReactiveWebServerApplicationContext */
				case REACTIVE:
					contextClass = Class.forName(DEFAULT_REACTIVE_WEB_CONTEXT_CLASS/* AnnotationConfigReactiveWebServerApplicationContext */);
					break;
				/* （3）默认：AnnotationConfigApplicationContext */
				default:
					contextClass = Class.forName(DEFAULT_CONTEXT_CLASS/* AnnotationConfigApplicationContext */);
				}
			}
			catch (ClassNotFoundException ex) {
				throw new IllegalStateException(
						"Unable create a default ApplicationContext, please specify an ApplicationContextClass", ex);
			}
		}

		/* 2、根据容器类型，实例化对应的容器对象 */
		return (ConfigurableApplicationContext) BeanUtils.instantiateClass(contextClass);
	}

	/**
	 * Apply any relevant post processing the {@link ApplicationContext}. Subclasses can
	 * apply additional processing as required.
	 *
	 * 应用任何相关的后处理 {@link ApplicationContext}。子类可以根据需要应用额外的处理。
	 *
	 * @param context the application context
	 */
	protected void postProcessApplicationContext/* 后置处理ApplicationContext */(ConfigurableApplicationContext context) {
		/* 1、存在beanName生成器，就往context中注入beanName生成器 */
		if (this.beanNameGenerator != null) {
			context.getBeanFactory().registerSingleton(
					AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR/* org.springframework.context.annotation.internalConfigurationBeanNameGenerator */,
					this.beanNameGenerator);
		}

		/* 2、存在资源加载器 */
		if (this.resourceLoader != null) {
			// （1）资源加载器
			if (context instanceof GenericApplicationContext) {
				((GenericApplicationContext) context).setResourceLoader(this.resourceLoader);
			}
			// （2）类加载器
			if (context instanceof DefaultResourceLoader) {
				((DefaultResourceLoader) context).setClassLoader(this.resourceLoader.getClassLoader());
			}
		}

		/* 3、需要添加转换服务，则往context添加转换服务 */
		if (this.addConversionService/* 是否需要添加转换服务 */) {
			/**
			 * 1、ApplicationConversionService.getSharedInstance：
			 * 获取ConversionService：ApplicationConversionService。不存在就创建。
			 */
			// 往context添加转换服务(ConversionService)：ApplicationConversionService
			context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance/* 获取共享实例 */());
		}
	}

	/**
	 * 调用spring.factories文件中的所有的(初始化器)ApplicationContextInitializer#initialize()，处理ConfigurableApplicationContext
	 *
	 * Apply any {@link ApplicationContextInitializer}s to the context before it is
	 * refreshed.
	 * @param context the configured ApplicationContext (not refreshed yet)
	 * @see ConfigurableApplicationContext#refresh()
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void applyInitializers(ConfigurableApplicationContext context) {
		/* 1、调用spring.factories文件中所有的(初始化器)ApplicationContextInitializer#initialize()，处理ConfigurableApplicationContext */
		/**
		 * 1、getInitializers()：
		 * 获取spring.factories文件中的"ApplicationContextInitializer(初始化器)"。默认有7个，分别是：
		 * （1）spring-boot模块中spring.factories中的5个：
		 * {@link org.springframework.boot.context.ConfigurationWarningsApplicationContextInitializer}
		 * {@link org.springframework.boot.context.ContextIdApplicationContextInitializer}
		 * {@link org.springframework.boot.context.config.DelegatingApplicationContextInitializer}
		 * {@link org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer}
		 * {@link org.springframework.boot.web.context.ServerPortInfoApplicationContextInitializer}
		 *
		 * （2）spring-boot-autoconfigure模块中spring.factories中的2个：
		 * {@link org.springframework.boot.autoconfigure.SharedMetadataReaderFactoryContextInitializer}
		 * {@link org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener}
		 */
		for (ApplicationContextInitializer initializer/* 初始化器 */: getInitializers()) {
			// 获取ApplicationContextInitializer实现类的Class
			Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(initializer.getClass(),
					ApplicationContextInitializer.class);
			// (忽略)断言提供的对象是提供的类的实例。Assert.instanceOf(Foo.class, foo, "Foo 预期");
			Assert.isInstanceOf(requiredType, context, "Unable to call initializer."/* 无法调用初始化程序。 */);
			// ⚠️调用ApplicationContextInitializer#initialize()处理ConfigurableApplicationContext
			initializer.initialize(context);
		}
	}

	/**
	 * Called to log startup information, subclasses may override to add additional
	 * logging.
	 * @param isRoot true if this application is the root of a context hierarchy
	 */
	protected void logStartupInfo(boolean isRoot) {
		if (isRoot) {
			new StartupInfoLogger(this.mainApplicationClass).logStarting(getApplicationLog());
		}
	}

	/**
	 * Called to log active profile information. —— 调用以记录活动的配置文件信息
	 *
	 * @param context the application context —— 应用程序上下文
	 */
	protected void logStartupProfileInfo(ConfigurableApplicationContext context) {
		Log log = getApplicationLog();
		if (log.isInfoEnabled()) {
			String[] activeProfiles = context.getEnvironment().getActiveProfiles();
			if (ObjectUtils.isEmpty(activeProfiles)) {
				String[] defaultProfiles = context.getEnvironment().getDefaultProfiles();
				log.info("No active profile set, falling back to default profiles: " /* 没有活动的配置文件集，回退到默认配置文件： */
						+ StringUtils.arrayToCommaDelimitedString(defaultProfiles));
			}
			else {
				log.info("The following profiles are active: " /* 以下配置文件处于活动状态： */
						+ StringUtils.arrayToCommaDelimitedString(activeProfiles));
			}
		}
	}

	/**
	 * Returns the {@link Log} for the application. By default will be deduced. —— 返回应用程序的 {@link Log}。默认情况下会被推导出来。
	 * @return the application log
	 */
	protected Log getApplicationLog() {
		if (this.mainApplicationClass == null) {
			return logger;
		}
		return LogFactory.getLog(this.mainApplicationClass);
	}

	/**
	 * Load beans into the application context. —— 将bean加载到应用程序上下文中。
	 * @param context the context to load beans into
	 * @param sources the sources to load
	 *                SpringApplication.run(class...)中传入的类
	 */
	protected void load(ApplicationContext context, Object[] sources) {
		if (logger.isDebugEnabled()) {
			logger.debug("Loading source " + StringUtils.arrayToCommaDelimitedString(sources));
		}
		BeanDefinitionLoader loader = createBeanDefinitionLoader(getBeanDefinitionRegistry(context)/* BeanDefinitionRegistry */, sources);

		// 设置beanNameGenerator
		if (this.beanNameGenerator != null) {
			loader.setBeanNameGenerator(this.beanNameGenerator);
		}
		// 设置resourceLoader
		if (this.resourceLoader != null) {
			loader.setResourceLoader(this.resourceLoader);
		}
		// 设置environment
		if (this.environment != null) {
			loader.setEnvironment(this.environment);
		}

		// 注册source对应的bd(⚠️source里面包含了SpringApplication.run(class...)中的Class)
		loader.load();
	}

	/**
	 * The ResourceLoader that will be used in the ApplicationContext.
	 * @return the resourceLoader the resource loader that will be used in the
	 * ApplicationContext (or null if the default)
	 */
	public ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	/**
	 * Either the ClassLoader that will be used in the ApplicationContext (if
	 * {@link #setResourceLoader(ResourceLoader) resourceLoader} is set, or the context
	 * class loader (if not null), or the loader of the Spring {@link ClassUtils} class.
	 * @return a ClassLoader (never null)
	 */
	public ClassLoader getClassLoader() {
		if (this.resourceLoader != null) {
			return this.resourceLoader.getClassLoader();
		}
		return ClassUtils.getDefaultClassLoader();
	}

	/**
	 * Get the bean definition registry.
	 * @param context the application context
	 * @return the BeanDefinitionRegistry if it can be determined
	 */
	private BeanDefinitionRegistry getBeanDefinitionRegistry(ApplicationContext context) {
		if (context instanceof BeanDefinitionRegistry) {
			return (BeanDefinitionRegistry) context;
		}
		if (context instanceof AbstractApplicationContext) {
			return (BeanDefinitionRegistry) ((AbstractApplicationContext) context).getBeanFactory();
		}
		throw new IllegalStateException("Could not locate BeanDefinitionRegistry");
	}

	/**
	 * Factory method used to create the {@link BeanDefinitionLoader}.
	 * @param registry the bean definition registry
	 * @param sources the sources to load
	 * @return the {@link BeanDefinitionLoader} that will be used to load beans
	 */
	protected BeanDefinitionLoader createBeanDefinitionLoader(BeanDefinitionRegistry registry, Object[] sources) {
		return new BeanDefinitionLoader(registry, sources);
	}

	/**
	 * Refresh the underlying {@link ApplicationContext}.
	 * @param applicationContext the application context to refresh
	 */
	protected void refresh(ApplicationContext applicationContext) {
		Assert.isInstanceOf(AbstractApplicationContext.class, applicationContext);
		((AbstractApplicationContext) applicationContext).refresh();
	}

	/**
	 * Called after the context has been refreshed.
	 * @param context the application context
	 * @param args the application arguments
	 */
	protected void afterRefresh(ConfigurableApplicationContext context, ApplicationArguments args) {
	}

	/**
	 * 执行容器中的ApplicationRunner#run()、CommandLineRunner#run()
	 */
	private void callRunners(ApplicationContext context, ApplicationArguments args) {
		List<Object> runners = new ArrayList<>();

		// 获取容器中的ApplicationRunner
		runners.addAll(context.getBeansOfType(ApplicationRunner.class).values());
		// 获取容器中的CommandLineRunner
		runners.addAll(context.getBeansOfType(CommandLineRunner.class).values());

		AnnotationAwareOrderComparator.sort(runners);
		for (Object runner : new LinkedHashSet<>(runners)) {
			// 执行ApplicationRunner#run()
			if (runner instanceof ApplicationRunner) {
				callRunner((ApplicationRunner) runner, args);
			}

			// 执行CommandLineRunner#run()
			if (runner instanceof CommandLineRunner) {
				callRunner((CommandLineRunner) runner, args);
			}
		}
	}

	private void callRunner(ApplicationRunner runner, ApplicationArguments args) {
		try {
			(runner).run(args);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to execute ApplicationRunner", ex);
		}
	}

	private void callRunner(CommandLineRunner runner, ApplicationArguments args) {
		try {
			(runner).run(args.getSourceArgs());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to execute CommandLineRunner", ex);
		}
	}

	private void handleRunFailure(ConfigurableApplicationContext context, Throwable exception,
			Collection<SpringBootExceptionReporter> exceptionReporters, SpringApplicationRunListeners listeners) {
		try {
			try {
				handleExitCode(context, exception);
				if (listeners != null) {
					// 🚥发布启动失败事件
					// 例如：在启动失败的时候，做一些资源回收
					listeners.failed(context, exception);
				}
			}
			finally {
				reportFailure(exceptionReporters, exception);
				if (context != null) {
					context.close();
				}
			}
		}
		catch (Exception ex) {
			logger.warn("Unable to close ApplicationContext", ex);
		}
		ReflectionUtils.rethrowRuntimeException(exception);
	}

	private void reportFailure(Collection<SpringBootExceptionReporter> exceptionReporters, Throwable failure) {
		try {
			for (SpringBootExceptionReporter reporter : exceptionReporters) {
				if (reporter.reportException(failure)) {
					registerLoggedException(failure);
					return;
				}
			}
		}
		catch (Throwable ex) {
			// Continue with normal handling of the original failure
		}
		if (logger.isErrorEnabled()) {
			logger.error("Application run failed", failure);
			registerLoggedException(failure);
		}
	}

	/**
	 * Register that the given exception has been logged. By default, if the running in
	 * the main thread, this method will suppress additional printing of the stacktrace.
	 * @param exception the exception that was logged
	 */
	protected void registerLoggedException(Throwable exception) {
		SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
		if (handler != null) {
			handler.registerLoggedException(exception);
		}
	}

	private void handleExitCode(ConfigurableApplicationContext context, Throwable exception) {
		int exitCode = getExitCodeFromException(context, exception);
		if (exitCode != 0) {
			if (context != null) {
				context.publishEvent(new ExitCodeEvent(context, exitCode));
			}
			SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
			if (handler != null) {
				handler.registerExitCode(exitCode);
			}
		}
	}

	private int getExitCodeFromException(ConfigurableApplicationContext context, Throwable exception) {
		int exitCode = getExitCodeFromMappedException(context, exception);
		if (exitCode == 0) {
			exitCode = getExitCodeFromExitCodeGeneratorException(exception);
		}
		return exitCode;
	}

	private int getExitCodeFromMappedException(ConfigurableApplicationContext context, Throwable exception) {
		if (context == null || !context.isActive()) {
			return 0;
		}
		ExitCodeGenerators generators = new ExitCodeGenerators();
		Collection<ExitCodeExceptionMapper> beans = context.getBeansOfType(ExitCodeExceptionMapper.class).values();
		generators.addAll(exception, beans);
		return generators.getExitCode();
	}

	private int getExitCodeFromExitCodeGeneratorException(Throwable exception) {
		if (exception == null) {
			return 0;
		}
		if (exception instanceof ExitCodeGenerator) {
			return ((ExitCodeGenerator) exception).getExitCode();
		}
		return getExitCodeFromExitCodeGeneratorException(exception.getCause());
	}

	SpringBootExceptionHandler getSpringBootExceptionHandler() {
		if (isMainThread(Thread.currentThread())) {
			return SpringBootExceptionHandler.forCurrentThread();
		}
		return null;
	}

	private boolean isMainThread(Thread currentThread) {
		return ("main".equals(currentThread.getName()) || "restartedMain".equals(currentThread.getName()))
				&& "main".equals(currentThread.getThreadGroup().getName());
	}

	/**
	 * Returns the main application class that has been deduced or explicitly configured.
	 * @return the main application class or {@code null}
	 */
	public Class<?> getMainApplicationClass()/* 没有地方调用 */{
		return this.mainApplicationClass;
	}

	/**
	 * Set a specific main application class that will be used as a log source and to
	 * obtain version information. By default the main application class will be deduced.
	 * Can be set to {@code null} if there is no explicit application class.
	 * @param mainApplicationClass the mainApplicationClass to set or {@code null}
	 */
	public void setMainApplicationClass(Class<?> mainApplicationClass) {
		this.mainApplicationClass = mainApplicationClass;
	}

	/**
	 * Returns the type of web application that is being run.
	 * @return the type of web application
	 * @since 2.0.0
	 */
	public WebApplicationType getWebApplicationType() {
		return this.webApplicationType;
	}

	/**
	 * Sets the type of web application to be run. If not explicitly set the type of web
	 * application will be deduced based on the classpath.
	 * @param webApplicationType the web application type
	 * @since 2.0.0
	 */
	public void setWebApplicationType(WebApplicationType webApplicationType) {
		Assert.notNull(webApplicationType, "WebApplicationType must not be null");
		this.webApplicationType = webApplicationType;
	}

	/**
	 * Sets if bean definition overriding, by registering a definition with the same name
	 * as an existing definition, should be allowed. Defaults to {@code false}.
	 * @param allowBeanDefinitionOverriding if overriding is allowed
	 * @since 2.1.0
	 * @see DefaultListableBeanFactory#setAllowBeanDefinitionOverriding(boolean)
	 */
	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	/**
	 * Sets if beans should be initialized lazily. Defaults to {@code false}.
	 * @param lazyInitialization if initialization should be lazy
	 * @since 2.2
	 * @see BeanDefinition#setLazyInit(boolean)
	 */
	public void setLazyInitialization(boolean lazyInitialization) {
		this.lazyInitialization = lazyInitialization;
	}

	/**
	 * Sets if the application is headless and should not instantiate AWT. Defaults to
	 * {@code true} to prevent java icons appearing.
	 * @param headless if the application is headless
	 */
	public void setHeadless(boolean headless) {
		this.headless = headless;
	}

	/**
	 * Sets if the created {@link ApplicationContext} should have a shutdown hook
	 * registered. Defaults to {@code true} to ensure that JVM shutdowns are handled
	 * gracefully.
	 * @param registerShutdownHook if the shutdown hook should be registered
	 */
	public void setRegisterShutdownHook(boolean registerShutdownHook) {
		this.registerShutdownHook = registerShutdownHook;
	}

	/**
	 * Sets the {@link Banner} instance which will be used to print the banner when no
	 * static banner file is provided.
	 * @param banner the Banner instance to use
	 */
	public void setBanner(Banner banner) {
		this.banner = banner;
	}

	/**
	 * Sets the mode used to display the banner when the application runs. Defaults to
	 * {@code Banner.Mode.CONSOLE}.
	 * @param bannerMode the mode used to display the banner
	 */
	public void setBannerMode(Banner.Mode bannerMode) {
		this.bannerMode = bannerMode;
	}

	/**
	 * Sets if the application information should be logged when the application starts.
	 * Defaults to {@code true}.
	 * @param logStartupInfo if startup info should be logged.
	 */
	public void setLogStartupInfo(boolean logStartupInfo) {
		this.logStartupInfo = logStartupInfo;
	}

	/**
	 * Sets if a {@link CommandLinePropertySource} should be added to the application
	 * context in order to expose arguments. Defaults to {@code true}.
	 * @param addCommandLineProperties if command line arguments should be exposed
	 */
	public void setAddCommandLineProperties(boolean addCommandLineProperties) {
		this.addCommandLineProperties = addCommandLineProperties;
	}

	/**
	 * Sets if the {@link ApplicationConversionService} should be added to the application
	 * context's {@link Environment}.
	 * @param addConversionService if the application conversion service should be added
	 * @since 2.1.0
	 */
	public void setAddConversionService(boolean addConversionService) {
		this.addConversionService = addConversionService;
	}

	/**
	 * Set default environment properties which will be used in addition to those in the
	 * existing {@link Environment}.
	 * @param defaultProperties the additional properties to set
	 */
	public void setDefaultProperties(Map<String, Object> defaultProperties) {
		this.defaultProperties = defaultProperties;
	}

	/**
	 * Convenient alternative to {@link #setDefaultProperties(Map)}.
	 * @param defaultProperties some {@link Properties}
	 */
	public void setDefaultProperties(Properties defaultProperties) {
		this.defaultProperties = new HashMap<>();
		for (Object key : Collections.list(defaultProperties.propertyNames())) {
			this.defaultProperties.put((String) key, defaultProperties.get(key));
		}
	}

	/**
	 * Set additional profile values to use (on top of those set in system or command line
	 * properties).
	 * @param profiles the additional profiles to set
	 */
	public void setAdditionalProfiles(String... profiles) {
		this.additionalProfiles = new LinkedHashSet<>(Arrays.asList(profiles));
	}

	/**
	 * Sets the bean name generator that should be used when generating bean names.
	 * @param beanNameGenerator the bean name generator
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator = beanNameGenerator;
	}

	/**
	 * Sets the underlying environment that should be used with the created application
	 * context.
	 * @param environment the environment
	 */
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.isCustomEnvironment = true;
		this.environment = environment;
	}

	/**
	 * Add additional items to the primary sources that will be added to an
	 * ApplicationContext when {@link #run(String...)} is called.
	 * <p>
	 * The sources here are added to those that were set in the constructor. Most users
	 * should consider using {@link #getSources()}/{@link #setSources(Set)} rather than
	 * calling this method.
	 * @param additionalPrimarySources the additional primary sources to add
	 * @see #SpringApplication(Class...)
	 * @see #getSources()
	 * @see #setSources(Set)
	 * @see #getAllSources()
	 */
	public void addPrimarySources(Collection<Class<?>> additionalPrimarySources) {
		this.primarySources.addAll(additionalPrimarySources);
	}

	/**
	 * Returns a mutable set of the sources that will be added to an ApplicationContext
	 * when {@link #run(String...)} is called.
	 * <p>
	 * Sources set here will be used in addition to any primary sources set in the
	 * constructor.
	 * @return the application sources.
	 * @see #SpringApplication(Class...)
	 * @see #getAllSources()
	 */
	public Set<String> getSources() {
		return this.sources;
	}

	/**
	 * Set additional sources that will be used to create an ApplicationContext. A source
	 * can be: a class name, package name, or an XML resource location.
	 *
	 * 设置将用于创建 ApplicationContext 的其他源。源可以是：类名、包名或 XML 资源位置。
	 *
	 * <p>
	 * Sources set here will be used in addition to any primary sources set in the
	 * constructor.
	 * @param sources the application sources to set
	 * @see #SpringApplication(Class...)
	 * @see #getAllSources()
	 */
	public void setSources(Set<String> sources) {
		Assert.notNull(sources, "Sources must not be null");
		this.sources = new LinkedHashSet<>(sources);
	}

	/**
	 * 获取SpringApplication.run(class...)中传入的类
	 *
	 * Return an immutable set of all the sources that will be added to an
	 * ApplicationContext when {@link #run(String...)} is called. This method combines any
	 * primary sources specified in the constructor with any additional ones that have
	 * been {@link #setSources(Set) explicitly set}.
	 *
	 * 当调用 {@link run(String...)} 时，返回将添加到 ApplicationContext 的所有源的不可变集合。
	 * 此方法将构造函数中指定的任何主要来源与已{@link setSources(Set) 显式设置} 的任何其他来源结合起来。
	 *
	 * @return an immutable set of all sources
	 */
	public Set<Object> getAllSources() {
		Set<Object> allSources = new LinkedHashSet<>();

		// ⚠️SpringApplication.run(class...)中传入的类
		if (!CollectionUtils.isEmpty(this.primarySources)) {
			allSources.addAll(this.primarySources);
		}

		// 通过ApplicationContext#setSource()添加的类（一般为null）
		if (!CollectionUtils.isEmpty(this.sources)) {
			allSources.addAll(this.sources);
		}

		return Collections.unmodifiableSet(allSources);
	}

	/**
	 * Sets the {@link ResourceLoader} that should be used when loading resources.
	 * @param resourceLoader the resource loader
	 */
	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
	}

	/**
	 * Sets the type of Spring {@link ApplicationContext} that will be created. If not
	 * specified defaults to {@link #DEFAULT_SERVLET_WEB_CONTEXT_CLASS} for web based
	 * applications or {@link AnnotationConfigApplicationContext} for non web based
	 * applications.
	 * @param applicationContextClass the context class to set
	 */
	public void setApplicationContextClass(Class<? extends ConfigurableApplicationContext> applicationContextClass) {
		this.applicationContextClass = applicationContextClass;
		this.webApplicationType = WebApplicationType.deduceFromApplicationContext(applicationContextClass);
	}

	/**
	 * Sets the {@link ApplicationContextInitializer} that will be applied to the Spring
	 * {@link ApplicationContext}.
	 * @param initializers the initializers to set
	 */
	public void setInitializers(Collection<? extends ApplicationContextInitializer<?>> initializers) {
		this.initializers = new ArrayList<>(initializers);
	}

	/**
	 * Add {@link ApplicationContextInitializer}s to be applied to the Spring
	 * {@link ApplicationContext}.
	 * @param initializers the initializers to add
	 */
	public void addInitializers(ApplicationContextInitializer<?>... initializers) {
		this.initializers.addAll(Arrays.asList(initializers));
	}

	/**
	 * 获取spring.factories文件中的"ApplicationContextInitializer(初始化器)"
	 *
	 * Returns read-only ordered Set of the {@link ApplicationContextInitializer}s that
	 * will be applied to the Spring {@link ApplicationContext}.
	 * @return the initializers
	 */
	public Set<ApplicationContextInitializer<?>> getInitializers() {
		// 1、this.initializers：spring.factories文件中的"ApplicationContextInitializer(初始化器)"
		// 2、asUnmodifiableOrderedSet()：排序
		return asUnmodifiableOrderedSet/* 作为不可修改的有序集 */(this.initializers);
	}

	/**
	 * Sets the {@link ApplicationListener}s that will be applied to the SpringApplication
	 * and registered with the {@link ApplicationContext}.
	 * @param listeners the listeners to set
	 */
	public void setListeners(Collection<? extends ApplicationListener<?>> listeners) {
		this.listeners = new ArrayList<>(listeners);
	}

	/**
	 * Add {@link ApplicationListener}s to be applied to the SpringApplication and
	 * registered with the {@link ApplicationContext}.
	 * @param listeners the listeners to add
	 */
	public void addListeners(ApplicationListener<?>... listeners) {
		this.listeners.addAll(Arrays.asList(listeners));
	}

	/**
	 * 获取所有的监听器
	 *
	 * 题外：这些监听器是在SpringApplication.run() ——> new SpringApplication()时进行的初始化
	 *
	 * Returns read-only ordered Set of the {@link ApplicationListener}s that will be
	 * applied to the SpringApplication and registered with the {@link ApplicationContext}
	 * .
	 * @return the listeners
	 */
	public Set<ApplicationListener<?>> getListeners() {
		return asUnmodifiableOrderedSet(this.listeners);
	}

	/**
	 * 添加相关的注释--波波烤鸭  获取资料技术支持加V：boge_java 1
	 * Static helper that can be used to run a {@link SpringApplication} from the
	 * specified source using default settings. 666
	 * 再次修改了代码
	 * @param primarySource the primary source to load
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return the running {@link ApplicationContext}
	 */
	public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
		// 调用重载的run方法
		return run(new Class<?>[] { primarySource }/* 将传递的Class对象封装为了一个数组 */, args);
	}

	/**
	 * Static helper that can be used to run a {@link SpringApplication} from the
	 * specified sources using default settings and user supplied arguments.
	 * @param primarySources the primary sources to load
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return the running {@link ApplicationContext}
	 */
	public static ConfigurableApplicationContext run(Class<?>[] primarySources, String[] args) {
		// 创建了一个SpringApplication对象，并调用其run方法
		// 1.先看下构造方法中的逻辑
		// 2.然后再看run方法的逻辑
		return new SpringApplication(primarySources).run(args);
	}

	/**
	 * A basic main that can be used to launch an application. This method is useful when
	 * application sources are defined via a {@literal --spring.main.sources} command line
	 * argument.
	 * <p>
	 * Most developers will want to define their own main method and call the
	 * {@link #run(Class, String...) run} method instead.
	 * @param args command line arguments
	 * @throws Exception if the application cannot be started
	 * @see SpringApplication#run(Class[], String[])
	 * @see SpringApplication#run(Class, String...)
	 */
	public static void main(String[] args) throws Exception {
		SpringApplication.run(new Class<?>[0], args);
	}

	/**
	 * Static helper that can be used to exit a {@link SpringApplication} and obtain a
	 * code indicating success (0) or otherwise. Does not throw exceptions but should
	 * print stack traces of any encountered. Applies the specified
	 * {@link ExitCodeGenerator} in addition to any Spring beans that implement
	 * {@link ExitCodeGenerator}. In the case of multiple exit codes the highest value
	 * will be used (or if all values are negative, the lowest value will be used)
	 * @param context the context to close if possible
	 * @param exitCodeGenerators exist code generators
	 * @return the outcome (0 if successful)
	 */
	public static int exit(ApplicationContext context, ExitCodeGenerator... exitCodeGenerators) {
		Assert.notNull(context, "Context must not be null");
		int exitCode = 0;
		try {
			try {
				ExitCodeGenerators generators = new ExitCodeGenerators();
				Collection<ExitCodeGenerator> beans = context.getBeansOfType(ExitCodeGenerator.class).values();
				generators.addAll(exitCodeGenerators);
				generators.addAll(beans);
				exitCode = generators.getExitCode();
				if (exitCode != 0) {
					context.publishEvent(new ExitCodeEvent(context, exitCode));
				}
			}
			finally {
				close(context);
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			exitCode = (exitCode != 0) ? exitCode : 1;
		}
		return exitCode;
	}

	private static void close(ApplicationContext context) {
		if (context instanceof ConfigurableApplicationContext) {
			ConfigurableApplicationContext closable = (ConfigurableApplicationContext) context;
			closable.close();
		}
	}

	/**
	 * 对elements进行排序
	 */
	private static <E> Set<E> asUnmodifiableOrderedSet(Collection<E> elements) {
		List<E> list = new ArrayList<>(elements);
		// 排序
		list.sort(AnnotationAwareOrderComparator.INSTANCE/* AnnotationAwareOrderComparator */);
		return new LinkedHashSet<>(list);
	}

}
