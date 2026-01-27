# Filter 注册机制说明（v2 - 代理模式）

## 问题背景

在插件框架中，插件需要注册 Filter 到主应用来拦截 HTTP 请求。但存在以下问题：

1. **插件容器不是 Web 容器**：插件的 Spring ApplicationContext 是普通的 ApplicationContext，不是 WebApplicationContext，因此没有 ServletContext
2. **无法在应用启动后注册 Filter**：`ServletContext.addFilter()` 只能在 ServletContext 初始化期间调用，应用启动后调用会抛出 `IllegalStateException`
3. **时序问题**：主应用启动完成后，插件才开始启动，此时 ServletContext 已经初始化完成

## 解决方案：代理模式

### 核心思想

**在主应用启动时注册一个代理 Filter（PluginDelegatingFilter），该 Filter 动态查找并执行插件中的 Filter。**

### 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                         主应用                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  ServletContext (Web 容器)                               │   │
│  │  ┌────────────────────────────────────────────────────┐  │   │
│  │  │  PluginDelegatingFilter (代理 Filter)              │  │   │
│  │  │  - 在主应用启动时注册                              │  │   │
│  │  │  - 拦截所有请求                                    │  │   │
│  │  │  - 动态查找并执行插件 Filter                       │  │   │
│  │  └────────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                           ↓                                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  PluginFilterManager (插件 Filter 管理器)               │   │
│  │  - 存储所有插件 Filter                                  │   │
│  │  - 支持动态注册和注销                                   │   │
│  │  - 线程安全（ConcurrentHashMap）                        │   │
│  └──────────────────────────────────────────────────────────┘   │
│                           ↑                                      │
│                    注册/注销 Filter                              │
│                           ↑                                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  FilterConfiguration (监听插件事件)                     │   │
│  │  - 监听 SbpPluginStartedEvent                           │   │
│  │  - 监听 SbpPluginStoppedEvent                           │   │
│  │  - 注册/注销插件 Filter                                 │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                           ↑
                    插件启动/停止事件
                           ↑
┌─────────────────────────────────────────────────────────────────┐
│                         插件                                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  ActivityPluginFilter (定义在插件中)                     │   │
│  │  - @Component 注解                                       │   │
│  │  - 实现 Filter 接口                                      │   │
│  │  - 由插件的 Spring 容器管理                              │   │
│  │  - 可以注入插件中的 Bean                                 │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 工作流程

```
1. 主应用启动
   ↓
2. FilterConfiguration 初始化
   - 注册 PluginDelegatingFilter 到 ServletContext
   - PluginFilterManager 初始化（空）
   ↓
3. 插件启动
   - 插件的 Spring 容器初始化
   - ActivityPluginFilter 被注册为 Bean
   ↓
4. SbpPluginStartedEvent 事件触发
   ↓
5. FilterConfiguration.onPluginStarted() 被调用
   - 获取插件的 ApplicationContext
   - 从插件上下文中获取所有 Filter Bean
   - 将每个 Filter 注册到 PluginFilterManager
   ↓
6. HTTP 请求到达
   - PluginDelegatingFilter 拦截请求
   - 从 PluginFilterManager 获取所有插件 Filter
   - 创建包含所有插件 Filter 的 Filter 链
   - 依次执行所有插件 Filter
   - 执行原始 Filter 链
   ↓
7. 插件停止
   - SbpPluginStoppedEvent 事件触发
   - FilterConfiguration.onPluginStopped() 被调用
   - 从 PluginFilterManager 注销插件的所有 Filter
```

## 核心组件

### 1. PluginFilterManager（插件 Filter 管理器）

负责管理所有插件中的 Filter，支持动态注册和注销。

```java
@Component
public class PluginFilterManager {
    
    // 存储所有插件 Filter
    // Key: pluginId:filterBeanName
    // Value: Filter 实例
    private final Map<String, Filter> pluginFilters = new ConcurrentHashMap<>();
    
    // 注册插件 Filter
    public void registerFilter(String filterKey, Filter filter) {
        pluginFilters.put(filterKey, filter);
    }
    
    // 注销插件 Filter
    public void unregisterFilter(String filterKey) {
        pluginFilters.remove(filterKey);
    }
    
    // 注销指定插件的所有 Filter
    public void unregisterPluginFilters(String pluginId) {
        String prefix = pluginId + ":";
        pluginFilters.keySet().removeIf(key -> key.startsWith(prefix));
    }
    
    // 获取所有插件 Filter
    public Collection<Filter> getAllFilters() {
        return pluginFilters.values();
    }
}
```

