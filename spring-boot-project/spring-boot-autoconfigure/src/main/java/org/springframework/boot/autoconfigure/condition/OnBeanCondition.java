/*
 * Copyright 2012-2020 the original author or authors.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotation.Adapt;
import org.springframework.core.annotation.MergedAnnotationCollectors;
import org.springframework.core.annotation.MergedAnnotationPredicates;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 *
 * 注意：OnBeanCondition与@ConditionalOnBean不是同一个东西。OnBeanCondition与OnClassCondition的效果差不多，也是看【配置类.ConditionalOnBean】所对应的全限定类名的类是否存在。
 *
 * {@link Condition} that checks for the presence or absence of specific beans.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Jakub Kubrynski
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @see ConditionalOnBean
 * @see ConditionalOnMissingBean
 * @see ConditionalOnSingleCandidate
 */
@Order(Ordered.LOWEST_PRECEDENCE)
class OnBeanCondition extends FilteringSpringBootCondition implements ConfigurationCondition {

	@Override
	public ConfigurationPhase getConfigurationPhase() {
		// 注册bean阶段
		return ConfigurationPhase.REGISTER_BEAN;
	}

	@Override
	protected final ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {

		// 有多少个配置类，就创建多大的ConditionOutcome[]。"ConditionOutcome[]"索引与"配置类数组索引"一一对应
		ConditionOutcome[] outcomes = new ConditionOutcome[autoConfigurationClasses.length];

		for (int i = 0; i < outcomes.length; i++) {
			// 获取配置类
			String autoConfigurationClass = autoConfigurationClasses[i];
			if (autoConfigurationClass != null) {
				/*

				1、从spring-autoconfigure-metadata.properties文件中，获取【配置类.ConditionalOnBean】对应的全限定类名。
				然后判断【配置类.ConditionalOnBean】对应的全限定类名，所对应的类，是否存在于系统当中：
				（1）只要有其中一个不存在，则返回一个不匹配的ConditionOutcome，代表不匹配
				（2）如果都存在，则返回null，代表匹配

				 */
				// 拼接【autoConfigurationClass.ConditionalOnBean】，然后获取其在spring-autoconfigure-metadata.properties文件中，对应的被加载的条件的全限定类名称
				// 题外：可以配置多个，以逗号分割
				Set<String> onBeanTypes = autoConfigurationMetadata.getSet(autoConfigurationClass, "ConditionalOnBean");
				// 判断【配置类.ConditionalOnBean】中的className是否存在，
				// 只要不存在【配置类.ConditionalOnBean】中的某一个className，则返回一个不匹配的ConditionOutcome，代表不匹配；
				// 如果【配置类.ConditionalOnBean】中的className都存在，则返回null
				outcomes[i] = getOutcome(onBeanTypes, ConditionalOnBean.class/* 这里无意义，只是为了获取日志信息 */);

				/*

				2、如果【配置类.ConditionalOnBean】对应的全限定类名，所对应的类，都存在于系统当中；
				则从spring-autoconfigure-metadata.properties文件中，获取【配置类.ConditionalOnSingleCandidate】对应的全限定类名。
				然后判断【配置类.ConditionalOnBean】对应的全限定类名，所对应的类，是否存在于系统当中：
				（1）只要有其中一个不存在，则返回一个不匹配的ConditionOutcome，代表不匹配
				（1）如果都存在，则返回null，代表匹配

				 */
				// 如果【配置类.ConditionalOnBean】中的className都存在，才会返回null
				if (outcomes[i] == null) {
					// 拼接【autoConfigurationClass.ConditionalOnSingleCandidate】，然后获取spring-autoconfigure-metadata.properties文件中，对应的被加载的条件的全限定类名称
					Set<String> onSingleCandidateTypes = autoConfigurationMetadata.getSet(autoConfigurationClass,
							"ConditionalOnSingleCandidate");
					// 判断【配置类.ConditionalOnSingleCandidate】中的className是否存在，
					// 只要不存在【配置类.ConditionalOnSingleCandidate】中的某一个className，则返回一个不匹配的ConditionOutcome，代表不匹配；
					// 如果【配置类.ConditionalOnSingleCandidate】中的className都存在，则返回null
					outcomes[i] = getOutcome(onSingleCandidateTypes, ConditionalOnSingleCandidate.class/* 这里无意义，只是为了获取日志信息 */);
				}
			}

		}

		return outcomes;
	}

