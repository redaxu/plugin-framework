# FilterConfiguration 修复说明

## 问题描述

在插件启动时，需要将插件中的 Filter 注册到主应用的 ServletContext。原有的 `FilterConfiguration` 实现存在以下问题：

### 问题 1：错误地将 Event Source 转换为 ApplicationContext

**错误代码**：
```java
@Override
public void onApplicationEvent(SbpPluginStartedEvent event) {
    registerPluginFilters((ApplicationContext)event.getSource());  // ❌ 错误
}
```

**问题分析**：
- `SbpPluginStartedEvent` 的 source 是 `SpringBootPlugin` 对象，不是 `ApplicationContext`
- 直接将 source 转换为 `ApplicationContext` 会导致 `ClassCastException`
- 需要先将 source 转换为 `SpringBootPlugin`，再通过 `plugin.getApplicationContext()` 获取插件的 Spring 上下文

### 问题 2：缺少详细的日志输出

原有代码缺少足够的日志输出，导致调试困难，无法确定：
- 事件是否被触发
- 插件上下文是否成功获取
- Filter Bean 是否存在
- 注册过程是否成功

### 问题 3：硬编码 Filter Bean 名称

原有代码硬编码了 Filter Bean 名称（`activityPluginFilter`），不够灵活：
- 只能注册特定名称的 Filter
- 无法支持多个 Filter
- 扩展性差

## 解决方案

### 修复 1：正确获取插件的 ApplicationContext

**修复后的代码**：
```java
@Override
public void onApplicationEvent(SbpPluginStartedEvent event) {
    System.out.println("FilterConfiguration - 收到插件启动事件: " + event);
    
    // SbpPluginStartedEvent 的 source 是插件对象 (SpringBootPlugin)
    Object source = event.getSource();
    System.out.println("FilterConfiguration - Event source type: " + source.getClass().getName());
    
    if (source instanceof SpringBootPlugin) {
        SpringBootPlugin plugin = (SpringBootPlugin) source;
        System.out.println("FilterConfiguration - 插件ID: " + plugin.getWrapper().getPluginId());
        
        // 从插件对象获取其 ApplicationContext
        ApplicationContext pluginContext = plugin.getApplicationContext();
        if (pluginContext != null) {
            System.out.println("FilterConfiguration - 成功获取插件上下文: " + pluginContext);
            registerPluginFilters(pluginContext);
        } else {
            System.out.println("FilterConfiguration - 插件上下文为 null");
        }
    } else {
        System.out.println("FilterConfiguration - Event source 不是 SpringBootPlugin 类型");
    }
}
```

**关键改进**：
1. ✅ 先判断 source 是否是 `SpringBootPlugin` 类型
2. ✅ 通过 `plugin.getApplicationContext()` 获取插件上下文
3. ✅ 添加详细的日志输出，方便调试

### 修复 2：动态获取所有 Filter Bean

**修复后的代码**：
```java
private void registerPluginFilters(ApplicationContext pluginContext) {
    if (servletContext == null) {
        System.out.println("FilterConfiguration - servletContext is null，无法注册 Filter");
        return;
    }

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
                    // 注册到主应用的 ServletContext
                    registerFilterToMainContext(filter, beanName);
                    System.out.println("FilterConfiguration - 成功注册插件 Filter [" + beanName + "] 到主应用");
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
```

**关键改进**：
1. ✅ 使用 `getBeanNamesForType(Filter.class)` 动态获取所有 Filter Bean
2. ✅ 支持注册多个 Filter
3. ✅ 添加详细的日志输出
4. ✅ 每个 Filter 独立处理，一个失败不影响其他 Filter

### 修复 3：优化 Filter 注册逻辑

**修复后的代码**：
```java
private void registerFilterToMainContext(Filter filter, String filterName) {
    try {
        System.out.println("FilterConfiguration - 开始注册 Filter: " + filterName + " (" + filter.getClass().getName() + ")");
        
        // 创建 FilterRegistrationBean 并注册
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        
        // 根据 Filter 名称设置不同的 URL 模式
        if (filterName.contains("activity")) {
            registration.addUrlPatterns("/api/activities", "/api/activities/*", "/api/activities/**");
            System.out.println("FilterConfiguration - 设置 URL 模式: /api/activities/**");
        } else {
            // 默认拦截所有请求
            registration.addUrlPatterns("/*");
            System.out.println("FilterConfiguration - 设置 URL 模式: /*");
        }
        
        registration.setName(filterName);
        registration.setOrder(1);
        registration.setEnabled(true);
        
        // 调用 onStartup 将 Filter 注册到 ServletContext
        registration.onStartup(servletContext);
        
        System.out.println("FilterConfiguration - Filter [" + filterName + "] 已成功注册到主应用的 ServletContext");
    } catch (Exception e) {
        System.out.println("FilterConfiguration - 注册 Filter [" + filterName + "] 到 ServletContext 失败: " + e.getMessage());
        e.printStackTrace();
    }
}
```

**关键改进**：
1. ✅ 根据 Filter 名称动态设置 URL 模式
2. ✅ 添加详细的日志输出
3. ✅ 异常处理更加完善

## 修复前后对比

