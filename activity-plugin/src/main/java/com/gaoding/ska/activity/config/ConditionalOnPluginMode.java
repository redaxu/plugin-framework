package com.gaoding.ska.activity.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.pf4j.PluginClassLoader;

/**
 * 条件注解：仅在插件模式下生效
 * 判断当前类加载器是否为 PluginClassLoader，如果是则说明运行在插件环境中
 *
 * @author baiye
 * @since 2025/01/02
 */
public class ConditionalOnPluginMode implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ClassLoader classLoader = context.getClassLoader();
        // 如果类加载器是 PluginClassLoader 或其子类，说明运行在插件环境中
        return classLoader instanceof PluginClassLoader;
    }
}

