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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

/**
 * {@link Conditional @Conditional} that only matches when the specified classes are on
 * the classpath.
 * <p>
 * A {@link #value()} can be safely specified on {@code @Configuration} classes as the
 * annotation metadata is parsed by using ASM before the class is loaded. Extra care is
 * required when placed on {@code @Bean} methods, consider isolating the condition in a
 * separate {@code Configuration} class, in particular if the return type of the method
 * matches the {@link #value target of the condition}.
 *
 * @author Phillip Webb
 * @since 1.0.0
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnClassCondition.class)
public @interface ConditionalOnClass {

	/**
	 * 标注当前系统需要存在的Class
	 *
	 * 示范：@ConditionalOnClass(DispatcherServlet.class)
	 *
	 * 题外：value是默认属性，并且name属性有默认属性值，所以在只书写value属性值时，可以省略value这个属性名称；
	 * >>> 如果name属性没有默认属性值，那么则需要当前注解只有value属性时，才能省略value这个属性名称；
	 * >>> 如果只有value属性，那么在只书写value属性值时，可以省略value这个属性名称；
	 * >>> 如果只有name属性，在书写name属性值时，即使只有name属性，是没法省略name这个属性名称的！
	 *
	 * The classes that must be present. Since this annotation is parsed by loading class
	 * bytecode, it is safe to specify classes here that may ultimately not be on the
	 * classpath, only if this annotation is directly on the affected component and
	 * <b>not</b> if this annotation is used as a composed, meta-annotation. In order to
	 * use this annotation as a meta-annotation, only use the {@link #name} attribute.
	 *
	 * 必须存在的类。由于这个注解是通过加载类字节码来解析的，所以在这里指定最终可能不在类路径上的类是安全的，
	 * 只有当这个注解，直接在受影响的组件上并且如果这个注解被用作
	 * <b>not<b>一个组合的元注解。为了将此注解用作元注解，请仅使用 {@link name} 属性。
	 *
	 * @return the classes that must be present
	 */
	Class<?>[] value() default {};

	/**
	 * 标注当前系统需要存在的类的全限定类名
	 *
	 * 示范：@ConditionalOnClass(name = "io.undertow.Undertow")
	 *
	 * The classes names that must be present. —— 必须存在的类名称
	 *
	 * @return the class names that must be present. —— 必须存在的类名
	 */
	String[] name() default {};

}
