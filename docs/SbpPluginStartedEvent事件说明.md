# SbpPluginStartedEvent 事件说明

## 事件发布方式

根据 SBP 框架的实现，`SbpPluginStartedEvent` 事件的发布代码如下：

```java
applicationContext.publishEvent(new SbpPluginStartedEvent(applicationContext));
```

## 事件源（Source）

**重要**：`SbpPluginStartedEvent` 的 source 是**插件的 ApplicationContext**，不是 `SpringBootPlugin` 对象。

### 正确的事件处理方式

```java
@EventListener
public void onPluginStarted(SbpPluginStartedEvent event) {
    // ✅ 正确：source 是 ApplicationContext
    Object source = event.getSource();
    
    if (source instanceof ApplicationContext) {
        ApplicationContext pluginContext = (ApplicationContext) source;
        
        // 从插件上下文获取插件 ID
        String pluginId = getPluginId(pluginContext);
        
        // 从插件上下文获取 Bean
        String[] filterBeanNames = pluginContext.getBeanNamesForType(Filter.class);
        // ...
    }
}
```

### 错误的事件处理方式

```java
@EventListener
public void onPluginStarted(SbpPluginStartedEvent event) {
    // ❌ 错误：source 不是 SpringBootPlugin
    Object source = event.getSource();
    
    if (source instanceof SpringBootPlugin) {
        SpringBootPlugin plugin = (SpringBootPlugin) source;
        // 这段代码不会执行，因为 source 是 ApplicationContext
    }
}
```

## 获取插件 ID

由于 source 是 `ApplicationContext`，无法直接获取插件 ID，需要从 ApplicationContext 的属性中提取：

### 方法1：从 ApplicationContext ID 中提取

```java
private String getPluginId(ApplicationContext pluginContext) {
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
```

### 方法2：从 Environment 中获取

```java
private String getPluginId(ApplicationContext pluginContext) {
    Environment environment = pluginContext.getEnvironment();
    
    // 尝试从配置中获取插件 ID
    String pluginId = environment.getProperty("plugin.id");
    if (pluginId != null && !pluginId.isEmpty()) {
        return pluginId;
    }
    
    // 尝试从 spring.application.name 获取
    String appName = environment.getProperty("spring.application.name");
    if (appName != null && !appName.isEmpty()) {
        return appName;
    }
    
    // 使用默认值
    return "unknown-plugin-" + System.currentTimeMillis();
}
```

### 方法3：从 Bean 中获取（推荐）

如果插件中定义了插件信息 Bean：

```java
private String getPluginId(ApplicationContext pluginContext) {
    try {
        // 尝试从插件上下文获取 PluginWrapper Bean
        PluginWrapper wrapper = pluginContext.getBean(PluginWrapper.class);
        if (wrapper != null) {
            return wrapper.getPluginId();
        }
    } catch (Exception e) {
        // Bean 不存在，使用其他方法
    }
    
    // 使用 ApplicationContext ID
    return pluginContext.getId();
}
```

## 完整示例