	/**
	 * 判断【requiredBeanTypes】中的className是否存在，
	 *
	 * 只要不存在【requiredBeanTypes】中的某一个className，则返回一个不匹配的ConditionOutcome，代表不匹配；
	 * 如果【requiredBeanTypes】中的className都存在，则返回null
	 *
	 * @param requiredBeanTypes				必须存在的bean类型
	 *                                      例如：在spring-autoconfigure-metadata.properties文件中，【配置类.ConditionalOnBean】对应的被加载的条件的全限定类名称
	 *                                      例如：在spring-autoconfigure-metadata.properties文件中，【配置类.ConditionalOnSingleCandidate】对应的被加载的条件的全限定类名称
	 *
	 * @param annotation					条件注解，例如：@ConditionalOnBean
	 */
	private ConditionOutcome getOutcome(Set<String> requiredBeanTypes, Class<? extends Annotation> annotation) {
		/* 1、过滤出【配置类.ConditionalOnBean】中不存在的className */
		List<String> missing = filter(requiredBeanTypes, ClassNameFilter.MISSING, getBeanClassLoader());

		/* 2、有"【配置类.ConditionalOnBean】属性值中不存在的className"，则构建一个不匹配的ConditionOutcome的返回 */
		if (!missing.isEmpty()) {
			// 条件消息
			ConditionMessage message = ConditionMessage.forCondition(annotation)
					.didNotFind("required type", "required types").items(Style.QUOTE, missing);

			// 构建一个不匹配的ConditionOutcome
			return ConditionOutcome.noMatch(message);
		}

		/* 3、否则，返回null，代表匹配 */
		return null;
	}

	/**
	 * 获取@ConditionalOnBean、@ConditionalOnSingleCandidate、@ConditionalOnMissingBean的匹配结果，
	 * 大体逻辑就是：从beanFactory中获取全限定类名对应类型的beanName，然后看是否存在beanName来决定是否匹配。🚩也就是看beanFactory中是否存在全限定类名对应类型的beanName，来决定是否匹配。
	 *
	 * 注意：⚠️是从beanFactory中的beanDefinitionNames，获取全限定类名对应类型的beanName。
	 * >>> 因为，例如这是注册配置类bd，那么bean还未被实例化，所以无法从singletonObjects中获取的到；
	 * >>> 另外，beanDefinitionNames中的bd后续都要实例化成为bean，所以通过beanDefinitionNames就可以得知是否存在对应类型的bean
	 *
	 * @param context        用于条件判断时使用的上下文环境，一般是一个{@link ConditionEvaluator.ConditionContextImpl}对象，
	 * 						 里面包含了BeanDefinitionRegistry、ConfigurableListableBeanFactory、Environment等对，方便我们进行条件判断！
	 *
	 * @param metadata		 @Condition所在标注类的注解元数据
	 */
	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		ConditionMessage matchMessage = ConditionMessage.empty();

		MergedAnnotations annotations = metadata.getAnnotations();

		/*

		1、存在@ConditionalOnBean，则从beanFactory中获取对应类型的beanName，如果不存在就代表不匹配，创建一个不匹配的ConditionOutcome返回

		注意：⚠️@ConditionalOnBean不止类型这一项条件，只是平时我们用的最多的是类型这一项条件，所以就用这个举例

		*/
		// 存在@ConditionalOnBean
		if (annotations.isPresent(ConditionalOnBean.class)) {
			Spec<ConditionalOnBean> spec/* 规格 */ = new Spec<>(context, metadata, annotations, ConditionalOnBean.class);
			// 获取匹配结果
			MatchResult matchResult = getMatchingBeans/* 获取匹配的Bean */(context, spec);
			// 如果不是全部匹配，则创建一个不匹配的ConditionOutcome返回
			if (!matchResult.isAllMatched()) {
				String reason = createOnBeanNoMatchReason(matchResult);
				return ConditionOutcome.noMatch(spec.message().because(reason));
			}
			matchMessage = spec.message(matchMessage).found("bean", "beans").items(Style.QUOTE,
					matchResult.getNamesOfAllMatches());
		}

		/*

		2、存在@ConditionalOnSingleCandidate，则从beanFactory中获取对应类型的beanName，如果不存在就代表不匹配，创建一个不匹配的ConditionOutcome返回

		*/
		// 存在@ConditionalOnSingleCandidate
		if (metadata.isAnnotated(ConditionalOnSingleCandidate.class.getName())) {
			Spec<ConditionalOnSingleCandidate> spec = new SingleCandidateSpec(context, metadata, annotations);
			// 获取匹配结果
			MatchResult matchResult = getMatchingBeans(context, spec);
			// 如果不是全部匹配，则创建一个不匹配的ConditionOutcome返回
			if (!matchResult.isAllMatched()) {
				return ConditionOutcome.noMatch(spec.message().didNotFind("any beans").atAll());
			}
			else if (!hasSingleAutowireCandidate(context.getBeanFactory(), matchResult.getNamesOfAllMatches(),
					spec.getStrategy() == SearchStrategy.ALL)) {
				return ConditionOutcome.noMatch(spec.message().didNotFind("a primary bean from beans")
						.items(Style.QUOTE, matchResult.getNamesOfAllMatches()));
			}
			matchMessage = spec.message(matchMessage).found("a primary bean from beans").items(Style.QUOTE,
					matchResult.getNamesOfAllMatches());
		}

