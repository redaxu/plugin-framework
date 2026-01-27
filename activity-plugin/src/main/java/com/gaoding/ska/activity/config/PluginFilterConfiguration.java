package com.gaoding.ska.activity.config;

import com.gaoding.ska.plugin.sdk.PluginFilterManager;
import com.gaoding.ska.plugin.sdk.PluginInterceptorManager;
import org.laxture.sbp.spring.boot.SbpPluginStartedEvent;
import org.laxture.spring.util.ApplicationContextProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
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
public class PluginFilterConfiguration implements ApplicationListener<SbpPluginStartedEvent> {

    private PluginFilterManager pluginFilterManager;

    private PluginInterceptorManager pluginInterceptorManager;


    /**
     * 监听插件启动事件
     * 从插件上下文中获取 Filter 和 HandlerInterceptor Bean，注册到对应的管理器
     * 
     * 使用 ApplicationListener 接口而不是 @EventListener 注解，
     * 确保事件监听器被正确注册，不会错过插件启动事件
     */
    @Override
    public void onApplicationEvent(SbpPluginStartedEvent event) {
        System.out.println("=================================================");
        System.out.println("FilterConfiguration - 收到插件启动事件: " + event);

        // 使用 ApplicationContextProvider 通过主应用的类加载器获取主应用的 Bean
        // 获取主应用的类加载器（通常是 AppClassLoader）
        ClassLoader pluginClassLoader = this.getClass().getClassLoader();
        ClassLoader mainClassLoader = pluginClassLoader.getParent();

        System.out.println("FilterConfiguration - 插件类加载器: " + pluginClassLoader.getClass().getName());
        System.out.println("FilterConfiguration - 主应用类加载器: " + mainClassLoader.getClass().getName());

        try {
            pluginFilterManager = ApplicationContextProvider.getBean(mainClassLoader, PluginFilterManager.class);
            System.out.println("FilterConfiguration - 成功获取 PluginFilterManager: " + pluginFilterManager);
            System.out.println("FilterConfiguration - PluginFilterManager hashCode: " + pluginFilterManager.hashCode());
        } catch (Exception e) {
            System.out.println("FilterConfiguration - 获取 PluginFilterManager 失败: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            pluginInterceptorManager = ApplicationContextProvider.getBean(mainClassLoader, PluginInterceptorManager.class);
            System.out.println("FilterConfiguration - 成功获取 PluginInterceptorManager: " + pluginInterceptorManager);
            System.out.println("FilterConfiguration - PluginInterceptorManager hashCode: " + pluginInterceptorManager.hashCode());
        } catch (Exception e) {
            System.out.println("FilterConfiguration - 获取 PluginInterceptorManager 失败: " + e.getMessage());
            e.printStackTrace();
        }

        // 打印当前执行上下文信息
        ApplicationContext currentContext = (ApplicationContext) event.getSource();
        System.out.println("FilterConfiguration - 当前执行上下文: " + currentContext);
        System.out.println("FilterConfiguration - 当前执行上下文 ID: " + (currentContext != null ? currentContext.getId() : "null"));
        System.out.println("FilterConfiguration - 当前类加载器: " + this.getClass().getClassLoader().getClass().getName());
        
        System.out.println("FilterConfiguration - pluginFilterManager: " + pluginFilterManager);
        System.out.println("FilterConfiguration - pluginFilterManager hashCode: " + (pluginFilterManager != null ? pluginFilterManager.hashCode() : "null"));
        System.out.println("FilterConfiguration - pluginInterceptorManager: " + pluginInterceptorManager);
        System.out.println("FilterConfiguration - pluginInterceptorManager hashCode: " + (pluginInterceptorManager != null ? pluginInterceptorManager.hashCode() : "null"));
        
        // SbpPluginStartedEvent 的 source 是插件的 ApplicationContext
        Object source = event.getSource();
        System.out.println("FilterConfiguration - Event source type: " + source.getClass().getName());
        
        if (source instanceof ApplicationContext) {
            ApplicationContext pluginContext = (ApplicationContext) source;
            System.out.println("FilterConfiguration - 成功获取插件上下文: " + pluginContext);
            System.out.println("FilterConfiguration - 插件上下文 ID: " + pluginContext.getId());
            System.out.println("FilterConfiguration - 插件上下文 DisplayName: " + pluginContext.getDisplayName());
            
            // 从插件上下文获取插件 ID
            String pluginId = getPluginId(pluginContext);
            System.out.println("FilterConfiguration - 插件ID: " + pluginId);
            
            // 注册插件 Filter
            registerPluginFilters(pluginId, pluginContext);
            
            // 注册插件 Interceptor
            registerPluginInterceptors(pluginId, pluginContext);
            
            // 打印注册后的状态
            System.out.println("FilterConfiguration - 注册完成后，Filter 数量: " + pluginFilterManager.getFilterCount());
            System.out.println("FilterConfiguration - 注册完成后，Interceptor 数量: " + pluginInterceptorManager.getInterceptorCount());
        } else {
            System.out.println("FilterConfiguration - Event source 不是 ApplicationContext 类型");
        }
        System.out.println("=================================================");
    }
    
    /**
     * 从插件上下文获取插件 ID
     * 尝试从 ApplicationContext 的 ID 或 DisplayName 中提取插件 ID
     */
    private String getPluginId(ApplicationContext pluginContext) {
        // 尝试从 ApplicationContext 的 ID 中提取插件 ID
        String contextId = pluginContext.getId();
        if (contextId != null && !contextId.isEmpty()) {
            // ApplicationContext ID 格式通常为: application-{pluginId}
            if (contextId.contains("-")) {
                String[] parts = contextId.split("-");
                if (parts.length > 1) {
                    return parts[parts.length - 1];
        }
            }
            return contextId;
        }
        
        // 如果无法从 ID 中提取，使用 DisplayName
        String displayName = pluginContext.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        
        // 如果都无法获取，使用默认值
        return "unknown-plugin-" + System.currentTimeMillis();
    }

    /**
     * 注意：插件停止事件的监听已移至 PluginStoppedListener 类
     * 这样可以保持 FilterConfiguration 的职责单一
     */

    /**
     * 从插件上下文中获取 Filter 并注册到 PluginFilterManager
     * 
     * @param pluginId 插件 ID
     * @param pluginContext 插件的 Spring 上下文
     */
    private void registerPluginFilters(String pluginId, ApplicationContext pluginContext) {
                try {
            System.out.println("FilterConfiguration - 开始从插件上下文获取 Filter Bean");
            
            // 从插件上下文中获取所有 Filter 类型的 Bean
            String[] filterBeanNames = pluginContext.getBeanNamesForType(Filter.class);
            System.out.println("FilterConfiguration - 找到 " + filterBeanNames.length + " 个 Filter Bean");
            
            for (String beanName : filterBeanNames) {
                System.out.println("FilterConfiguration - 发现 Filter Bean: " + beanName);
                
                try {
                    Filter filter = pluginContext.getBean(beanName, Filter.class);
                    if (filter != null) {
                        // 注册到 PluginFilterManager
                        String filterKey = pluginId + ":" + beanName;
                        pluginFilterManager.registerFilter(filterKey, filter);
                        System.out.println("FilterConfiguration - 成功注册插件 Filter [" + filterKey + "] 到 PluginFilterManager");
                    }
                } catch (Exception e) {
                    System.out.println("FilterConfiguration - 注册 Filter [" + beanName + "] 失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            if (filterBeanNames.length == 0) {
                System.out.println("FilterConfiguration - 插件上下文中未找到任何 Filter Bean");
            }
        } catch (Exception e) {
            System.out.println("FilterConfiguration - 注册插件 Filter 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 从插件上下文中获取 HandlerInterceptor 并注册到 PluginInterceptorManager
     * 
     * @param pluginId 插件 ID
     * @param pluginContext 插件的 Spring 上下文
     */
    private void registerPluginInterceptors(String pluginId, ApplicationContext pluginContext) {
        try {
            System.out.println("FilterConfiguration - 开始从插件上下文获取 HandlerInterceptor Bean");
            
            // 从插件上下文中获取所有 HandlerInterceptor 类型的 Bean
            String[] interceptorBeanNames = pluginContext.getBeanNamesForType(HandlerInterceptor.class);
            System.out.println("FilterConfiguration - 找到 " + interceptorBeanNames.length + " 个 HandlerInterceptor Bean");
            
            for (String beanName : interceptorBeanNames) {
                System.out.println("FilterConfiguration - 发现 HandlerInterceptor Bean: " + beanName);
                
                try {
                    HandlerInterceptor interceptor = pluginContext.getBean(beanName, HandlerInterceptor.class);
                    if (interceptor != null) {
                        // 注册到 PluginInterceptorManager
                        String interceptorKey = pluginId + ":" + beanName;
                        pluginInterceptorManager.registerInterceptor(interceptorKey, interceptor);
                        System.out.println("FilterConfiguration - 成功注册插件 Interceptor [" + interceptorKey + "] 到 PluginInterceptorManager");
                    }
                } catch (Exception e) {
                    System.out.println("FilterConfiguration - 注册 Interceptor [" + beanName + "] 失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            if (interceptorBeanNames.length == 0) {
                System.out.println("FilterConfiguration - 插件上下文中未找到任何 HandlerInterceptor Bean");
            }
        } catch (Exception e) {
            System.out.println("FilterConfiguration - 注册插件 Interceptor 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

