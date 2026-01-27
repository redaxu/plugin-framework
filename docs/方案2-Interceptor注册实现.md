# 方案2：Spring MVC Interceptor 注册实现

## 概述

方案2 实现了对插件中 Spring MVC 拦截器（`HandlerInterceptor`）的动态注册和管理，与方案1（Filter 注册）类似，采用代理模式。

## 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                         主应用                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Spring MVC                                              │   │
│  │  ┌────────────────────────────────────────────────────┐  │   │
│  │  │  PluginDelegatingInterceptor (代理拦截器)         │  │   │
│  │  │  - 在主应用启动时注册                              │  │   │
│  │  │  - 拦截所有 Controller 请求                        │  │   │
│  │  │  - 动态查找并执行插件拦截器                        │  │   │
│  │  └────────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                           ↓                                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  PluginInterceptorManager (插件拦截器管理器)            │   │
│  │  - 存储所有插件拦截器                                   │   │
│  │  - 支持动态注册和注销                                   │   │
│  │  - 线程安全（ConcurrentHashMap）                        │   │
│  └──────────────────────────────────────────────────────────┘   │
│                           ↑                                      │
│                    注册/注销拦截器                               │
│                           ↑                                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  FilterConfiguration (监听插件事件)                     │   │
│  │  - 监听 SbpPluginStartedEvent                           │   │
│  │  - 监听 SbpPluginStoppedEvent                           │   │
│  │  - 注册/注销插件拦截器                                  │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                           ↑
                    插件启动/停止事件
                           ↑
┌─────────────────────────────────────────────────────────────────┐
│                         插件                                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  ActivityPluginInterceptor (定义在插件中)               │   │
│  │  - @Component 注解                                       │   │
│  │  - 实现 HandlerInterceptor 接口                          │   │
│  │  - 由插件的 Spring 容器管理                              │   │
│  │  - 可以注入插件中的 Bean                                 │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## 核心组件

### 1. PluginInterceptorManager（插件拦截器管理器）

负责管理所有插件中的 `HandlerInterceptor`，支持动态注册和注销。

```java
@Component
public class PluginInterceptorManager {
    
    // 存储所有插件拦截器
    // Key: pluginId:interceptorBeanName
    // Value: HandlerInterceptor 实例
    private final Map<String, HandlerInterceptor> pluginInterceptors = new ConcurrentHashMap<>();
    
    // 注册插件拦截器
    public void registerInterceptor(String interceptorKey, HandlerInterceptor interceptor) {
        pluginInterceptors.put(interceptorKey, interceptor);
    }
    
    // 注销插件拦截器
    public void unregisterInterceptor(String interceptorKey) {
        pluginInterceptors.remove(interceptorKey);
    }
    
    // 注销指定插件的所有拦截器
    public void unregisterPluginInterceptors(String pluginId) {
        String prefix = pluginId + ":";
        pluginInterceptors.keySet().removeIf(key -> key.startsWith(prefix));
    }
    
    // 获取所有插件拦截器
    public List<HandlerInterceptor> getAllInterceptors() {
        return new ArrayList<>(pluginInterceptors.values());
    }
}
```

**关键特性**：
- 使用 `ConcurrentHashMap` 保证线程安全
- Interceptor Key 格式：`pluginId:interceptorBeanName`，确保唯一性
- 支持按插件 ID 批量注销拦截器

### 2. PluginDelegatingInterceptor（代理拦截器）

在主应用启动时注册，动态查找并执行插件中的 `HandlerInterceptor`。