		/*

		3、存在@ConditionalOnMissingBean，则从beanFactory中获取对应类型的beanName，如果存在就代表不匹配，创建一个不匹配的ConditionOutcome返回

		注意：⚠️@ConditionalOnMissingBean不止类型这一项条件，只是平时我们用的最多的是类型这一项条件，所以就用这个举例

		*/
		// 存在@ConditionalOnMissingBean
		if (metadata.isAnnotated(ConditionalOnMissingBean.class.getName())) {
			Spec<ConditionalOnMissingBean> spec = new Spec<>(context, metadata, annotations,
					ConditionalOnMissingBean.class);
			// 获取匹配结果
			MatchResult matchResult = getMatchingBeans(context, spec);
			// 所有的都匹配，则创建一个不匹配的ConditionOutcome返回
			// 题外：@ConditionalOnMissingBean要的是所有的都不匹配，所以如果所有的都匹配，就创建一个不匹配的ConditionOutcome返回
			if (matchResult.isAnyMatched()) {
				String reason = createOnMissingBeanNoMatchReason(matchResult);
				return ConditionOutcome.noMatch(spec.message().because(reason));
			}
			matchMessage = spec.message(matchMessage).didNotFind("any beans").atAll();
		}

		/*

		4、上面的@ConditionalOnBean、@ConditionalOnSingleCandidate、@ConditionalOnMissingBean，满足任意一个条件，或者几个条件都满足，那么就直接创建一个匹配的ConditionOutcome（条件结果）返回

		注意：⚠️没有配置@ConditionalOnBean/@ConditionalOnSingleCandidate/@ConditionalOnMissingBean，那么是不可能进入到当前方法的！

		*/
		// 创建一个匹配的ConditionOutcome返回
		return ConditionOutcome.match(matchMessage);
	}

	protected final MatchResult getMatchingBeans/* 获取匹配的Bean */(ConditionContext context, Spec<?> spec) {

		ClassLoader classLoader = context.getClassLoader();
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		// 如果搜索范围不是"只在当前上下文搜索"，则要考虑"上下文层次结构"，否则不用考虑
		boolean considerHierarchy/* 考虑层次结构 */ = spec.getStrategy() != SearchStrategy.CURRENT;
		// 注解中的parameterizedContainer属性值
		Set<Class<?>> parameterizedContainers = spec.getParameterizedContainers();

		// 如果搜索范围是"只在所有父上下文中搜索"
		if (spec.getStrategy() == SearchStrategy.ANCESTORS) {
			BeanFactory parent = beanFactory.getParentBeanFactory();
			Assert.isInstanceOf(ConfigurableListableBeanFactory.class, parent,
					"Unable to use SearchStrategy.ANCESTORS");
			beanFactory = (ConfigurableListableBeanFactory) parent;
		}

		// 存放匹配结果
		MatchResult result = new MatchResult();

		// 从容器中，获取要忽略的类型的beanName
		Set<String> beansIgnoredByType = getNamesOfBeansIgnoredByType/* 获取按类型忽略的Bean名称 */(classLoader, beanFactory, considerHierarchy,
				spec.getIgnoredTypes(), parameterizedContainers);

		// 遍历全限定类名
		for (String type : spec.getTypes()/* 注解中value、type属性名对应的属性值 */) {
			// 从容器中，获取对应类型的beanName
			Collection<String> typeMatches = getBeanNamesForType(classLoader, considerHierarchy, beanFactory, type,
					parameterizedContainers);
			// 移除需要忽略的
			typeMatches.removeAll(beansIgnoredByType);

			// 如果容器中不存在对应类型的beanName，则代表条件不匹配，于是记录不匹配的类型
			if (typeMatches.isEmpty()) {
				result.recordUnmatchedType/* 记录不匹配的类型 */(type);
			}
			// 如果容器中存在对应类型的beanName，则代表条件匹配，于是记录匹配的类型
			else {
				result.recordMatchedType/* 记录匹配类型 */(type, typeMatches);
			}
		}

		// 遍历注解的全限定类名
		for (String annotation : spec.getAnnotations()) {
			Set<String> annotationMatches = getBeanNamesForAnnotation(classLoader, beanFactory, annotation,
					considerHierarchy);
			annotationMatches.removeAll(beansIgnoredByType);
			if (annotationMatches.isEmpty()) {
				result.recordUnmatchedAnnotation/* 记录不匹配的注解 */(annotation);
			}
			else {
				result.recordMatchedAnnotation/* 记录匹配的注解 */(annotation, annotationMatches);
			}
		}

		// 遍历beanName
		for (String beanName : spec.getNames()) {
			if (!beansIgnoredByType.contains(beanName) && containsBean(beanFactory, beanName, considerHierarchy)) {
				result.recordMatchedName/* 记录匹配名称 */(beanName);
			}
			else {
				result.recordUnmatchedName/* 记录不匹配的名称 */(beanName);
			}
		}

		return result;
	}

	private Set<String> getNamesOfBeansIgnoredByType(ClassLoader classLoader, ListableBeanFactory beanFactory,
			boolean considerHierarchy, Set<String> ignoredTypes, Set<Class<?>> parameterizedContainers) {
		Set<String> result = null;

		for (String ignoredType : ignoredTypes) {
			// 从容器中，获取对应类型的beanName
			Collection<String> ignoredNames = getBeanNamesForType(classLoader, considerHierarchy, beanFactory,
					ignoredType, parameterizedContainers);

			result = addAll(result, ignoredNames);
		}

		return (result != null) ? result : Collections.emptySet();
	}

	/**
	 * 从容器中，获取对应类型的beanName
	 *
	 * @param classLoader
	 * @param considerHierarchy
	 * @param beanFactory
	 * @param type
	 * @param parameterizedContainers
	 * @return
	 * @throws LinkageError
	 */
	private Set<String> getBeanNamesForType(ClassLoader classLoader, boolean considerHierarchy,
			ListableBeanFactory beanFactory, String type, Set<Class<?>> parameterizedContainers) throws LinkageError {
		try {
			// 从容器中，获取对应类型的beanName
			return getBeanNamesForType(beanFactory, considerHierarchy, resolve(type, classLoader)/* 获取全限定类名对应的Class对象 */,
					parameterizedContainers);
		}
		catch (ClassNotFoundException | NoClassDefFoundError ex) {
			return Collections.emptySet();
		}
	}

	/**
	 * 从容器中，获取对应类型的beanName
	 *
	 * @param beanFactory
	 * @param considerHierarchy
	 * @param type
	 * @param parameterizedContainers
	 * @return
	 */
	private Set<String> getBeanNamesForType(ListableBeanFactory beanFactory, boolean considerHierarchy, Class<?> type,
			Set<Class<?>> parameterizedContainers) {
		// 从容器中，获取对应类型的beanName
		Set<String> result = collectBeanNamesForType(beanFactory, considerHierarchy, type, parameterizedContainers,
				null);
		return (result != null) ? result : Collections.emptySet();
	}

	/**
	 * 从容器中，获取对应类型的beanName
	 *
	 * 注意：⚠️现在还没有初始化bean呢，所以是从beanDefinitionNames中获取
	 *
	 * @param beanFactory
	 * @param considerHierarchy
	 * @param type
	 * @param parameterizedContainers
	 * @param result
	 * @return
	 */
	private Set<String> collectBeanNamesForType/* 收集类型的Bean名称 */(ListableBeanFactory beanFactory, boolean considerHierarchy,
			Class<?> type, Set<Class<?>> parameterizedContainers, Set<String> result) {

		result = addAll(result, /* ⚠️ */beanFactory.getBeanNamesForType(type, true, false)/* 从beanFactory中，️通过类型获取beanName */);

		for (Class<?> container : parameterizedContainers) {
			ResolvableType generic = ResolvableType.forClassWithGenerics(container, type);
			result = addAll(result, /* ⚠️ */beanFactory.getBeanNamesForType(generic, true, false)/* 从beanFactory中，️通过类型获取beanName */);
		}

		if (considerHierarchy && beanFactory instanceof HierarchicalBeanFactory) {
			// 获取父容器
			BeanFactory parent = ((HierarchicalBeanFactory) beanFactory).getParentBeanFactory();
			if (parent instanceof ListableBeanFactory) {
				// 从父容器中，️通过类型获取beanName
				// 题外：递归
				result = collectBeanNamesForType((ListableBeanFactory) parent, considerHierarchy, type,
						parameterizedContainers, result);
			}
		}

		return result;
	}

	private Set<String> getBeanNamesForAnnotation(ClassLoader classLoader, ConfigurableListableBeanFactory beanFactory,
			String type, boolean considerHierarchy) throws LinkageError {
		Set<String> result = null;
		try {
			result = collectBeanNamesForAnnotation(beanFactory, resolveAnnotationType(classLoader, type),
					considerHierarchy, result);
		}
		catch (ClassNotFoundException ex) {
			// Continue
		}
		return (result != null) ? result : Collections.emptySet();
	}

	@SuppressWarnings("unchecked")
	private Class<? extends Annotation> resolveAnnotationType(ClassLoader classLoader, String type)
			throws ClassNotFoundException {
		return (Class<? extends Annotation>) resolve(type, classLoader);
	}

	private Set<String> collectBeanNamesForAnnotation(ListableBeanFactory beanFactory,
			Class<? extends Annotation> annotationType, boolean considerHierarchy, Set<String> result) {
		result = addAll(result, beanFactory.getBeanNamesForAnnotation(annotationType));
		if (considerHierarchy) {
			BeanFactory parent = ((HierarchicalBeanFactory) beanFactory).getParentBeanFactory();
			if (parent instanceof ListableBeanFactory) {
				result = collectBeanNamesForAnnotation((ListableBeanFactory) parent, annotationType, considerHierarchy,
						result);
			}
		}
		return result;
	}

	private boolean containsBean(ConfigurableListableBeanFactory beanFactory, String beanName,
			boolean considerHierarchy) {
		if (considerHierarchy) {
			return beanFactory.containsBean(beanName);
		}
		return beanFactory.containsLocalBean(beanName);
	}

	private String createOnBeanNoMatchReason(MatchResult matchResult) {
		StringBuilder reason = new StringBuilder();
		appendMessageForNoMatches(reason, matchResult.getUnmatchedAnnotations(), "annotated with");
		appendMessageForNoMatches(reason, matchResult.getUnmatchedTypes(), "of type");
		appendMessageForNoMatches(reason, matchResult.getUnmatchedNames(), "named");
		return reason.toString();
	}

	private void appendMessageForNoMatches(StringBuilder reason, Collection<String> unmatched, String description) {
		if (!unmatched.isEmpty()) {
			if (reason.length() > 0) {
				reason.append(" and ");
			}
			reason.append("did not find any beans ");
			reason.append(description);
			reason.append(" ");
			reason.append(StringUtils.collectionToDelimitedString(unmatched, ", "));
		}
	}

	private String createOnMissingBeanNoMatchReason(MatchResult matchResult) {
		StringBuilder reason = new StringBuilder();
		appendMessageForMatches(reason, matchResult.getMatchedAnnotations(), "annotated with");
		appendMessageForMatches(reason, matchResult.getMatchedTypes(), "of type");
		if (!matchResult.getMatchedNames().isEmpty()) {
			if (reason.length() > 0) {
				reason.append(" and ");
			}
			reason.append("found beans named ");
			reason.append(StringUtils.collectionToDelimitedString(matchResult.getMatchedNames(), ", "));
		}
		return reason.toString();
	}

	private void appendMessageForMatches(StringBuilder reason, Map<String, Collection<String>> matches,
			String description) {
		if (!matches.isEmpty()) {
			matches.forEach((key, value) -> {
				if (reason.length() > 0) {
					reason.append(" and ");
				}
				reason.append("found beans ");
				reason.append(description);
				reason.append(" '");
				reason.append(key);
				reason.append("' ");
				reason.append(StringUtils.collectionToDelimitedString(value, ", "));
			});
		}
	}

	private boolean hasSingleAutowireCandidate(ConfigurableListableBeanFactory beanFactory, Set<String> beanNames,
			boolean considerHierarchy) {
		return (beanNames.size() == 1 || getPrimaryBeans(beanFactory, beanNames, considerHierarchy).size() == 1);
	}

	private List<String> getPrimaryBeans(ConfigurableListableBeanFactory beanFactory, Set<String> beanNames,
			boolean considerHierarchy) {
		List<String> primaryBeans = new ArrayList<>();
		for (String beanName : beanNames) {
			BeanDefinition beanDefinition = findBeanDefinition(beanFactory, beanName, considerHierarchy);
			if (beanDefinition != null && beanDefinition.isPrimary()) {
				primaryBeans.add(beanName);
			}
		}
		return primaryBeans;
	}

	private BeanDefinition findBeanDefinition(ConfigurableListableBeanFactory beanFactory, String beanName,
			boolean considerHierarchy) {
		if (beanFactory.containsBeanDefinition(beanName)) {
			return beanFactory.getBeanDefinition(beanName);
		}
		if (considerHierarchy && beanFactory.getParentBeanFactory() instanceof ConfigurableListableBeanFactory) {
			return findBeanDefinition(((ConfigurableListableBeanFactory) beanFactory.getParentBeanFactory()), beanName,
					considerHierarchy);
		}
		return null;
	}

	private static Set<String> addAll(Set<String> result, Collection<String> additional) {
		if (CollectionUtils.isEmpty(additional)) {
			return result;
		}
		result = (result != null) ? result : new LinkedHashSet<>();
		result.addAll(additional);
		return result;
	}

	private static Set<String> addAll(Set<String> result, String[] additional) {
		if (ObjectUtils.isEmpty(additional)) {
			return result;
		}
		result = (result != null) ? result : new LinkedHashSet<>();
		Collections.addAll(result, additional);
		return result;
	}

	/**
	 * A search specification extracted from the underlying annotation.
	 */
	private static class Spec<A extends Annotation> {

		private final ClassLoader classLoader;

		// 注解类型
		private final Class<? extends Annotation> annotationType;

		private final Set<String> names;
		// 注解中value、type属性名对应的属性值
		private final Set<String> types;

		private final Set<String> annotations;

		private final Set<String> ignoredTypes;

		private final Set<Class<?>> parameterizedContainers;
		// 搜索范围
		private final SearchStrategy strategy;

		/**
		 *
		 * @param context        		用于条件判断时使用的上下文环境，一般是一个{@link ConditionEvaluator.ConditionContextImpl}对象，
		 * 						 		里面包含了BeanDefinitionRegistry、ConfigurableListableBeanFactory、Environment等对，方便我们进行条件判断！
		 *
		 * @param metadata		 		@Condition所在标注类的注解元数据
		 *
		 * @param annotations			通过"@Condition所在标注类的注解元数据"，得到的所合并好的的注解元数据
		 *
		 * @param annotationType		注解类型
		 */
		Spec/* 规格 */(ConditionContext context, AnnotatedTypeMetadata metadata, MergedAnnotations annotations,
				Class<A> annotationType) {

			// 获取注解的属性映射
			MultiValueMap<String, Object> attributes = annotations.stream(annotationType/* 注解类型 */)
					.filter(MergedAnnotationPredicates.unique(MergedAnnotation::getMetaTypes))
					.collect(MergedAnnotationCollectors.toMultiValueMap(Adapt.CLASS_TO_STRING/* 类到字符串 */));

			// 获取注解的合并元数据
			MergedAnnotation<A> annotation = annotations.get(annotationType);

			this.classLoader = context.getClassLoader();
			this.annotationType = annotationType;

			this.names = extract(attributes, "name");
			this.annotations = extract(attributes, "annotation");
			// 题外：@ConditionalOnBean中没有这个属性
			this.ignoredTypes = extract(attributes, "ignored", "ignoredType");
			this.parameterizedContainers/* 参数化容器 */ = resolveWhenPossible(extract(attributes, "parameterizedContainer"));
			this.strategy = annotation.getValue("search", SearchStrategy.class).orElse(null);
			// 获取注解中value、type属性名对应的属性值
			Set<String> types = extractTypes(attributes);

			BeanTypeDeductionException deductionException/* 扣除例外 */ = null;
			// types是空 && names是空
			if (types.isEmpty() && this.names.isEmpty()) {
				try {
					// 推断beanType
					types = deducedBeanType(context, metadata);
				}
				catch (BeanTypeDeductionException ex) {
					// 记录异常
					deductionException = ex;
				}
			}
			this.types = types;

			validate(deductionException);
		}

		/**
		 * 获取注解中value、type属性名对应的属性值
		 *
		 * @param attributes				注解的属性映射
		 */
		protected Set<String> extractTypes(MultiValueMap<String, Object> attributes) {
			// 获取value、type属性名对应的属性值
			return extract(attributes, "value", "type");
		}

		/**
		 * 获取属性名对应的属性值
		 *
		 * @param attributes				注解的属性映射
		 * @param attributeNames			属性名
		 */
		private Set<String> extract(MultiValueMap<String, Object> attributes, String... attributeNames) {
			// 属性名为空，直接返回空集合
			if (attributes.isEmpty()) {
				return Collections.emptySet();
			}

			// 存放属性值
			Set<String> result = new LinkedHashSet<>();
			// 遍历属性名称
			for (String attributeName : attributeNames) {
				// 通过属性名称获取属性值，如果不存在属性值，那么默认的属性值为一个空集合
				List<Object> values = attributes.getOrDefault(attributeName, Collections.emptyList()/* 默认属性值 */);
				// 将属性值添加到result集合中
				for (Object value : values) {
					if (value instanceof String[]) {
						merge(result, (String[]) value);
					}
					else if (value instanceof String) {
						merge(result, (String) value);
					}
				}
			}

			// 返回属性值集合
			return result.isEmpty() ? Collections.emptySet() : result;
		}

		private void merge(Set<String> result, String... additional) {
			Collections.addAll(result, additional);
		}

		private Set<Class<?>> resolveWhenPossible(Set<String> classNames) {
			if (classNames.isEmpty()) {
				return Collections.emptySet();
			}
			Set<Class<?>> resolved = new LinkedHashSet<>(classNames.size());
			for (String className : classNames) {
				try {
					resolved.add(resolve(className, this.classLoader));
				}
				catch (ClassNotFoundException | NoClassDefFoundError ex) {
				}
			}
			return resolved;
		}

		protected void validate(BeanTypeDeductionException ex) {
			if (!hasAtLeastOneElement(this.types, this.names, this.annotations)) {
				String message = getAnnotationName() + " did not specify a bean using type, name or annotation";
				if (ex == null) {
					throw new IllegalStateException(message);
				}
				throw new IllegalStateException(message + " and the attempt to deduce the bean's type failed", ex);
			}
		}

		private boolean hasAtLeastOneElement(Set<?>... sets) {
			for (Set<?> set : sets) {
				if (!set.isEmpty()) {
					return true;
				}
			}
			return false;
		}

		protected final String getAnnotationName() {
			return "@" + ClassUtils.getShortName(this.annotationType);
		}

		/**
		 *
		 * @param context        		用于条件判断时使用的上下文环境，一般是一个{@link ConditionEvaluator.ConditionContextImpl}对象，
		 * 						 		里面包含了BeanDefinitionRegistry、ConfigurableListableBeanFactory、Environment等对，方便我们进行条件判断！
		 *
		 * @param metadata		 		@Condition所在标注类的注解元数据
		 */
		private Set<String> deducedBeanType(ConditionContext context, AnnotatedTypeMetadata metadata) {
			// 存在@Bean
			if (metadata instanceof MethodMetadata && metadata.isAnnotated(Bean.class.getName())) {
				return deducedBeanTypeForBeanMethod(context, (MethodMetadata) metadata);
			}

			return Collections.emptySet();
		}

		private Set<String> deducedBeanTypeForBeanMethod(ConditionContext context, MethodMetadata metadata) {
			try {
				Class<?> returnType = getReturnType(context, metadata);
				return Collections.singleton(returnType.getName());
			}
			catch (Throwable ex) {
				throw new BeanTypeDeductionException(metadata.getDeclaringClassName(), metadata.getMethodName(), ex);
			}
		}

		private Class<?> getReturnType(ConditionContext context, MethodMetadata metadata)
				throws ClassNotFoundException, LinkageError {
			// Safe to load at this point since we are in the REGISTER_BEAN phase
			// 上面翻译：由于我们处于REGISTER_BEAN(注册bean)阶段，因此此时可以安全加载

			ClassLoader classLoader = context.getClassLoader();
			Class<?> returnType = resolve(metadata.getReturnTypeName(), classLoader);
			if (isParameterizedContainer(returnType)) {
				returnType = getReturnTypeGeneric(metadata, classLoader);
			}
			return returnType;
		}

		private boolean isParameterizedContainer(Class<?> type) {
			for (Class<?> parameterizedContainer : this.parameterizedContainers) {
				if (parameterizedContainer.isAssignableFrom(type)) {
					return true;
				}
			}
			return false;
		}

		private Class<?> getReturnTypeGeneric(MethodMetadata metadata, ClassLoader classLoader)
				throws ClassNotFoundException, LinkageError {
			Class<?> declaringClass = resolve(metadata.getDeclaringClassName(), classLoader);
			Method beanMethod = findBeanMethod(declaringClass, metadata.getMethodName());
			return ResolvableType.forMethodReturnType(beanMethod).resolveGeneric();
		}

		private Method findBeanMethod(Class<?> declaringClass, String methodName) {
			Method method = ReflectionUtils.findMethod(declaringClass, methodName);
			if (isBeanMethod(method)) {
				return method;
			}
			Method[] candidates = ReflectionUtils.getAllDeclaredMethods(declaringClass);
			for (Method candidate : candidates) {
				if (candidate.getName().equals(methodName) && isBeanMethod(candidate)) {
					return candidate;
				}
			}
			throw new IllegalStateException("Unable to find bean method " + methodName);
		}

		private boolean isBeanMethod(Method method) {
			return method != null && MergedAnnotations.from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
					.isPresent(Bean.class);
		}

		private SearchStrategy getStrategy() {
			return (this.strategy != null) ? this.strategy : SearchStrategy.ALL;
		}

		Set<String> getNames() {
			return this.names;
		}
		// 注解中value、type属性名对应的属性值
		Set<String> getTypes() {
			return this.types;
		}

		Set<String> getAnnotations() {
			return this.annotations;
		}

		Set<String> getIgnoredTypes() {
			return this.ignoredTypes;
		}

		Set<Class<?>> getParameterizedContainers() {
			return this.parameterizedContainers;
		}

		ConditionMessage.Builder message() {
			return ConditionMessage.forCondition(this.annotationType, this);
		}

		ConditionMessage.Builder message(ConditionMessage message) {
			return message.andCondition(this.annotationType, this);
		}

		@Override
		public String toString() {
			boolean hasNames = !this.names.isEmpty();
			boolean hasTypes = !this.types.isEmpty();
			boolean hasIgnoredTypes = !this.ignoredTypes.isEmpty();
			StringBuilder string = new StringBuilder();
			string.append("(");
			if (hasNames) {
				string.append("names: ");
				string.append(StringUtils.collectionToCommaDelimitedString(this.names));
				string.append(hasTypes ? " " : "; ");
			}
			if (hasTypes) {
				string.append("types: ");
				string.append(StringUtils.collectionToCommaDelimitedString(this.types));
				string.append(hasIgnoredTypes ? " " : "; ");
			}
			if (hasIgnoredTypes) {
				string.append("ignored: ");
				string.append(StringUtils.collectionToCommaDelimitedString(this.ignoredTypes));
				string.append("; ");
			}
			string.append("SearchStrategy: ");
			string.append(this.strategy.toString().toLowerCase(Locale.ENGLISH));
			string.append(")");
			return string.toString();
		}

	}

	/**
	 * Specialized {@link Spec specification} for
	 * {@link ConditionalOnSingleCandidate @ConditionalOnSingleCandidate}.
	 */
	private static class SingleCandidateSpec extends Spec<ConditionalOnSingleCandidate> {

		private static final Collection<String> FILTERED_TYPES = Arrays.asList("", Object.class.getName());

		SingleCandidateSpec(ConditionContext context, AnnotatedTypeMetadata metadata, MergedAnnotations annotations) {
			super(context, metadata, annotations, ConditionalOnSingleCandidate.class);
		}

		@Override
		protected Set<String> extractTypes(MultiValueMap<String, Object> attributes) {
			Set<String> types = super.extractTypes(attributes);
			types.removeAll(FILTERED_TYPES);
			return types;
		}

		@Override
		protected void validate(BeanTypeDeductionException ex) {
			Assert.isTrue(getTypes().size() == 1,
					() -> getAnnotationName() + " annotations must specify only one type (got "
							+ StringUtils.collectionToCommaDelimitedString(getTypes()) + ")");
		}

	}

	/**
	 * Results collected during the condition evaluation.
	 */
	private static final class MatchResult {

		private final Map<String, Collection<String>> matchedAnnotations = new HashMap<>();

		private final List<String> matchedNames = new ArrayList<>();

		private final Map<String, Collection<String>> matchedTypes = new HashMap<>();

		private final List<String> unmatchedAnnotations = new ArrayList<>();

		private final List<String> unmatchedNames = new ArrayList<>();

		private final List<String> unmatchedTypes = new ArrayList<>();

		private final Set<String> namesOfAllMatches = new HashSet<>();

		private void recordMatchedName(String name) {
			this.matchedNames.add(name);
			this.namesOfAllMatches.add(name);
		}

		private void recordUnmatchedName(String name) {
			this.unmatchedNames.add(name);
		}

		private void recordMatchedAnnotation(String annotation, Collection<String> matchingNames) {
			this.matchedAnnotations.put(annotation, matchingNames);
			this.namesOfAllMatches.addAll(matchingNames);
		}

		private void recordUnmatchedAnnotation(String annotation) {
			this.unmatchedAnnotations.add(annotation);
		}

		private void recordMatchedType(String type, Collection<String> matchingNames) {
			this.matchedTypes.put(type, matchingNames);
			this.namesOfAllMatches.addAll(matchingNames);
		}

		private void recordUnmatchedType(String type) {
			this.unmatchedTypes.add(type);
		}

		boolean isAllMatched() {
			return this.unmatchedAnnotations.isEmpty() && this.unmatchedNames.isEmpty()
					&& this.unmatchedTypes.isEmpty();
		}

		boolean isAnyMatched() {
			return (!this.matchedAnnotations.isEmpty()) || (!this.matchedNames.isEmpty())
					|| (!this.matchedTypes.isEmpty());
		}

		Map<String, Collection<String>> getMatchedAnnotations() {
			return this.matchedAnnotations;
		}

		List<String> getMatchedNames() {
			return this.matchedNames;
		}

		Map<String, Collection<String>> getMatchedTypes() {
			return this.matchedTypes;
		}

		List<String> getUnmatchedAnnotations() {
			return this.unmatchedAnnotations;
		}

		List<String> getUnmatchedNames() {
			return this.unmatchedNames;
		}

		List<String> getUnmatchedTypes() {
			return this.unmatchedTypes;
		}

		Set<String> getNamesOfAllMatches() {
			return this.namesOfAllMatches;
		}

	}

	/**
	 * Exteption thrown when the bean type cannot be deduced.
	 */
	static final class BeanTypeDeductionException extends RuntimeException {

		private BeanTypeDeductionException(String className, String beanMethodName, Throwable cause) {
			super("Failed to deduce bean type for " + className + "." + beanMethodName, cause);
		}

	}

}
