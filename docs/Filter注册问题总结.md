# Filter 注册问题总结

## 问题发现

用户提出了一个非常关键的问题：

> **registerFilterToMainContext 注册 filter 的时候，主应用已经启动，是否能够注册 Filter 成功？**

## 问题分析

### 原有方案的问题

原有方案尝试在主应用启动后，通过 `FilterRegistrationBean.onStartup(servletContext)` 动态注册 Filter：

```java
private void registerFilterToMainContext(Filter filter, String filterName) {
    FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
    registration.setFilter(filter);
    registration.addUrlPatterns("/api/activities/**");
    registration.setName(filterName);
    registration.setOrder(1);
    registration.setEnabled(true);
    
    // ❌ 问题：在应用启动后调用，会抛出 IllegalStateException
    registration.onStartup(servletContext);
}
```

### 为什么会失败？

1. **ServletContext 生命周期限制**
   - `ServletContext.addFilter()` 方法只能在 ServletContext 初始化期间调用
   - 一旦 ServletContext 初始化完成（应用启动完成），就无法再添加新的 Filter
   - Servlet 3.0 规范明确规定了这一限制

2. **时序问题**
   ```
   主应用启动 → ServletContext 初始化完成 → 插件启动 → 尝试注册 Filter → ❌ 失败
   ```

3. **错误信息**
   ```
   java.lang.IllegalStateException: Unable to configure filter [xxx] 
   as the servlet context has been initialized
   ```

## 解决方案：代理模式

### 核心思想

**在主应用启动时注册一个代理 Filter，该 Filter 动态查找并执行插件中的 Filter。**

### 架构对比

#### ❌ 原有方案（失败）

```
主应用启动 → ServletContext 初始化完成
                ↓
            插件启动
                ↓
        尝试注册 Filter 到 ServletContext
                ↓
            ❌ 失败（IllegalStateException）
```

#### ✅ 新方案（成功）

```
主应用启动 → 注册代理 Filter 到 ServletContext
                ↓
            插件启动
                ↓
        注册 Filter 到 PluginFilterManager
                ↓
        HTTP 请求 → 代理 Filter → 执行插件 Filter
```

### 实现组件

1. **PluginFilterManager**：管理所有插件 Filter
2. **PluginDelegatingFilter**：代理 Filter，动态执行插件 Filter
3. **FilterConfiguration**：注册代理 Filter，监听插件事件

### 工作流程

```
1. 主应用启动
   - 注册 PluginDelegatingFilter 到 ServletContext ✅
   ↓
2. 插件启动
   - 插件 Filter 被创建为 Bean
   ↓
3. 监听插件启动事件
   - 从插件上下文获取 Filter Bean
   - 注册到 PluginFilterManager ✅
   ↓
4. HTTP 请求到达
   - PluginDelegatingFilter 拦截请求
   - 从 PluginFilterManager 获取所有插件 Filter
   - 依次执行插件 Filter ✅
```

## 代码对比

### ❌ 原有方案

```java
@Configuration
public class FilterConfiguration implements ApplicationListener<SbpPluginStartedEvent>, 
                                          ServletContextInitializer {
    
    private ServletContext servletContext;
    
    @Override
    public void onApplicationEvent(SbpPluginStartedEvent event) {
        // 获取插件 Filter
        Filter filter = getPluginFilter(event);
        
        // ❌ 尝试在应用启动后注册 Filter
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.onStartup(servletContext);  // ❌ 失败
    }
}
```

### ✅ 新方案

