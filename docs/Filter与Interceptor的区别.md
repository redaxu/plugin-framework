# Filter 与 Interceptor 的区别

## 问题

**方案1（DelegatingFilterProxy）可以拦截插件中的 Spring MVC 拦截器吗？**

## 答案

**❌ 不能！Filter 和 Interceptor 是两个不同层次的概念，Filter 不能"拦截" Interceptor。**

但是，**Filter 会在 Interceptor 之前执行**，可以在请求到达 Interceptor 之前进行拦截和处理。

## Filter 和 Interceptor 的区别

### 1. 工作层次不同

```
HTTP 请求
    ↓
┌─────────────────────────────────────────┐
│  Servlet 容器层（Tomcat/Jetty）         │
│  ┌───────────────────────────────────┐  │
│  │  Filter（过滤器）                  │  │  ← Servlet 规范
│  │  - 在 Servlet 层面工作             │  │
│  │  - 可以拦截所有请求                │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  DispatcherServlet                      │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  Spring MVC 层                          │
│  ┌───────────────────────────────────┐  │
│  │  HandlerInterceptor（拦截器）     │  │  ← Spring MVC 规范
│  │  - 在 Spring MVC 层面工作         │  │
│  │  - 只能拦截 Controller 请求       │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  Controller（控制器）                   │
└─────────────────────────────────────────┘
```

### 2. 执行顺序

```
HTTP 请求
    ↓
1. Filter.doFilter()           ← 最先执行（Servlet 层）
    ↓
2. DispatcherServlet           ← Spring MVC 入口
    ↓
3. HandlerInterceptor.preHandle()    ← 在 Controller 之前执行
    ↓
4. Controller 方法执行
    ↓
5. HandlerInterceptor.postHandle()   ← 在 Controller 之后执行
    ↓
6. 视图渲染
    ↓
7. HandlerInterceptor.afterCompletion()  ← 请求完成后执行
    ↓
8. Filter 链继续执行（返回）
    ↓
HTTP 响应
```

### 3. 核心区别

| 维度 | Filter（过滤器） | Interceptor（拦截器） |
|------|-----------------|---------------------|
| **规范** | Servlet 规范 | Spring MVC 规范 |
| **工作层次** | Servlet 容器层 | Spring MVC 层 |
| **依赖** | 依赖 Servlet 容器 | 依赖 Spring MVC |
| **拦截范围** | 所有请求（包括静态资源） | 仅 Controller 请求 |
| **执行时机** | 在 DispatcherServlet 之前 | 在 DispatcherServlet 之后，Controller 之前 |
| **访问 Spring Bean** | 需要通过 `@Autowired` 注入 | 可以直接访问 Spring Bean |
| **访问 Controller 信息** | ❌ 无法访问 | ✅ 可以访问 Handler、ModelAndView 等 |
| **修改请求/响应** | ✅ 可以修改 | ⚠️ 有限制（不能修改已提交的响应） |
| **适用场景** | 编码、认证、日志、跨域等 | 权限检查、日志、性能监控等 |

## 插件框架中的应用

### 场景 1：插件中定义 Filter

```java
@Component
public class ActivityPluginFilter extends OncePerRequestFilter {
    
    @Autowired
    private ActivityPluginProperties pluginProperties;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) 
            throws ServletException, IOException {
        
        // 在 Servlet 层面拦截请求
        if (pluginProperties.isExpired()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;  // 直接返回，不继续执行
        }
        
        // 继续执行 Filter 链和后续的 Interceptor、Controller
        filterChain.doFilter(request, response);
    }
}
```

**特点**：
- ✅ 可以在请求到达 DispatcherServlet 之前拦截
- ✅ 可以直接返回响应，阻止请求继续执行
- ✅ 可以拦截所有类型的请求（包括静态资源）
- ❌ 无法访问 Controller 信息（Handler、ModelAndView 等）

### 场景 2：插件中定义 Interceptor

```java
@Component
public class ActivityPluginInterceptor implements HandlerInterceptor {
    
    @Autowired
    private ActivityPluginProperties pluginProperties;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) throws Exception {
        
        // 在 Spring MVC 层面拦截请求
        if (pluginProperties.isExpired()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;  // 返回 false，阻止 Controller 执行
        }
        
        return true;  // 继续执行 Controller
    }
    
    @Override
    public void postHandle(HttpServletRequest request, 
                         HttpServletResponse response, 
                         Object handler, 
                         ModelAndView modelAndView) throws Exception {
        // 在 Controller 执行后，视图渲染前执行
        // 可以修改 ModelAndView
    }
}
```

**特点**：
- ✅ 可以访问 Controller 信息（Handler、ModelAndView 等）
- ✅ 可以在 Controller 执行前后进行处理
- ✅ 更适合处理业务逻辑相关的拦截
- ❌ 只能拦截 Controller 请求，无法拦截静态资源
- ❌ 在 Filter 之后执行，无法阻止 Filter 的执行

### 场景 3：Filter 和 Interceptor 配合使用