```java
@Configuration
public class FilterConfiguration {

    @Autowired
    private PluginFilterManager pluginFilterManager;
    
    @Autowired
    private PluginInterceptorManager pluginInterceptorManager;

    /**
     * 监听插件启动事件
     */
    @EventListener
    public void onPluginStarted(SbpPluginStartedEvent event) {
        System.out.println("FilterConfiguration - 收到插件启动事件: " + event);
        
        // source 是插件的 ApplicationContext
        Object source = event.getSource();
        System.out.println("FilterConfiguration - Event source type: " + source.getClass().getName());
        
        if (source instanceof ApplicationContext) {
            ApplicationContext pluginContext = (ApplicationContext) source;
            System.out.println("FilterConfiguration - 成功获取插件上下文: " + pluginContext);
            
            // 从插件上下文获取插件 ID
            String pluginId = getPluginId(pluginContext);
            System.out.println("FilterConfiguration - 插件ID: " + pluginId);
            
            // 注册插件 Filter
            registerPluginFilters(pluginId, pluginContext);
            
            // 注册插件 Interceptor
            registerPluginInterceptors(pluginId, pluginContext);
        } else {
            System.out.println("FilterConfiguration - Event source 不是 ApplicationContext 类型");
        }
    }

    /**
     * 监听插件停止事件
     */
    @EventListener
    public void onPluginStopped(SbpPluginStoppedEvent event) {
        System.out.println("FilterConfiguration - 收到插件停止事件: " + event);
        
        Object source = event.getSource();
        if (source instanceof ApplicationContext) {
            ApplicationContext pluginContext = (ApplicationContext) source;
            String pluginId = getPluginId(pluginContext);
            System.out.println("FilterConfiguration - 插件ID: " + pluginId);
            
            // 注销插件的所有 Filter
            pluginFilterManager.unregisterPluginFilters(pluginId);
            
            // 注销插件的所有 Interceptor
            pluginInterceptorManager.unregisterPluginInterceptors(pluginId);
        }
    }
    
    /**
     * 从插件上下文获取插件 ID
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
    
    private void registerPluginFilters(String pluginId, ApplicationContext pluginContext) {
        // 从插件上下文中获取所有 Filter Bean
        String[] filterBeanNames = pluginContext.getBeanNamesForType(Filter.class);
        
        for (String beanName : filterBeanNames) {
            Filter filter = pluginContext.getBean(beanName, Filter.class);
            String filterKey = pluginId + ":" + beanName;
            pluginFilterManager.registerFilter(filterKey, filter);
        }
    }
    
    private void registerPluginInterceptors(String pluginId, ApplicationContext pluginContext) {
        // 从插件上下文中获取所有 HandlerInterceptor Bean
        String[] interceptorBeanNames = pluginContext.getBeanNamesForType(HandlerInterceptor.class);
        
        for (String beanName : interceptorBeanNames) {
            HandlerInterceptor interceptor = pluginContext.getBean(beanName, HandlerInterceptor.class);
            String interceptorKey = pluginId + ":" + beanName;
            pluginInterceptorManager.registerInterceptor(interceptorKey, interceptor);
        }
    }
}
```

## 关键要点

### 1. 事件源是 ApplicationContext

```java
// SBP 框架发布事件的代码
applicationContext.publishEvent(new SbpPluginStartedEvent(applicationContext));
                                                           ↑
                                                    这是 source
```

### 2. 无法直接获取 SpringBootPlugin 对象

由于 source 是 `ApplicationContext`，无法直接获取 `SpringBootPlugin` 对象和 `PluginWrapper`。

### 3. 需要从 ApplicationContext 中提取插件 ID

可以通过以下方式获取插件 ID：
- ApplicationContext.getId()
- ApplicationContext.getDisplayName()
- Environment.getProperty("plugin.id")
- 从 Bean 中获取（如果插件定义了相关 Bean）

### 4. 直接使用 ApplicationContext

好处是可以直接从插件上下文获取 Bean，不需要额外的步骤：

```java
ApplicationContext pluginContext = (ApplicationContext) event.getSource();

// 直接获取 Bean
String[] filterBeanNames = pluginContext.getBeanNamesForType(Filter.class);
for (String beanName : filterBeanNames) {
    Filter filter = pluginContext.getBean(beanName, Filter.class);
    // ...
}
```

## 总结

| 维度 | 说明 |
|------|------|
| **事件类型** | `SbpPluginStartedEvent` |
| **事件源（source）** | 插件的 `ApplicationContext` |
| **发布方式** | `applicationContext.publishEvent(new SbpPluginStartedEvent(applicationContext))` |
| **获取插件上下文** | 直接转换：`(ApplicationContext) event.getSource()` |
| **获取插件 ID** | 从 ApplicationContext 的属性中提取 |
| **获取 Bean** | `pluginContext.getBeanNamesForType()` 和 `pluginContext.getBean()` |

**重要提示**：之前文档中关于 source 是 `SpringBootPlugin` 的说明是错误的，已经在本文档中修正。

