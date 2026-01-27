package com.gaoding.ska.activity.config;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 条件注解：仅在插件模式下生效
 * 当类加载器是 PluginClassLoader 时，该注解标记的 Bean 才会被注册
 *
 * @author baiye
 * @since 2025/01/02
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnPluginMode.class)
public @interface OnPluginMode {
}

