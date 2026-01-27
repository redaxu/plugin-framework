package com.gaoding.ska.activity.config;

import org.laxture.sbp.spring.boot.SbpAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * 活动插件配置类
 * 
 * 作用：
 * 1. 扫描插件中的组件（Controller、Service、Repository等）
 * 2. 作为SpringBootstrap的配置类参数
 * 3. Filter 在插件中定义，但由主应用注册到主容器（类似 Controller 的处理方式）
 * 4. 设置插件的 ClassLoader 为当前线程的上下文 ClassLoader，确保 ClassUtils.getDefaultClassLoader() 返回插件的 ClassLoader
 * 
 * 注意：仅在插件模式下生效（通过 SpringBootstrap 显式使用），独立运行时不会被注册
 * 条件：只有当当前类加载器是 PluginClassLoader 时才生效（即运行在插件环境中）
 *
 * @author baiye
 * @since 2024/12/19
 */
@Configuration
@OnPluginMode
@EnableAutoConfiguration(exclude = {
        SbpAutoConfiguration.class
})
@ComponentScan(basePackages = "com.gaoding.ska.activity")
public class ActivityPluginConfiguration {

    // Filter 通过 @Component 注解在插件中定义（ActivityPluginFilter）
    // Filter 的注册由主应用的 FilterConfiguration 负责，类似 Controller 的处理方式
    // 主应用会从插件上下文中获取 Filter Bean，然后注册到主应用的 ServletContext

    /**
     * 设置插件的 ClassLoader 为当前线程的上下文 ClassLoader
     * 确保 ClassUtils.getDefaultClassLoader() 方法返回插件的 ClassLoader 而不是主应用的 ClassLoader
     * 
     * 注意：此方法在配置类初始化时执行，此时插件的 ClassLoader 已经通过 ActivityPlugin.start() 方法设置
     * 这里再次设置以确保在插件上下文中执行的代码都能使用正确的 ClassLoader
     */
    @PostConstruct
    public void setPluginClassLoader() {
        ClassLoader pluginClassLoader = this.getClass().getClassLoader();
        Thread.currentThread().setContextClassLoader(pluginClassLoader);
    }
}

