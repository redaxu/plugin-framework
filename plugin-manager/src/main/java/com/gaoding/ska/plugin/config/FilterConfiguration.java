package com.gaoding.ska.plugin.config;

import org.laxture.sbp.spring.boot.SbpPluginStartedEvent;
import org.laxture.spring.util.ApplicationContextProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.Filter;

/**
 * Filter 和 Interceptor 配置类
 * 负责注册代理 Filter、代理 Interceptor，以及管理插件的 Filter 和 Interceptor
 * 
 * 工作原理：
 * 1. 主应用启动时：
 *    - 注册 PluginDelegatingFilter（代理 Filter）到 ServletContext
 *    - 注册 PluginDelegatingInterceptor（代理 Interceptor）到 Spring MVC（在 WebMvcConfiguration 中）
 * 2. 插件启动时，监听 SbpPluginStartedEvent 事件：
 *    - 从插件上下文中获取所有 Filter Bean，注册到 PluginFilterManager
 *    - 从插件上下文中获取所有 HandlerInterceptor Bean，注册到 PluginInterceptorManager
 * 3. HTTP 请求到达时：
 *    - PluginDelegatingFilter 从 PluginFilterManager 获取所有插件 Filter 并执行
 *    - PluginDelegatingInterceptor 从 PluginInterceptorManager 获取所有插件 Interceptor 并执行
 * 4. 插件停止时，监听 SbpPluginStoppedEvent 事件：
 *    - 从 PluginFilterManager 注销插件 Filter
 *    - 从 PluginInterceptorManager 注销插件 Interceptor
 * 
 * 注意：
 * - 不能在应用启动后直接调用 ServletContext.addFilter()，会抛出 IllegalStateException
 * - 使用代理模式，在主应用启动时注册代理 Filter 和代理 Interceptor，动态查找并执行插件组件
 * - 插件 Filter 和 Interceptor 由插件容器管理，可以注入插件中的 Bean
 * - 使用 @OnMainApplication 注解，确保只在主应用中生效，避免插件 Spring 上下文扫描到此配置类
 *
 * @author baiye
 * @since 2024/12/30
 */
@Configuration
public class FilterConfiguration {

    /**
     * 注册代理 Filter
     * 在主应用启动时注册，拦截所有请求，动态执行插件 Filter
     */
    @Bean
    public FilterRegistrationBean<Filter> pluginDelegatingFilterRegistration(
            PluginDelegatingFilter filter) {

        System.out.println("FilterConfiguration - 注册代理 Filter: PluginDelegatingFilter");
        System.out.println("FilterConfiguration - Filter 实例: " + filter);

        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");  // 拦截所有请求
        registration.setName("pluginDelegatingFilter");
        registration.setOrder(1);  // 优先级设置为 1，确保在其他 Filter 之前执行

        System.out.println("FilterConfiguration - 代理 Filter 注册完成，URL 模式: /*");

        return registration;
    }
}

