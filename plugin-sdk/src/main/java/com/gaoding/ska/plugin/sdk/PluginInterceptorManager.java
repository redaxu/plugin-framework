package com.gaoding.ska.plugin.sdk;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件拦截器管理器
 * 负责管理所有插件中的 Spring MVC 拦截器，支持动态注册和注销
 * 
 * 工作原理：
 * 1. 插件启动时，将插件中的 HandlerInterceptor 注册到此管理器
 * 2. PluginDelegatingInterceptor 从此管理器获取所有插件拦截器
 * 3. 依次执行所有插件拦截器
 * 4. 插件停止时，从此管理器注销拦截器
 * 
 * 注意：
 * - 使用 @Component 注解，由 Spring 自动注册为 Bean
 * - 插件通过 ApplicationContextProvider 获取主应用中的实例
 *
 * @author baiye
 * @since 2025/01/12
 */
@Component
public class PluginInterceptorManager {
    
    /**
     * 存储所有插件拦截器
     * Key: 拦截器唯一标识（格式：pluginId:interceptorBeanName）
     * Value: HandlerInterceptor 实例
     */
    private final Map<String, HandlerInterceptor> pluginInterceptors = new ConcurrentHashMap<>();
    
    /**
     * 注册插件拦截器
     * 
     * @param interceptorKey 拦截器唯一标识（格式：pluginId:interceptorBeanName）
     * @param interceptor HandlerInterceptor 实例
     */
    public void registerInterceptor(String interceptorKey, HandlerInterceptor interceptor) {
        pluginInterceptors.put(interceptorKey, interceptor);
        System.out.println("PluginInterceptorManager - 注册拦截器: " + interceptorKey + " (" + interceptor.getClass().getName() + ")");
        System.out.println("PluginInterceptorManager - 当前已注册拦截器数量: " + pluginInterceptors.size());
    }
    
    /**
     * 注销插件拦截器
     * 
     * @param interceptorKey 拦截器唯一标识
     */
    public void unregisterInterceptor(String interceptorKey) {
        HandlerInterceptor removed = pluginInterceptors.remove(interceptorKey);
        if (removed != null) {
            System.out.println("PluginInterceptorManager - 注销拦截器: " + interceptorKey);
            System.out.println("PluginInterceptorManager - 当前已注册拦截器数量: " + pluginInterceptors.size());
        }
    }
    
    /**
     * 注销指定插件的所有拦截器
     * 
     * @param pluginId 插件 ID
     */
    public void unregisterPluginInterceptors(String pluginId) {
        String prefix = pluginId + ":";
        pluginInterceptors.keySet().removeIf(key -> {
            if (key.startsWith(prefix)) {
                System.out.println("PluginInterceptorManager - 注销插件 [" + pluginId + "] 的拦截器: " + key);
                return true;
            }
            return false;
        });
        System.out.println("PluginInterceptorManager - 当前已注册拦截器数量: " + pluginInterceptors.size());
    }
    
    /**
     * 获取所有插件拦截器
     * 
     * @return 所有插件拦截器的列表（返回副本，避免并发修改）
     */
    public List<HandlerInterceptor> getAllInterceptors() {
        return new ArrayList<>(pluginInterceptors.values());
    }
    
    /**
     * 获取已注册的拦截器数量
     * 
     * @return 拦截器数量
     */
    public int getInterceptorCount() {
        return pluginInterceptors.size();
    }
    
    /**
     * 检查是否有已注册的拦截器
     * 
     * @return 如果有已注册的拦截器返回 true，否则返回 false
     */
    public boolean hasInterceptors() {
        return !pluginInterceptors.isEmpty();
    }
}

