package com.gaoding.ska.activity.config;

import com.gaoding.ska.plugin.sdk.PluginFilterManager;
import com.gaoding.ska.plugin.sdk.PluginInterceptorManager;
import org.laxture.sbp.spring.boot.SbpPluginStoppedEvent;
import org.laxture.spring.util.ApplicationContextProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 插件停止事件监听器
 * 监听插件停止事件，注销插件的 Filter 和 Interceptor
 *
 * @author baiye
 * @since 2025/01/09
 */
@Component
public class PluginStoppedListener implements ApplicationListener<SbpPluginStoppedEvent> {

    private PluginFilterManager pluginFilterManager;

    private PluginInterceptorManager pluginInterceptorManager;
    @Override
    public void onApplicationEvent(SbpPluginStoppedEvent event) {
        System.out.println("PluginStoppedListener - 收到插件停止事件: " + event);

        // 使用 ApplicationContextProvider 通过主应用的类加载器获取主应用的 Bean
        // 获取主应用的类加载器（通常是 AppClassLoader）
        ClassLoader pluginClassLoader = this.getClass().getClassLoader();
        ClassLoader mainClassLoader = pluginClassLoader.getParent();

        Object source = event.getSource();
        if (source instanceof ApplicationContext) {
            ApplicationContext pluginContext = (ApplicationContext) source;
            String pluginId = getPluginId(pluginContext);
            System.out.println("PluginStoppedListener - 插件ID: " + pluginId);

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
            // 注销插件的所有 Filter
            pluginFilterManager.unregisterPluginFilters(pluginId);
            System.out.println("PluginStoppedListener - 已注销插件 [" + pluginId + "] 的所有 Filter");
            
            // 注销插件的所有 Interceptor
            pluginInterceptorManager.unregisterPluginInterceptors(pluginId);
            System.out.println("PluginStoppedListener - 已注销插件 [" + pluginId + "] 的所有 Interceptor");
        }
    }
    
    /**
     * 从插件的 ApplicationContext 中提取插件 ID
     * 
     * @param pluginContext 插件的 ApplicationContext
     * @return 插件 ID
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
}

