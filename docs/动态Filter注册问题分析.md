# 动态 Filter 注册问题分析

## 问题描述

在主应用已经启动后，使用 `FilterRegistrationBean.onStartup(servletContext)` 方法注册 Filter 可能无法成功。

### 原因分析

1. **ServletContext 生命周期限制**
   - `ServletContext.addFilter()` 方法只能在 ServletContext 初始化期间调用
   - 一旦 ServletContext 初始化完成（应用启动完成），就无法再添加新的 Filter
   - Servlet 3.0 规范明确规定了这一限制

2. **FilterRegistrationBean.onStartup() 的设计**
   - `onStartup()` 方法设计用于应用启动时注册 Filter
   - 内部调用 `ServletContext.addFilter()`
   - 如果在应用启动后调用，会抛出 `IllegalStateException`

3. **错误信息**
   ```
   java.lang.IllegalStateException: Unable to configure filter [xxx] as the servlet context has been initialized
   ```

## 解决方案

### 方案 1：使用 DelegatingFilterProxy（推荐）

通过在主应用启动时注册一个代理 Filter，该 Filter 动态查找并调用插件中的 Filter。

#### 实现步骤

**1. 创建插件 Filter 管理器**

```java
@Component
public class PluginFilterManager {
    
    private final Map<String, Filter> pluginFilters = new ConcurrentHashMap<>();
    
    /**
     * 注册插件 Filter
     */
    public void registerFilter(String filterName, Filter filter) {
        pluginFilters.put(filterName, filter);
        System.out.println("PluginFilterManager - 注册 Filter: " + filterName);
    }
    
    /**
     * 注销插件 Filter
     */
    public void unregisterFilter(String filterName) {
        pluginFilters.remove(filterName);
        System.out.println("PluginFilterManager - 注销 Filter: " + filterName);
    }
    
    /**
     * 获取所有插件 Filter
     */
    public Collection<Filter> getAllFilters() {
        return pluginFilters.values();
    }
}
```

**2. 创建代理 Filter**

```java
@Component
public class PluginDelegatingFilter implements Filter {
    
    @Autowired
    private PluginFilterManager pluginFilterManager;
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 初始化代理 Filter
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        // 创建一个包含所有插件 Filter 的 Filter 链
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
    
    @Override
    public void destroy() {
        // 销毁代理 Filter
    }
    
    /**
     * 插件 Filter 链
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
                nextFilter.doFilter(request, response, this);
            } else {
                // 所有插件 Filter 执行完毕，执行原始 Filter 链
                originalChain.doFilter(request, response);
            }
        }
    }
}
```

**3. 在主应用启动时注册代理 Filter**

```java
@Configuration
public class FilterConfiguration {
    
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
}
```

**4. 监听插件启动事件，注册插件 Filter**

```java
@Configuration
public class PluginFilterRegistrar implements ApplicationListener<SbpPluginStartedEvent> {
    
    @Autowired
    private PluginFilterManager pluginFilterManager;
    
    @Override
    public void onApplicationEvent(SbpPluginStartedEvent event) {
        Object source = event.getSource();
        
        if (source instanceof SpringBootPlugin) {
            SpringBootPlugin plugin = (SpringBootPlugin) source;
            ApplicationContext pluginContext = plugin.getApplicationContext();
            
            if (pluginContext != null) {
                // 从插件上下文中获取所有 Filter
                String[] filterBeanNames = pluginContext.getBeanNamesForType(Filter.class);
                
                for (String beanName : filterBeanNames) {
                    Filter filter = pluginContext.getBean(beanName, Filter.class);
                    // 注册到 PluginFilterManager
                    pluginFilterManager.registerFilter(beanName, filter);
                }
            }
        }
    }
}
```

### 方案 2：使用 Spring MVC 拦截器（备选）

如果只需要拦截 Spring MVC 的请求，可以使用拦截器代替 Filter。

#### 实现步骤

**1. 创建插件拦截器管理器**