```java
// Filter：在 Servlet 层面进行基础检查
@Component
public class ActivityPluginFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) 
            throws ServletException, IOException {
        
        // 1. 基础检查（如插件是否过期）
        if (isPluginExpired()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }
        
        // 2. 继续执行
        filterChain.doFilter(request, response);
    }
}

// Interceptor：在 Spring MVC 层面进行业务检查
@Component
public class ActivityPluginInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) throws Exception {
        
        // 1. 业务权限检查（如用户是否有权限访问活动）
        if (!hasPermission(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;
        }
        
        return true;
    }
    
    @Override
    public void postHandle(HttpServletRequest request, 
                         HttpServletResponse response, 
                         Object handler, 
                         ModelAndView modelAndView) throws Exception {
        
        // 2. 修改返回数据（如添加额外信息）
        if (modelAndView != null) {
            modelAndView.addObject("pluginVersion", "1.0.0");
        }
    }
}
```

**执行顺序**：

```
HTTP 请求
    ↓
1. ActivityPluginFilter.doFilterInternal()
   - 检查插件是否过期
   - 如果过期，直接返回 403
   - 如果未过期，继续执行
    ↓
2. DispatcherServlet
    ↓
3. ActivityPluginInterceptor.preHandle()
   - 检查用户权限
   - 如果无权限，返回 false，阻止 Controller 执行
   - 如果有权限，返回 true，继续执行
    ↓
4. Controller 方法执行
    ↓
5. ActivityPluginInterceptor.postHandle()
   - 修改 ModelAndView
    ↓
6. 视图渲染
    ↓
7. ActivityPluginInterceptor.afterCompletion()
    ↓
HTTP 响应
```

## 插件中的 Interceptor 如何注册？

### 问题

插件中的 Interceptor 需要注册到插件的 `WebMvcConfigurer` 中，但在插件环境中可能不会自动生效。

### 原因

1. **Interceptor 需要注册到 HandlerMapping**
   - Interceptor 是 Spring MVC 的概念，需要注册到 `RequestMappingHandlerMapping`
   - 插件的 `WebMvcConfigurer` 可能不会被主应用的 `DispatcherServlet` 识别

2. **插件的 Spring 容器是独立的**
   - 插件有自己的 `RequestMappingHandlerMapping`
   - 但这个 `HandlerMapping` 可能没有正确注册 Interceptor

### 解决方案 1：在插件中手动注册 Interceptor

```java
@Configuration
@OnPluginMode
public class ActivityPluginConfiguration implements WebMvcConfigurer {
    
    @Autowired
    private ActivityPluginInterceptor activityPluginInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(activityPluginInterceptor)
                .addPathPatterns("/api/activities/**");
    }
}
```

**注意**：这种方式依赖于 SBP 框架是否支持插件的 `WebMvcConfigurer`。

### 解决方案 2：使用 Filter 代替 Interceptor（推荐）

由于插件环境的特殊性，**推荐在插件中使用 Filter 而不是 Interceptor**：

```java
@Component
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
        
        // 实现拦截逻辑
        if (pluginProperties.isExpired()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}
```

**优点**：
- ✅ Filter 在 Servlet 层面工作，不依赖 Spring MVC
- ✅ 通过方案1（DelegatingFilterProxy）可以动态注册
- ✅ 可以拦截所有请求，包括静态资源
- ✅ 更适合插件框架的架构

### 解决方案 3：在主应用中注册代理 Interceptor

如果确实需要使用 Interceptor，可以在主应用中注册一个代理 Interceptor：

```java
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    
    @Autowired
    private PluginInterceptorManager pluginInterceptorManager;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   Object handler) throws Exception {
                // 执行所有插件 Interceptor
                for (HandlerInterceptor interceptor : pluginInterceptorManager.getAllInterceptors()) {
                    if (!interceptor.preHandle(request, response, handler)) {
                        return false;
                    }
                }
                return true;
            }
        }).addPathPatterns("/**");
    }
}
```

这种方式类似于方案1的 DelegatingFilterProxy，但用于 Interceptor。

## 总结

### Filter vs Interceptor

| 问题 | 答案 |
|------|------|
| **Filter 可以拦截 Interceptor 吗？** | ❌ 不能，它们是不同层次的概念 |
| **Filter 会在 Interceptor 之前执行吗？** | ✅ 是的，Filter 先执行 |
| **Filter 可以阻止 Interceptor 执行吗？** | ✅ 可以，Filter 可以直接返回响应 |
| **Interceptor 可以阻止 Filter 执行吗？** | ❌ 不能，Interceptor 在 Filter 之后执行 |

### 插件框架中的推荐

1. **推荐使用 Filter**
   - ✅ 更适合插件框架的架构
   - ✅ 可以通过方案1（DelegatingFilterProxy）动态注册
   - ✅ 不依赖 Spring MVC，更通用

2. **如果需要使用 Interceptor**
   - 在插件中定义 Interceptor
   - 在主应用中注册代理 Interceptor（类似 DelegatingFilterProxy）
   - 或者在插件的 `WebMvcConfigurer` 中注册（需要 SBP 框架支持）

3. **Filter 和 Interceptor 配合使用**
   - Filter：基础检查（如插件过期、编码、认证等）
   - Interceptor：业务检查（如权限、日志、性能监控等）

### 方案1（DelegatingFilterProxy）的作用

**方案1 只能管理插件中的 Filter，不能管理 Interceptor。**

如果需要管理插件中的 Interceptor，需要实现类似的代理 Interceptor 机制（方案2）。

但在插件框架中，**推荐使用 Filter 而不是 Interceptor**，因为：
- Filter 更通用，不依赖 Spring MVC
- Filter 可以拦截所有请求，包括静态资源
- Filter 更适合插件框架的动态加载机制