```java
@Component
public class PluginDelegatingInterceptor implements HandlerInterceptor {
    
    @Autowired
    private PluginInterceptorManager pluginInterceptorManager;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) throws Exception {
        
        // 获取所有插件拦截器
        List<HandlerInterceptor> pluginInterceptors = pluginInterceptorManager.getAllInterceptors();
        
        if (pluginInterceptors.isEmpty()) {
            return true;
        }
        
        // 依次执行所有插件拦截器的 preHandle 方法
        for (HandlerInterceptor interceptor : pluginInterceptors) {
            boolean result = interceptor.preHandle(request, response, handler);
            if (!result) {
                // 如果某个拦截器返回 false，停止执行
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public void postHandle(HttpServletRequest request, 
                          HttpServletResponse response, 
                          Object handler, 
                          ModelAndView modelAndView) throws Exception {
        
        List<HandlerInterceptor> pluginInterceptors = pluginInterceptorManager.getAllInterceptors();
        
        // 依次执行所有插件拦截器的 postHandle 方法
        for (HandlerInterceptor interceptor : pluginInterceptors) {
            interceptor.postHandle(request, response, handler, modelAndView);
        }
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler, 
                               Exception ex) throws Exception {
        
        List<HandlerInterceptor> pluginInterceptors = pluginInterceptorManager.getAllInterceptors();
        
        // 依次执行所有插件拦截器的 afterCompletion 方法
        for (HandlerInterceptor interceptor : pluginInterceptors) {
            try {
                interceptor.afterCompletion(request, response, handler, ex);
            } catch (Exception e) {
                // 捕获异常，避免影响其他拦截器
                e.printStackTrace();
            }
        }
    }
}
```

**关键特性**：
- 实现标准的 `HandlerInterceptor` 接口
- 动态获取插件拦截器，支持运行时添加/删除
- 依次执行所有插件拦截器的三个方法
- 在 `afterCompletion` 中捕获异常，避免影响其他拦截器

### 3. WebMvcConfiguration（Spring MVC 配置类）

注册代理拦截器到 Spring MVC。

```java
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    
    @Autowired
    private PluginDelegatingInterceptor pluginDelegatingInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pluginDelegatingInterceptor)
                .addPathPatterns("/**")  // 拦截所有请求
                .order(1);  // 设置优先级
    }
}
```

**关键特性**：
- 在主应用启动时注册代理拦截器
- 拦截所有请求（`/**`）
- 设置优先级（`order(1)`）

### 4. FilterConfiguration（配置类）

监听插件事件，注册和注销插件拦截器。

```java
@Configuration
public class FilterConfiguration {

    @Autowired
    private PluginInterceptorManager pluginInterceptorManager;

    // 监听插件启动事件
    @EventListener
    public void onPluginStarted(SbpPluginStartedEvent event) {
        SpringBootPlugin plugin = (SpringBootPlugin) event.getSource();
        String pluginId = plugin.getWrapper().getPluginId();
        ApplicationContext pluginContext = plugin.getApplicationContext();
        
        // 从插件上下文中获取所有 HandlerInterceptor Bean
        String[] interceptorBeanNames = pluginContext.getBeanNamesForType(HandlerInterceptor.class);
        
        for (String beanName : interceptorBeanNames) {
            HandlerInterceptor interceptor = pluginContext.getBean(beanName, HandlerInterceptor.class);
            String interceptorKey = pluginId + ":" + beanName;
            pluginInterceptorManager.registerInterceptor(interceptorKey, interceptor);
        }
    }

    // 监听插件停止事件
    @EventListener
    public void onPluginStopped(SbpPluginStoppedEvent event) {
        SpringBootPlugin plugin = (SpringBootPlugin) event.getSource();
        String pluginId = plugin.getWrapper().getPluginId();
        
        // 注销插件的所有拦截器
        pluginInterceptorManager.unregisterPluginInterceptors(pluginId);
    }
}
```

**关键特性**：
- 使用 `@EventListener` 监听插件启动和停止事件
- 从插件上下文动态获取 `HandlerInterceptor` Bean
- 支持插件停止时自动注销拦截器

### 5. ActivityPluginInterceptor（插件中的拦截器）

插件中定义的拦截器，由插件容器管理。

```java
@Component
@Order(1)
public class ActivityPluginInterceptor implements HandlerInterceptor {
    
    @Autowired
    private ActivityPluginProperties pluginProperties;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) throws Exception {
        
        String requestURI = request.getRequestURI();
        
        // 只拦截活动相关的接口
        if (requestURI != null && requestURI.startsWith("/api/activities")) {
            // 检查插件是否过期
            if (pluginProperties != null && pluginProperties.isExpired()) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                // ... 返回错误响应
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public void postHandle(HttpServletRequest request, 
                         HttpServletResponse response, 
                         Object handler, 
                         ModelAndView modelAndView) throws Exception {
        
        // 在 Controller 执行后，视图渲染前执行
        // 可以修改 ModelAndView
        if (modelAndView != null) {
            modelAndView.addObject("pluginVersion", "1.0.0");
        }
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler, 
                               Exception ex) throws Exception {
        
        // 请求完成后执行
        // 清理资源、记录日志等
    }
}
```

