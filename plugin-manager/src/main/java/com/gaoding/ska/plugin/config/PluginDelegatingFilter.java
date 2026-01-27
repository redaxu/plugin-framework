package com.gaoding.ska.plugin.config;

import com.gaoding.ska.plugin.sdk.PluginFilterManager;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * 插件代理 Filter
 * 在主应用启动时注册，动态查找并执行插件中的 Filter
 * 
 * 工作原理：
 * 1. 主应用启动时，此 Filter 被注册到 ServletContext
 * 2. HTTP 请求到达时，此 Filter 被调用
 * 3. 从 PluginFilterManager 获取所有插件 Filter
 * 4. 创建包含所有插件 Filter 的 Filter 链
 * 5. 依次执行所有插件 Filter
 * 6. 最后执行原始 Filter 链
 * 
 * 优点：
 * - 支持动态添加和删除插件 Filter
 * - 不需要在应用启动后注册 Filter（避免 IllegalStateException）
 * - 插件 Filter 由插件容器管理，可以注入插件中的 Bean
 * 
 * 注意：
 * - 不使用 @Component 注解，通过 FilterConfiguration 中的 @Bean 方法注册
 * - 避免重复注册导致 Bean 冲突
 *
 * @author baiye
 * @since 2025/01/09
 */
@Component
public class PluginDelegatingFilter implements Filter {

    @Resource
    private PluginFilterManager pluginFilterManager;
    
    /**
     * 设置插件 Filter 管理器
     * 由于不使用 @Component 注解，在 @Bean 方法中手动调用此方法注入
     * 
     * 注意：不使用 @Autowired 注解，避免 Spring 自动注入导致实例不一致
     */
    public void setPluginFilterManager(PluginFilterManager pluginFilterManager) {
        System.out.println("PluginDelegatingFilter.setPluginFilterManager - 设置 PluginFilterManager: " + pluginFilterManager);
        System.out.println("PluginDelegatingFilter.setPluginFilterManager - PluginFilterManager hashCode: " + 
            (pluginFilterManager != null ? pluginFilterManager.hashCode() : "null"));
        this.pluginFilterManager = pluginFilterManager;
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("PluginDelegatingFilter - 初始化代理 Filter");
        System.out.println("PluginDelegatingFilter - pluginFilterManager: " + pluginFilterManager);
        if (pluginFilterManager != null) {
            System.out.println("PluginDelegatingFilter - 当前已注册 Filter 数量: " + pluginFilterManager.getFilterCount());
        } else {
            System.out.println("PluginDelegatingFilter - pluginFilterManager 为 null！");
        }
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        // 调试：检查 pluginFilterManager 是否为 null
        if (pluginFilterManager == null) {
            System.out.println("PluginDelegatingFilter.doFilter - pluginFilterManager 为 null！");
            chain.doFilter(request, response);
            return;
        }
        
        // 获取所有插件 Filter
        Collection<Filter> pluginFilters = pluginFilterManager.getAllFilters();
        
        // 调试：打印 Filter 数量
        System.out.println("PluginDelegatingFilter.doFilter - 当前插件 Filter 数量: " + pluginFilters.size());
        
        if (pluginFilters.isEmpty()) {
            // 没有插件 Filter，直接执行原始 Filter 链
            System.out.println("PluginDelegatingFilter.doFilter - 没有插件 Filter，直接执行原始 Filter 链");
            chain.doFilter(request, response);
            return;
        }
        
        // 打印调试信息
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            System.out.println("PluginDelegatingFilter - 拦截请求: " + httpRequest.getMethod() + " " + httpRequest.getRequestURI());
            System.out.println("PluginDelegatingFilter - 当前插件 Filter 数量: " + pluginFilters.size());
        }
        
        // 创建包含插件 Filter 的 Filter 链
        FilterChain pluginFilterChain = new PluginFilterChain(
            pluginFilters.iterator(), 
            chain
        );
        
        // 执行插件 Filter 链
        pluginFilterChain.doFilter(request, response);
    }
    
    @Override
    public void destroy() {
        System.out.println("PluginDelegatingFilter - 销毁代理 Filter");
    }
    
    /**
     * 插件 Filter 链
     * 依次执行所有插件 Filter，最后执行原始 Filter 链
     */
    private static class PluginFilterChain implements FilterChain {
        
        private final Iterator<Filter> filterIterator;
        private final FilterChain originalChain;
        
        public PluginFilterChain(Iterator<Filter> filterIterator, FilterChain originalChain) {
            this.filterIterator = filterIterator;
            this.originalChain = originalChain;
        }
        
        @Override
        public void doFilter(ServletRequest request, ServletResponse response) 
                throws IOException, ServletException {
            
            if (filterIterator.hasNext()) {
                // 执行下一个插件 Filter
                Filter nextFilter = filterIterator.next();
                System.out.println("PluginFilterChain - 执行插件 Filter: " + nextFilter.getClass().getName());
                nextFilter.doFilter(request, response, this);
            } else {
                // 所有插件 Filter 执行完毕，执行原始 Filter 链
                System.out.println("PluginFilterChain - 所有插件 Filter 执行完毕，执行原始 Filter 链");
                originalChain.doFilter(request, response);
            }
        }
    }
}

