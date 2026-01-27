# Filter 注册机制说明

## 问题背景

在插件框架中，插件需要注册 Filter 到主应用的 ServletContext 来拦截 HTTP 请求。由于插件拥有独立的 Spring 容器（非 Web 容器），Filter 无法直接注册到 ServletContext，需要通过主应用来完成注册。

## 核心问题

1. **插件容器不是 Web 容器**：插件的 Spring ApplicationContext 是普通的 ApplicationContext，不是 WebApplicationContext，因此没有 ServletContext
2. **Filter 需要注册到 ServletContext**：只有注册到主应用的 ServletContext，Filter 才能拦截 HTTP 请求
3. **时序问题**：主应用启动完成后，插件才开始启动，需要在插件启动后动态注册 Filter

## 解决方案

### 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                         主应用                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  FilterConfiguration (监听插件启动事件)               │   │
│  │  - 监听 SbpPluginStartedEvent                        │   │
│  │  - 从插件上下文获取 Filter Bean                       │   │
│  │  - 注册 Filter 到主应用的 ServletContext             │   │
│  └──────────────────────────────────────────────────────┘   │
│                           ↓                                  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  ServletContext (Web 容器)                           │   │
│  │  - 包含所有已注册的 Filter                           │   │
│  │  - 拦截 HTTP 请求                                    │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                           ↑
                    Filter 注册
                           ↑
┌─────────────────────────────────────────────────────────────┐
│                         插件                                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  ActivityPluginFilter (定义在插件中)                 │   │
│  │  - @Component 注解                                   │   │
│  │  - 实现 Filter 接口                                  │   │
│  │  - 由插件的 Spring 容器管理                          │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 实现步骤

#### 1. 在插件中定义 Filter

