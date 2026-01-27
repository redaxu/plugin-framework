package com.gaoding.ska.plugin.controller;

import com.gaoding.ska.plugin.sdk.PluginFilterManager;
import com.gaoding.ska.plugin.sdk.PluginInterceptorManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 插件测试控制器
 * 用于测试和诊断插件 Filter 和 Interceptor 的注册情况
 *
 * @author baiye
 * @since 2026/01/09
 */
@RestController
@RequestMapping("/plugin-test")
public class PluginTestController {

//    @Autowired
    private PluginFilterManager pluginFilterManager;

//    @Autowired
    private PluginInterceptorManager pluginInterceptorManager;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 获取插件 Filter 注册情况
     */
    @GetMapping("/filters")
    public Map<String, Object> getFilters() {
        Map<String, Object> result = new HashMap<>();
        
        Collection<javax.servlet.Filter> filters = pluginFilterManager.getAllFilters();
        result.put("filterCount", filters.size());
        result.put("filters", filters.stream()
                .map(f -> f.getClass().getName())
                .toArray());
        
        result.put("pluginFilterManager", pluginFilterManager.toString());
        result.put("pluginFilterManagerHashCode", pluginFilterManager.hashCode());
        
        return result;
    }

    /**
     * 获取插件 Interceptor 注册情况
     */
    @GetMapping("/interceptors")
    public Map<String, Object> getInterceptors() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("interceptorCount", pluginInterceptorManager.getInterceptorCount());
        result.put("pluginInterceptorManager", pluginInterceptorManager.toString());
        
        return result;
    }

    /**
     * 获取 ApplicationContext 信息
     */
    @GetMapping("/context")
    public Map<String, Object> getContext() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("contextId", applicationContext.getId());
        result.put("contextDisplayName", applicationContext.getDisplayName());
        result.put("contextClass", applicationContext.getClass().getName());
        
        // 获取所有 ApplicationListener Bean
        String[] listenerBeanNames = applicationContext.getBeanNamesForType(
                org.springframework.context.ApplicationListener.class);
        result.put("listenerCount", listenerBeanNames.length);
        result.put("listeners", listenerBeanNames);
        
        return result;
    }
}