```java
@Component
public class PluginInterceptorManager {
    
    private final List<HandlerInterceptor> pluginInterceptors = new CopyOnWriteArrayList<>();
    
    public void registerInterceptor(HandlerInterceptor interceptor) {
        pluginInterceptors.add(interceptor);
    }
    
    public void unregisterInterceptor(HandlerInterceptor interceptor) {
        pluginInterceptors.remove(interceptor);
    }
    
    public List<HandlerInterceptor> getAllInterceptors() {
        return new ArrayList<>(pluginInterceptors);
    }
}
```

**2. 配置 Spring MVC 拦截器**

```java
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    
    @Autowired
    private PluginInterceptorManager pluginInterceptorManager;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加代理拦截器
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   Object handler) throws Exception {
                // 执行所有插件拦截器
                for (HandlerInterceptor interceptor : pluginInterceptorManager.getAllInterceptors()) {
                    if (!interceptor.preHandle(request, response, handler)) {
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
                // 执行所有插件拦截器
                for (HandlerInterceptor interceptor : pluginInterceptorManager.getAllInterceptors()) {
                    interceptor.postHandle(request, response, handler, modelAndView);
                }
            }
            
            @Override
            public void afterCompletion(HttpServletRequest request, 
                                      HttpServletResponse response, 
                                      Object handler, 
                                      Exception ex) throws Exception {
                // 执行所有插件拦截器
                for (HandlerInterceptor interceptor : pluginInterceptorManager.getAllInterceptors()) {
                    interceptor.afterCompletion(request, response, handler, ex);
                }
            }
        }).addPathPatterns("/**");
    }
}
```

**3. 在插件中定义拦截器**

```java
@Component
public class ActivityPluginInterceptor implements HandlerInterceptor {
    
    @Autowired
    private ActivityPluginProperties pluginProperties;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        
        if (requestURI != null && requestURI.startsWith("/api/activities")) {
            // 检查插件是否过期
            if (pluginProperties != null && pluginProperties.isExpired()) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                // ... 返回错误响应
                return false;
            }
        }
        
        return true;
    }
}
```

## 方案对比

| 维度 | 方案 1：DelegatingFilterProxy | 方案 2：Spring MVC 拦截器 |
|------|------------------------------|--------------------------|
| **适用范围** | 所有 Servlet 请求 | 仅 Spring MVC 请求 |
| **实现复杂度** | 中等 | 简单 |
| **性能** | 较好（Filter 级别） | 较好（拦截器级别） |
| **灵活性** | 高（可拦截所有请求） | 中（仅拦截 Controller 请求） |
| **动态性** | 支持动态添加/删除 | 支持动态添加/删除 |
| **推荐度** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |

## 推荐方案

**推荐使用方案 1（DelegatingFilterProxy）**，原因如下：

1. ✅ **适用范围广**：可以拦截所有 Servlet 请求，包括静态资源
2. ✅ **符合 Filter 语义**：插件中定义的是 Filter，使用 Filter 代理更合理
3. ✅ **动态性强**：支持在运行时动态添加和删除 Filter
4. ✅ **性能好**：Filter 在 Servlet 层面拦截，性能更好
5. ✅ **兼容性好**：不依赖 Spring MVC，适用于各种 Web 应用

## 总结

**关键点**：

1. ❌ **不能在应用启动后直接注册 Filter**：`ServletContext.addFilter()` 只能在初始化期间调用
2. ✅ **使用代理模式**：在主应用启动时注册一个代理 Filter，动态查找并调用插件中的 Filter
3. ✅ **使用管理器模式**：通过 `PluginFilterManager` 管理插件 Filter 的注册和注销
4. ✅ **事件驱动**：监听插件启动事件，将插件 Filter 注册到管理器

**工作流程**：

```
1. 主应用启动
   - 注册 PluginDelegatingFilter（代理 Filter）
   ↓
2. 插件启动
   - 插件中的 Filter 被创建为 Bean
   ↓
3. 监听插件启动事件
   - 从插件上下文获取 Filter Bean
   - 注册到 PluginFilterManager
   ↓
4. HTTP 请求到达
   - PluginDelegatingFilter 拦截请求
   - 从 PluginFilterManager 获取所有插件 Filter
   - 依次执行插件 Filter
   - 执行原始 Filter 链
```

这种方案既解决了动态注册的问题，又保持了良好的架构设计。