| 维度 | 修复前 | 修复后 |
|------|--------|--------|
| **Event Source 处理** | ❌ 直接转换为 ApplicationContext | ✅ 先转换为 SpringBootPlugin，再获取 ApplicationContext |
| **Filter 获取方式** | ❌ 硬编码 Bean 名称 | ✅ 动态获取所有 Filter Bean |
| **日志输出** | ❌ 日志不足，调试困难 | ✅ 详细的日志输出，方便调试 |
| **异常处理** | ⚠️ 简单的异常捕获 | ✅ 每个 Filter 独立处理，一个失败不影响其他 |
| **扩展性** | ❌ 只能注册一个特定名称的 Filter | ✅ 支持注册多个 Filter |
| **URL 模式** | ⚠️ 硬编码 URL 模式 | ✅ 根据 Filter 名称动态设置 |

## 验证方法

### 1. 启动主应用

```bash
cd plugin-manager
mvn spring-boot:run
```

### 2. 观察日志输出

启动成功后，应该看到以下日志：

```
FilterConfiguration - 收到插件启动事件: org.laxture.sbp.spring.boot.SbpPluginStartedEvent[source=plugin.com.gaoding.ska.plugin.activity.ActivityPlugin@xxxxx]
FilterConfiguration - Event source type: plugin.com.gaoding.ska.plugin.activity.ActivityPlugin
FilterConfiguration - 插件ID: activity-plugin
FilterConfiguration - 成功获取插件上下文: org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext@xxxxx
FilterConfiguration - 开始从插件上下文获取 Filter Bean
FilterConfiguration - 找到 1 个 Filter Bean
FilterConfiguration - 发现 Filter Bean: activityPluginFilter
FilterConfiguration - 开始注册 Filter: activityPluginFilter (config.com.gaoding.ska.plugin.activity.ActivityPluginFilter)
FilterConfiguration - 设置 URL 模式: /api/activities/**
FilterConfiguration - Filter [activityPluginFilter] 已成功注册到主应用的 ServletContext
FilterConfiguration - 成功注册插件 Filter [activityPluginFilter] 到主应用
```

### 3. 测试 Filter 是否生效

```bash
# 测试创建活动
curl -X POST http://localhost:8080/api/activities \
  -H 'Content-Type: application/json' \
  -d '{"name":"测试活动","description":"测试描述","startTime":"2025-01-10T00:00:00","endTime":"2025-01-20T00:00:00","status":"ACTIVE"}'

# 测试查询活动
curl http://localhost:8080/api/activities
```

观察日志输出，应该看到：

```
ActivityPluginFilter.shouldNotFilter - URI: /api/activities, shouldFilter: true
ActivityPluginFilter.doFilterInternal - 过滤器执行, URI: /api/activities, Method: POST
ActivityPluginFilter - 匹配到活动接口，检查过期状态
ActivityPluginFilter - pluginProperties: config.com.gaoding.ska.plugin.activity.ActivityPluginProperties@xxxxx
ActivityPluginFilter - isExpired: false
```

## 核心技术要点

### 1. SbpPluginStartedEvent 的正确使用

```java
// ❌ 错误：直接转换为 ApplicationContext
ApplicationContext context = (ApplicationContext) event.getSource();

// ✅ 正确：先转换为 SpringBootPlugin，再获取 ApplicationContext
SpringBootPlugin plugin = (SpringBootPlugin) event.getSource();
ApplicationContext context = plugin.getApplicationContext();
```

### 2. 动态获取 Bean

```java
// ❌ 错误：硬编码 Bean 名称
Filter filter = context.getBean("activityPluginFilter", Filter.class);

// ✅ 正确：动态获取所有 Filter Bean
String[] filterBeanNames = context.getBeanNamesForType(Filter.class);
for (String beanName : filterBeanNames) {
    Filter filter = context.getBean(beanName, Filter.class);
    // 注册 Filter
}
```

### 3. FilterRegistrationBean 的使用

```java
FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
registration.setFilter(filter);                    // 设置 Filter 实例
registration.addUrlPatterns("/api/activities/**"); // 设置 URL 模式
registration.setName(filterName);                  // 设置 Filter 名称
registration.setOrder(1);                          // 设置优先级
registration.setEnabled(true);                     // 启用 Filter
registration.onStartup(servletContext);            // 注册到 ServletContext
```

## 总结

本次修复解决了以下问题：

1. ✅ **正确获取插件上下文**：通过 `SpringBootPlugin.getApplicationContext()` 方法
2. ✅ **动态注册所有 Filter**：支持插件中定义多个 Filter
3. ✅ **详细的日志输出**：方便调试和问题排查
4. ✅ **更好的异常处理**：每个 Filter 独立处理，提高健壮性
5. ✅ **更好的扩展性**：支持不同类型的 Filter，动态设置 URL 模式

修复后的 `FilterConfiguration` 可以正确地将插件中的 Filter 注册到主应用的 ServletContext，实现插件 Filter 的功能。

## 相关文档

- [Filter注册机制说明.md](Filter注册机制说明.md) - Filter 注册机制的详细说明
- [README.md](../README.md) - 项目整体说明文档