**关键特性**：
- 使用 `@Component` 注解，由插件容器管理
- 可以注入插件中的其他 Bean
- 实现 `HandlerInterceptor` 接口的三个方法
- 可以访问 Controller 信息（Handler、ModelAndView 等）

## 工作流程

```
1. 主应用启动
   ↓
2. WebMvcConfiguration 初始化
   - 注册 PluginDelegatingInterceptor 到 Spring MVC
   - PluginInterceptorManager 初始化（空）
   ↓
3. 插件启动
   - 插件的 Spring 容器初始化
   - ActivityPluginInterceptor 被注册为 Bean
   ↓
4. SbpPluginStartedEvent 事件触发
   ↓
5. FilterConfiguration.onPluginStarted() 被调用
   - 获取插件的 ApplicationContext
   - 从插件上下文中获取所有 HandlerInterceptor Bean
   - 将每个拦截器注册到 PluginInterceptorManager
   ↓
6. HTTP 请求到达
   - Filter 执行（如果有）
   - DispatcherServlet 处理请求
   - PluginDelegatingInterceptor.preHandle() 被调用
     - 从 PluginInterceptorManager 获取所有插件拦截器
     - 依次执行所有插件拦截器的 preHandle()
   - Controller 执行
   - PluginDelegatingInterceptor.postHandle() 被调用
     - 依次执行所有插件拦截器的 postHandle()
   - 视图渲染
   - PluginDelegatingInterceptor.afterCompletion() 被调用
     - 依次执行所有插件拦截器的 afterCompletion()
   ↓
7. 插件停止
   - SbpPluginStoppedEvent 事件触发
   - FilterConfiguration.onPluginStopped() 被调用
   - 从 PluginInterceptorManager 注销插件的所有拦截器
```

## 与方案1（Filter）的对比

| 维度 | 方案1：Filter | 方案2：Interceptor |
|------|--------------|-------------------|
| **工作层次** | Servlet 容器层 | Spring MVC 层 |
| **拦截范围** | 所有请求（包括静态资源） | 仅 Controller 请求 |
| **执行时机** | 在 DispatcherServlet 之前 | 在 DispatcherServlet 之后，Controller 之前 |
| **访问 Controller 信息** | ❌ 无法访问 | ✅ 可以访问 Handler、ModelAndView 等 |
| **修改请求/响应** | ✅ 可以修改 | ⚠️ 有限制（不能修改已提交的响应） |
| **实现复杂度** | 中等 | 中等 |
| **适用场景** | 编码、认证、日志、跨域等 | 权限检查、日志、性能监控等 |

## 优势

### 1. 解决动态注册问题

✅ **不需要在应用启动后注册拦截器**
- 代理拦截器在主应用启动时注册
- 插件拦截器注册到管理器，不涉及 Spring MVC 配置

### 2. 支持动态添加和删除

✅ **插件启动时自动注册拦截器**
- 监听 `SbpPluginStartedEvent` 事件
- 从插件上下文获取 `HandlerInterceptor` Bean
- 注册到 `PluginInterceptorManager`

✅ **插件停止时自动注销拦截器**
- 监听 `SbpPluginStoppedEvent` 事件
- 从 `PluginInterceptorManager` 注销拦截器
- 不影响其他插件的拦截器

### 3. 保持插件隔离性

✅ **插件拦截器由插件容器管理**
- 拦截器 Bean 在插件的 Spring 容器中
- 可以注入插件中的其他 Bean
- 插件停止时，拦截器随插件容器一起销毁

### 4. 访问 Controller 信息

✅ **可以访问 Controller 信息**
- 可以访问 Handler（Controller 方法）
- 可以访问 ModelAndView
- 可以在 Controller 执行前后进行处理

## 注意事项

### 1. 拦截器执行顺序

代理拦截器的执行顺序由 `order()` 方法指定：

```java
registry.addInterceptor(pluginDelegatingInterceptor)
        .addPathPatterns("/**")
        .order(1);  // 数字越小，优先级越高
```