```java
@Configuration
public class FilterConfiguration {

    @Autowired
    private PluginFilterManager pluginFilterManager;

    // ✅ 在主应用启动时注册代理 Filter
    @Bean
    public FilterRegistrationBean<PluginDelegatingFilter> pluginDelegatingFilter(
            PluginDelegatingFilter filter) {
        
        FilterRegistrationBean<PluginDelegatingFilter> registration = 
            new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setName("pluginDelegatingFilter");
        registration.setOrder(1);
        
        return registration;  // ✅ 成功
    }

    // ✅ 监听插件启动事件，注册到管理器
    @EventListener
    public void onPluginStarted(SbpPluginStartedEvent event) {
        SpringBootPlugin plugin = (SpringBootPlugin) event.getSource();
        String pluginId = plugin.getWrapper().getPluginId();
        ApplicationContext pluginContext = plugin.getApplicationContext();
        
        // 从插件上下文中获取所有 Filter Bean
        String[] filterBeanNames = pluginContext.getBeanNamesForType(Filter.class);
        
        for (String beanName : filterBeanNames) {
            Filter filter = pluginContext.getBean(beanName, Filter.class);
            String filterKey = pluginId + ":" + beanName;
            pluginFilterManager.registerFilter(filterKey, filter);  // ✅ 成功
        }
    }
}
```

## 关键改进

| 维度 | 原有方案 | 新方案 |
|------|---------|--------|
| **Filter 注册时机** | ❌ 应用启动后 | ✅ 应用启动时 |
| **注册方式** | ❌ 直接注册到 ServletContext | ✅ 注册代理 Filter |
| **动态性** | ❌ 无法注册 | ✅ 支持动态添加/删除 |
| **是否会失败** | ❌ 抛出 IllegalStateException | ✅ 正常工作 |
| **插件隔离性** | ✅ 保持 | ✅ 保持 |
| **实现复杂度** | 简单 | 中等 |

## 技术要点

### 1. Servlet 规范限制

Servlet 3.0 规范规定：

> **ServletContext.addFilter() 方法只能在 ServletContext 初始化期间调用。**

这意味着：
- ✅ 可以在 `ServletContextInitializer.onStartup()` 中调用（应用启动时）
- ❌ 不能在应用启动后调用（会抛出 `IllegalStateException`）

### 2. FilterRegistrationBean 的设计

`FilterRegistrationBean.onStartup()` 方法内部调用 `ServletContext.addFilter()`：

```java
public void onStartup(ServletContext servletContext) throws ServletException {
    // 内部调用 ServletContext.addFilter()
    Dynamic registration = servletContext.addFilter(getOrDeduceName(filter), filter);
    // ...
}
```

因此，`FilterRegistrationBean.onStartup()` 也只能在应用启动时调用。

### 3. 代理模式的优势

代理模式的核心优势：

1. **解耦注册时机**：代理 Filter 在应用启动时注册，插件 Filter 在插件启动时注册
2. **动态性**：支持在运行时动态添加和删除插件 Filter
3. **灵活性**：可以控制 Filter 的执行顺序、条件等
4. **隔离性**：插件 Filter 由插件容器管理，保持隔离

## 总结

### 问题

❌ **无法在应用启动后直接注册 Filter 到 ServletContext**

原因：
- Servlet 规范限制
- `ServletContext.addFilter()` 只能在初始化期间调用
- 应用启动后调用会抛出 `IllegalStateException`

### 解决方案

✅ **使用代理模式**

核心思想：
- 在主应用启动时注册代理 Filter
- 插件启动时，将插件 Filter 注册到管理器
- 代理 Filter 动态查找并执行插件 Filter

### 关键组件

1. **PluginFilterManager**：管理所有插件 Filter
2. **PluginDelegatingFilter**：代理 Filter，动态执行插件 Filter
3. **FilterConfiguration**：注册代理 Filter，监听插件事件

### 优势

- ✅ 解决动态注册问题
- ✅ 支持动态添加和删除 Filter
- ✅ 保持插件隔离性
- ✅ 性能优化（只有在有插件 Filter 时才创建 Filter 链）

### 相关文档

- [Filter注册机制说明-v2.md](Filter注册机制说明-v2.md) - 详细的实现说明
- [动态Filter注册问题分析.md](动态Filter注册问题分析.md) - 问题分析和解决方案对比
- [README.md](../README.md) - 项目整体说明文档

## 致谢

感谢用户提出这个关键问题，帮助我们发现了原有方案的致命缺陷，并促使我们设计了更加健壮的解决方案！

