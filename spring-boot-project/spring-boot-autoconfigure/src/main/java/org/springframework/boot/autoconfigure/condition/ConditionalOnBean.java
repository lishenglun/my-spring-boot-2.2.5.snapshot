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

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Conditional;

/**
 * {@link Conditional @Conditional} that only matches when beans meeting all the specified
 * requirements are already contained in the {@link BeanFactory}. All the requirements
 * must be met for the condition to match, but they do not have to be met by the same
 * bean.
 * <p>
 * When placed on a {@code @Bean} method, the bean class defaults to the return type of
 * the factory method:
 *
 * <pre class="code">
 * &#064;Configuration
 * public class MyAutoConfiguration {
 *
 *     &#064;ConditionalOnBean
 *     &#064;Bean
 *     public MyService myService() {
 *         ...
 *     }
 *
 * }</pre>
 * <p>
 * In the sample above the condition will match if a bean of type {@code MyService} is
 * already contained in the {@link BeanFactory}.
 * <p>
 * The condition can only match the bean definitions that have been processed by the
 * application context so far and, as such, it is strongly recommended to use this
 * condition on auto-configuration classes only. If a candidate bean may be created by
 * another auto-configuration, make sure that the one using this condition runs after.
 *
 * @author Phillip Webb
 * @since 1.0.0
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnBeanCondition.class)
public @interface ConditionalOnBean {

	/**
	 * bean的Class对象。只有当BeanFactory中存在所有指定Class的bean，条件匹配
	 *
	 * The class types of beans that should be checked. The condition matches when beans
	 * of all classes specified are contained in the {@link BeanFactory}.
	 *
	 * 应该检查的 bean 的class类型。当所有指定类的 bean 都包含在 {@link BeanFactory} 中时，条件匹配。
	 *
	 * @return the class types of beans to check
	 */
	Class<?>[] value() default {};

	/**
	 * bean的全限定类名。只有当BeanFactory中存在所有指定全限定类名的bean，条件匹配
	 *
	 * The class type names of beans that should be checked. The condition matches when
	 * beans of all classes specified are contained in the {@link BeanFactory}.
	 *
	 * 应检查的 bean 的class类型名称。当所有指定类的 bean 都包含在 {@link BeanFactory} 中时，条件匹配。
	 *
	 * @return the class type names of beans to check
	 */
	String[] type() default {};

	/**
	 * 注解类型
	 *
	 * The annotation type decorating a bean that should be checked. The condition matches
	 * when all of the annotations specified are defined on beans in the
	 * {@link BeanFactory}.
	 *
	 * 装饰应检查的 bean 的注解类型。当所有指定的注解都在 {@link BeanFactory} 中的 bean 上定义时，条件匹配。
	 *
	 * @return the class-level annotation types to check
	 */
	Class<? extends Annotation>[] annotation() default {};

	/**
	 * beanName。只有当BeanFactory中存在所有指定beanName时，条件匹配
	 *
	 * The names of beans to check. The condition matches when all of the bean names
	 * specified are contained in the {@link BeanFactory}.
	 *
	 * 要检查的bean的名称。当所有指定的bean名称都包含在 {@link BeanFactory} 中时，条件匹配。
	 *
	 * @return the names of beans to check
	 */
	String[] name() default {};

	/**
	 * 搜索范围。例如：只在当前上下文搜索，只在所有父上下文中搜索、搜索所有上下文（默认）
	 *
	 * Strategy to decide if the application context hierarchy (parent contexts) should be
	 * considered.
	 * 决定是否应考虑应用程序上下文层次结构（父上下文）的策略。
	 *
	 * @return the search strategy
	 */
	SearchStrategy search() default SearchStrategy.ALL;

	/**
	 * Additional classes that may contain the specified bean types within their generic
	 * parameters. For example, an annotation declaring {@code value=Name.class} and
	 * {@code parameterizedContainer=NameRegistration.class} would detect both
	 * {@code Name} and {@code NameRegistration<Name>}.
	 *
	 * 可能在其通用参数中包含指定 bean 类型的其他类。例如，声明 {@code value=Name.class}
	 * 和 {@code parameterizedContainer=NameRegistration.class} 的注释将同时检测 {@code Name} 和 {@code NameRegistration<Name>}。
	 *
	 * @return the container types
	 * @since 2.1.0
	 */
	Class<?>[] parameterizedContainer/* 参数化容器 */() default {};

}
