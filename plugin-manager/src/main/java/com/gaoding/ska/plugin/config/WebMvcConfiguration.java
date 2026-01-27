package com.gaoding.ska.plugin.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.Filter;

/**
 * Spring MVC 配置类
 * 注册代理拦截器，支持动态执行插件中的 HandlerInterceptor
 * 
 * 工作原理：
 * 1. 主应用启动时，注册 PluginDelegatingInterceptor 到 Spring MVC
 * 2. PluginDelegatingInterceptor 拦截所有请求
 * 3. 从 PluginInterceptorManager 获取所有插件拦截器
 * 4. 依次执行所有插件拦截器
 * 
 * 注意：
 * - 代理拦截器拦截所有请求（/**）
 * - 插件拦截器可以在自己的 shouldNotFilter() 方法中判断是否需要拦截
 * - 拦截器的执行顺序由注册顺序决定
 * - 使用 @OnMainApplication 注解，确保只在主应用中生效
 *
 * @author baiye
 * @since 2025/01/09
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    
    @Autowired
    private PluginDelegatingInterceptor pluginDelegatingInterceptor;
    
    /**
     * 添加拦截器
     * 注册代理拦截器，拦截所有请求
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        System.out.println("WebMvcConfiguration - 注册代理拦截器: PluginDelegatingInterceptor");
        
        registry.addInterceptor(pluginDelegatingInterceptor)
                .addPathPatterns("/**")  // 拦截所有请求
                .order(1);  // 设置优先级，数字越小优先级越高
        
        System.out.println("WebMvcConfiguration - 代理拦截器注册完成，URL 模式: /**");
    }

}