**关键特性**：
- 使用 `ConcurrentHashMap` 保证线程安全
- Filter Key 格式：`pluginId:filterBeanName`，确保唯一性
- 支持按插件 ID 批量注销 Filter

### 2. PluginDelegatingFilter（代理 Filter）

在主应用启动时注册，动态查找并执行插件中的 Filter。

```java
@Component
public class PluginDelegatingFilter implements Filter {
    
    @Autowired
    private PluginFilterManager pluginFilterManager;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        // 获取所有插件 Filter
        Collection<Filter> pluginFilters = pluginFilterManager.getAllFilters();
        
        if (pluginFilters.isEmpty()) {
            // 没有插件 Filter，直接执行原始 Filter 链
            chain.doFilter(request, response);
            return;
        }
        
        // 创建包含插件 Filter 的 Filter 链
        FilterChain pluginFilterChain = new PluginFilterChain(
            pluginFilters.iterator(), 
            chain
        );
        
        // 执行插件 Filter 链
        pluginFilterChain.doFilter(request, response);
    }
    
    // 插件 Filter 链
    private static class PluginFilterChain implements FilterChain {
        
        private final Iterator<Filter> filterIterator;
        private final FilterChain originalChain;
        
        @Override
        public void doFilter(ServletRequest request, ServletResponse response) 
                throws IOException, ServletException {
            
            if (filterIterator.hasNext()) {
                // 执行下一个插件 Filter
                Filter nextFilter = filterIterator.next();
                nextFilter.doFilter(request, response, this);
            } else {
                // 所有插件 Filter 执行完毕，执行原始 Filter 链
                originalChain.doFilter(request, response);
            }
        }
    }
}
```

**关键特性**：
- 实现标准的 `Filter` 接口
- 动态获取插件 Filter，支持运行时添加/删除
- 创建自定义 Filter 链，依次执行所有插件 Filter
- 最后执行原始 Filter 链

### 3. FilterConfiguration（Filter 配置类）

负责注册代理 Filter 和管理插件 Filter。

```java
@Configuration
public class FilterConfiguration {

    @Autowired
    private PluginFilterManager pluginFilterManager;

    // 注册代理 Filter
    @Bean
    public FilterRegistrationBean<PluginDelegatingFilter> pluginDelegatingFilter(
            PluginDelegatingFilter filter) {
        
        FilterRegistrationBean<PluginDelegatingFilter> registration = 
            new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");  // 拦截所有请求
        registration.setName("pluginDelegatingFilter");
        registration.setOrder(1);
        
        return registration;
    }

    // 监听插件启动事件
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
            pluginFilterManager.registerFilter(filterKey, filter);
        }
    }

    // 监听插件停止事件
    @EventListener
    public void onPluginStopped(SbpPluginStoppedEvent event) {
        SpringBootPlugin plugin = (SpringBootPlugin) event.getSource();
        String pluginId = plugin.getWrapper().getPluginId();
        
        // 注销插件的所有 Filter
        pluginFilterManager.unregisterPluginFilters(pluginId);
    }
}
```

**关键特性**：
- 使用 `@Bean` 注解注册代理 Filter（在应用启动时）
- 使用 `@EventListener` 监听插件启动和停止事件
- 从插件上下文动态获取 Filter Bean
- 支持插件停止时自动注销 Filter

### 4. ActivityPluginFilter（插件中的 Filter）

插件中定义的 Filter，由插件容器管理。

```java
@Component
@Order(1)
public class ActivityPluginFilter extends OncePerRequestFilter {
    
    @Autowired
    private ActivityPluginProperties pluginProperties;
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        return !(requestURI != null && requestURI.startsWith("/api/activities"));
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) 
            throws ServletException, IOException {
        
        // 检查插件是否过期
        if (pluginProperties != null && pluginProperties.isExpired()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            // ... 返回错误响应
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}
```