插件拦截器的执行顺序由注册顺序决定（`ConcurrentHashMap` 的迭代顺序）。

**建议**：
- 如果需要控制插件拦截器的执行顺序，可以使用 `LinkedHashMap` 或 `TreeMap`
- 或者在拦截器中使用 `@Order` 注解，在管理器中排序

### 2. 拦截器的生命周期

- **创建**：由插件的 Spring 容器创建和管理
- **注册**：注册到 `PluginInterceptorManager`
- **执行**：由 `PluginDelegatingInterceptor` 调用
- **注销**：插件停止时，从 `PluginInterceptorManager` 注销
- **销毁**：插件停止时，随插件容器一起销毁

### 3. URL 模式匹配

代理拦截器拦截所有请求（`/**`），插件拦截器可以在 `preHandle()` 方法中判断是否需要拦截：

```java
@Override
public boolean preHandle(HttpServletRequest request, 
                       HttpServletResponse response, 
                       Object handler) throws Exception {
    
    String requestURI = request.getRequestURI();
    
    // 只拦截活动相关的接口
    if (requestURI != null && requestURI.startsWith("/api/activities")) {
        // 执行拦截逻辑
        // ...
    }
    
    return true;
}
```

### 4. 跨容器 Bean 引用

插件拦截器只能注入插件容器中的 Bean，不能注入主应用的 Bean：

```java
@Component
public class ActivityPluginInterceptor implements HandlerInterceptor {
    
    @Autowired
    private ActivityPluginProperties pluginProperties;  // ✅ 插件中的 Bean
    
    // @Autowired
    // private MainAppService mainAppService;  // ❌ 主应用的 Bean，无法注入
}
```

## 方案1 + 方案2：Filter 和 Interceptor 配合使用

在实际项目中，可以同时使用 Filter 和 Interceptor，发挥各自的优势：

### 执行顺序

```
HTTP 请求
    ↓
1. PluginDelegatingFilter
   - 执行插件 Filter（Servlet 层）
   - 基础检查（如插件过期、编码、认证等）
    ↓
2. DispatcherServlet
    ↓
3. PluginDelegatingInterceptor.preHandle()
   - 执行插件 Interceptor（Spring MVC 层）
   - 业务检查（如权限、日志等）
    ↓
4. Controller 方法执行
    ↓
5. PluginDelegatingInterceptor.postHandle()
   - 修改 ModelAndView
    ↓
6. 视图渲染
    ↓
7. PluginDelegatingInterceptor.afterCompletion()
   - 清理资源、记录日志
    ↓
HTTP 响应
```

### 职责划分

| 组件 | 职责 | 示例 |
|------|------|------|
| **Filter** | 基础检查 | 插件过期检查、编码设置、认证等 |
| **Interceptor** | 业务检查 | 权限检查、日志记录、性能监控等 |

## 总结

### 核心思想

**使用代理模式，在主应用启动时注册代理拦截器，动态查找并执行插件中的拦截器。**

### 关键组件

1. **PluginInterceptorManager**：管理所有插件拦截器
2. **PluginDelegatingInterceptor**：代理拦截器，动态执行插件拦截器
3. **WebMvcConfiguration**：注册代理拦截器到 Spring MVC
4. **FilterConfiguration**：监听插件事件，注册和注销拦截器
5. **ActivityPluginInterceptor**：插件中的拦截器

### 优势

- ✅ 解决动态注册问题
- ✅ 支持动态添加和删除拦截器
- ✅ 保持插件隔离性（拦截器由插件容器管理）
- ✅ 可以访问 Controller 信息（Handler、ModelAndView 等）
- ✅ 适合处理业务逻辑相关的拦截

### 适用场景

- 插件中需要使用 Spring MVC 拦截器
- 需要访问 Controller 信息（Handler、ModelAndView 等）
- 需要在 Controller 执行前后进行处理
- 需要修改 ModelAndView

### 与方案1的关系

- **方案1（Filter）**：适合基础检查，在 Servlet 层面工作
- **方案2（Interceptor）**：适合业务检查，在 Spring MVC 层面工作
- **可以同时使用**：Filter 负责基础检查，Interceptor 负责业务检查

这种方案是插件框架中处理 Spring MVC 拦截器的标准模式，既解决了动态注册的问题，又保持了良好的架构设计。

