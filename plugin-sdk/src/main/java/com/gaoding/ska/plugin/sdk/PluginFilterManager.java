package com.gaoding.ska.plugin.sdk;

import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件 Filter 管理器
 * 负责管理所有插件中的 Filter，支持动态注册和注销
 * 
 * 工作原理：
 * 1. 插件启动时，将插件中的 Filter 注册到此管理器
 * 2. PluginDelegatingFilter 从此管理器获取所有插件 Filter
 * 3. 依次执行所有插件 Filter
 * 4. 插件停止时，从此管理器注销 Filter
 * 
 * 注意：
 * - 使用 @Component 注解，由 Spring 自动注册为 Bean
 * - 插件通过 ApplicationContextProvider 获取主应用中的实例
 *
 * @author baiye
 * @since 2025/01/12
 */
@Component
public class PluginFilterManager {
    
    /**
     * 存储所有插件 Filter
     * Key: Filter 名称（格式：pluginId:filterBeanName）
     * Value: Filter 实例
     */
    private final Map<String, Filter> pluginFilters = new ConcurrentHashMap<>();
    
    /**
     * 注册插件 Filter
     * 
     * @param filterKey Filter 唯一标识（格式：pluginId:filterBeanName）
     * @param filter Filter 实例
     */
    public void registerFilter(String filterKey, Filter filter) {
        pluginFilters.put(filterKey, filter);
        System.out.println("PluginFilterManager - 注册 Filter: " + filterKey + " (" + filter.getClass().getName() + ")");
        System.out.println("PluginFilterManager - 当前已注册 Filter 数量: " + pluginFilters.size());
    }
    
    /**
     * 注销插件 Filter
     * 
     * @param filterKey Filter 唯一标识
     */
    public void unregisterFilter(String filterKey) {
        Filter removed = pluginFilters.remove(filterKey);
        if (removed != null) {
            System.out.println("PluginFilterManager - 注销 Filter: " + filterKey);
            System.out.println("PluginFilterManager - 当前已注册 Filter 数量: " + pluginFilters.size());
        }
    }
    
    /**
     * 注销指定插件的所有 Filter
     * 
     * @param pluginId 插件 ID
     */
    public void unregisterPluginFilters(String pluginId) {
        String prefix = pluginId + ":";
        pluginFilters.keySet().removeIf(key -> {
            if (key.startsWith(prefix)) {
                System.out.println("PluginFilterManager - 注销插件 [" + pluginId + "] 的 Filter: " + key);
                return true;
            }
            return false;
        });
        System.out.println("PluginFilterManager - 当前已注册 Filter 数量: " + pluginFilters.size());
    }
    
    /**
     * 获取所有插件 Filter
     * 
     * @return 所有插件 Filter 的集合
     */
    public Collection<Filter> getAllFilters() {
        return pluginFilters.values();
    }
    
    /**
     * 获取已注册的 Filter 数量
     * 
     * @return Filter 数量
     */
    public int getFilterCount() {
        return pluginFilters.size();
    }
    
    /**
     * 检查是否有已注册的 Filter
     * 
     * @return 如果有已注册的 Filter 返回 true，否则返回 false
     */
    public boolean hasFilters() {
        return !pluginFilters.isEmpty();
    }
}