**关键特性**：
- 使用 `@Component` 注解，由插件容器管理
- 可以注入插件中的其他 Bean
- 实现 `shouldNotFilter()` 方法判断是否需要过滤
- 实现业务逻辑（如检查插件过期状态）

## 优势

### 1. 解决动态注册问题

✅ **不需要在应用启动后调用 `ServletContext.addFilter()`**
- 代理 Filter 在主应用启动时注册
- 插件 Filter 注册到管理器，不涉及 ServletContext

### 2. 支持动态添加和删除

✅ **插件启动时自动注册 Filter**
- 监听 `SbpPluginStartedEvent` 事件
- 从插件上下文获取 Filter Bean
- 注册到 `PluginFilterManager`

✅ **插件停止时自动注销 Filter**
- 监听 `SbpPluginStoppedEvent` 事件
- 从 `PluginFilterManager` 注销 Filter
- 不影响其他插件的 Filter

### 3. 保持插件隔离性

✅ **插件 Filter 由插件容器管理**
- Filter Bean 在插件的 Spring 容器中
- 可以注入插件中的其他 Bean
- 插件停止时，Filter 随插件容器一起销毁

### 4. 性能优化

✅ **只有在有插件 Filter 时才创建 Filter 链**
- 如果没有插件 Filter，直接执行原始 Filter 链
- 避免不必要的性能开销

✅ **使用 `ConcurrentHashMap` 保证线程安全**
- 支持并发读写
- 性能优于同步集合

## 注意事项

### 1. Filter 执行顺序

代理 Filter 的执行顺序由 `setOrder()` 方法指定：

```java
registration.setOrder(1);  // 数字越小，优先级越高
```

插件 Filter 的执行顺序由注册顺序决定（`ConcurrentHashMap` 的迭代顺序）。

**建议**：
- 如果需要控制插件 Filter 的执行顺序，可以使用 `LinkedHashMap` 或 `TreeMap`
- 或者在 Filter 中使用 `@Order` 注解，在管理器中排序

### 2. Filter 的生命周期

- **创建**：由插件的 Spring 容器创建和管理
- **注册**：注册到 `PluginFilterManager`
- **执行**：由 `PluginDelegatingFilter` 调用
- **注销**：插件停止时，从 `PluginFilterManager` 注销
- **销毁**：插件停止时，随插件容器一起销毁

### 3. URL 模式匹配

代理 Filter 拦截所有请求（`/*`），插件 Filter 可以在 `shouldNotFilter()` 方法中判断是否需要过滤：

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String requestURI = request.getRequestURI();
    return !(requestURI != null && requestURI.startsWith("/api/activities"));
}
```

**建议**：
- 在插件 Filter 中实现 `shouldNotFilter()` 方法
- 只过滤插件相关的请求，避免影响其他请求

### 4. 跨容器 Bean 引用

插件 Filter 只能注入插件容器中的 Bean，不能注入主应用的 Bean：

```java
@Component
public class ActivityPluginFilter extends OncePerRequestFilter {
    
    @Autowired
    private ActivityPluginProperties pluginProperties;  // ✅ 插件中的 Bean
    
    // @Autowired
    // private MainAppService mainAppService;  // ❌ 主应用的 Bean，无法注入
}
```

## 总结

### 核心思想

**使用代理模式，在主应用启动时注册代理 Filter，动态查找并执行插件中的 Filter。**

### 关键组件

1. **PluginFilterManager**：管理所有插件 Filter
2. **PluginDelegatingFilter**：代理 Filter，动态执行插件 Filter
3. **FilterConfiguration**：注册代理 Filter，监听插件事件
4. **ActivityPluginFilter**：插件中的 Filter

### 优势

- ✅ 解决动态注册问题（不需要在应用启动后调用 `ServletContext.addFilter()`）
- ✅ 支持动态添加和删除 Filter
- ✅ 保持插件隔离性（Filter 由插件容器管理）
- ✅ 性能优化（只有在有插件 Filter 时才创建 Filter 链）

### 适用场景

- 插件框架中需要动态注册 Filter
- 需要在应用运行时添加/删除 Filter
- 需要保持插件隔离性，Filter 可以注入插件中的 Bean

这种方案是插件框架中处理 Filter 的标准模式，既解决了动态注册的问题，又保持了良好的架构设计。