插件中的 Filter 使用 `@Component` 注解，由插件的 Spring 容器管理：

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
        // 实现过滤逻辑
        if (pluginProperties != null && pluginProperties.isExpired()) {
            // 插件已过期，拒绝请求
            response.setStatus(HttpStatus.FORBIDDEN.value());
            // ... 返回错误响应
            return;
        }
        filterChain.doFilter(request, response);
    }
}
```

**关键点**：
- Filter 在插件中定义，由插件的 Spring 容器管理
- 可以注入插件中的其他 Bean（如 `ActivityPluginProperties`）
- 实现 `shouldNotFilter()` 方法来判断是否需要过滤请求

#### 2. 在主应用中监听插件启动事件

主应用的 `FilterConfiguration` 监听 `SbpPluginStartedEvent` 事件，在插件启动后注册 Filter：

```java
@Configuration
public class FilterConfiguration implements ApplicationListener<SbpPluginStartedEvent>, 
                                          ServletContextInitializer {
    
    private ServletContext servletContext;
    
    @Override
    public void onApplicationEvent(SbpPluginStartedEvent event) {
        // SbpPluginStartedEvent 的 source 是插件对象 (SpringBootPlugin)
        Object source = event.getSource();
        
        if (source instanceof SpringBootPlugin) {
            SpringBootPlugin plugin = (SpringBootPlugin) source;
            
            // 从插件对象获取其 ApplicationContext
            ApplicationContext pluginContext = plugin.getApplicationContext();
            if (pluginContext != null) {
                registerPluginFilters(pluginContext);
            }
        }
    }
    
    @Override
    public void onStartup(ServletContext servletContext) {
        this.servletContext = servletContext;
    }
    
    private void registerPluginFilters(ApplicationContext pluginContext) {
        // 从插件上下文中获取所有 Filter 类型的 Bean
        String[] filterBeanNames = pluginContext.getBeanNamesForType(Filter.class);
        
        for (String beanName : filterBeanNames) {
            Filter filter = pluginContext.getBean(beanName, Filter.class);
            registerFilterToMainContext(filter, beanName);
        }
    }
    
    private void registerFilterToMainContext(Filter filter, String filterName) {
        // 创建 FilterRegistrationBean 并注册
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/activities/**");
        registration.setName(filterName);
        registration.setOrder(1);
        registration.setEnabled(true);
        
        // 调用 onStartup 将 Filter 注册到 ServletContext
        registration.onStartup(servletContext);
    }
}
```

**关键点**：
- 实现 `ApplicationListener<SbpPluginStartedEvent>` 接口，监听插件启动事件
- 实现 `ServletContextInitializer` 接口，获取主应用的 ServletContext
- `SbpPluginStartedEvent` 的 source 是 `SpringBootPlugin` 对象，不是 ApplicationContext
- 通过 `plugin.getApplicationContext()` 获取插件的 Spring 上下文
- 使用 `FilterRegistrationBean` 将 Filter 注册到主应用的 ServletContext

## 工作流程

```
1. 主应用启动
   ↓
2. FilterConfiguration 初始化
   - onStartup() 被调用，保存 ServletContext 引用
   ↓
3. 插件启动
   - 插件的 Spring 容器初始化
   - ActivityPluginFilter 被注册为 Bean
   ↓
4. SbpPluginStartedEvent 事件触发
   ↓
5. FilterConfiguration.onApplicationEvent() 被调用
   - 获取插件的 ApplicationContext
   - 从插件上下文中获取所有 Filter Bean
   - 将每个 Filter 注册到主应用的 ServletContext
   ↓
6. Filter 生效
   - HTTP 请求到达主应用
   - ServletContext 调用已注册的 Filter
   - Filter 拦截并处理请求
```

## 关键技术点

### 1. SbpPluginStartedEvent 事件机制

`SbpPluginStartedEvent` 是 SBP 框架提供的插件启动事件：

- **触发时机**：插件的 Spring 容器启动完成后
- **事件源（source）**：`SpringBootPlugin` 对象（不是 ApplicationContext）
- **获取插件上下文**：通过 `plugin.getApplicationContext()` 方法

**错误示例**：
```java
// ❌ 错误：直接将 event.getSource() 转换为 ApplicationContext
ApplicationContext context = (ApplicationContext) event.getSource();
```

**正确示例**：
```java
// ✅ 正确：先转换为 SpringBootPlugin，再获取 ApplicationContext
SpringBootPlugin plugin = (SpringBootPlugin) event.getSource();
ApplicationContext context = plugin.getApplicationContext();
```

### 2. ServletContext 的获取

主应用通过实现 `ServletContextInitializer` 接口来获取 ServletContext：

```java
@Override
public void onStartup(ServletContext servletContext) {
    this.servletContext = servletContext;
}
```

**注意**：
- `onStartup()` 方法在主应用启动时被调用
- 必须保存 ServletContext 引用，供后续注册 Filter 使用
- 插件中无法直接获取 ServletContext（插件容器不是 Web 容器）

### 3. FilterRegistrationBean 的使用

`FilterRegistrationBean` 是 Spring Boot 提供的 Filter 注册工具：

```java
FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
registration.setFilter(filter);                    // 设置 Filter 实例
registration.addUrlPatterns("/api/activities/**"); // 设置 URL 模式
registration.setName(filterName);                  // 设置 Filter 名称
registration.setOrder(1);                          // 设置优先级
registration.setEnabled(true);                     // 启用 Filter
registration.onStartup(servletContext);            // 注册到 ServletContext
```

**关键方法**：
- `onStartup(ServletContext)`：将 Filter 注册到 ServletContext
- 必须在主应用中调用，插件中无法直接调用

### 4. 跨容器 Bean 引用

插件中的 Filter 可以注入插件中的其他 Bean：

```java
@Component
public class ActivityPluginFilter extends OncePerRequestFilter {
    
    @Autowired
    private ActivityPluginProperties pluginProperties;  // 插件中的 Bean
    
    // ...
}
```

**工作原理**：
- Filter Bean 由插件的 Spring 容器管理
- Filter 可以注入插件容器中的其他 Bean
- Filter 注册到主应用的 ServletContext 后，仍然由插件容器管理
- Filter 的依赖注入由插件容器负责

## 注意事项

### 1. Filter 的生命周期

- **创建**：由插件的 Spring 容器创建和管理
- **注册**：由主应用注册到 ServletContext
- **销毁**：插件停止时，Filter 随插件容器一起销毁

### 2. URL 模式匹配

Filter 的 URL 模式在主应用注册时指定：

```java
registration.addUrlPatterns("/api/activities", "/api/activities/*", "/api/activities/**");
```

也可以在 Filter 的 `shouldNotFilter()` 方法中判断：

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String requestURI = request.getRequestURI();
    return !(requestURI != null && requestURI.startsWith("/api/activities"));
}
```

**建议**：
- 在主应用注册时设置 URL 模式（性能更好）
- 在 Filter 中实现 `shouldNotFilter()` 作为补充（更灵活）

### 3. Filter 执行顺序

多个 Filter 的执行顺序由 `setOrder()` 方法指定：

```java
registration.setOrder(1);  // 数字越小，优先级越高
```

**注意**：
- 主应用的 Filter 和插件的 Filter 在同一个 Filter 链中
- 需要合理设置优先级，避免冲突

### 4. 插件停止时的处理

当插件停止时，需要注意：

- Filter 会随插件容器一起销毁
- 但 ServletContext 中的注册信息可能仍然存在
- 建议在插件停止时手动注销 Filter（可选）

## 最佳实践

1. **Filter 定义在插件中**：使用 `@Component` 注解，由插件容器管理
2. **Filter 注册在主应用中**：通过监听 `SbpPluginStartedEvent` 事件动态注册
3. **延迟注册**：确保在插件启动完成后再注册 Filter
4. **URL 模式匹配**：在主应用注册时指定，在 Filter 中作为补充判断
5. **异常处理**：注册过程中捕获异常，避免影响其他插件的启动
6. **日志输出**：添加详细的日志，方便调试和排查问题

## 示例代码

完整的示例代码请参考：

- **插件中的 Filter**：`activity-plugin/src/main/java/com/gaoding/ska/customize/config/ActivityPluginFilter.java`
- **主应用中的注册**：`plugin-manager/src/main/java/com/gaoding/ska/customize/config/FilterConfiguration.java`

## 常见问题

### Q1: 为什么不能在插件中直接注册 Filter？

**A**: 插件的 Spring 容器不是 Web 容器（WebApplicationContext），没有 ServletContext，因此无法直接注册 Filter。

### Q2: 为什么 SbpPluginStartedEvent 的 source 不是 ApplicationContext？

**A**: SBP 框架的设计中，事件源是 `SpringBootPlugin` 对象，需要通过 `plugin.getApplicationContext()` 方法获取插件的 Spring 上下文。

### Q3: Filter 注册后，插件停止时会自动注销吗？

**A**: Filter 会随插件容器一起销毁，但 ServletContext 中的注册信息可能仍然存在。建议在插件停止时手动注销 Filter（可选）。

### Q4: 多个插件都有 Filter，会冲突吗？

**A**: 不会冲突。每个插件的 Filter 都有独立的名称和 URL 模式，可以共存。需要注意设置合理的优先级（Order）。

### Q5: Filter 中可以注入主应用的 Bean 吗？

**A**: 不可以。Filter 由插件容器管理，只能注入插件容器中的 Bean。主应用和插件的 Spring 容器是隔离的。

## 总结

插件中的 Filter 注册机制是一个典型的**跨容器协作**场景：

- **Filter 定义在插件中**：由插件容器管理，可以注入插件中的 Bean
- **Filter 注册在主应用中**：由主应用注册到 ServletContext，才能拦截 HTTP 请求
- **通过事件机制协作**：主应用监听插件启动事件，动态注册 Filter

这种设计既保证了插件的隔离性，又实现了 Filter 的功能，是插件框架中处理 Web 组件的标准模式。

