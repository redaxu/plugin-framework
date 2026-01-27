# Bean 重复注册问题解决

## 问题描述

启动应用时报错：

```
Description:

The bean 'pluginDelegatingFilter', defined in class path resource [com/gaoding/ska/customize/config/FilterConfiguration.class], could not be registered. A bean with that name has already been defined in file [/Users/gaoding/projects/IdeaProjects/ska-plugin-framework-demo/plugin-manager/target/classes/com/gaoding/ska/customize/config/PluginDelegatingFilter.class] and overriding is disabled.

Action:

Consider renaming one of the beans or enabling overriding by setting spring.main.allow-bean-definition-overriding=true
```

## 问题原因

`PluginDelegatingFilter` 被注册了两次：

1. **第一次注册**：通过 `@Component` 注解自动注册
   ```java
   @Component  // ← 自动注册为 Bean
   public class PluginDelegatingFilter implements Filter {
       // ...
   }
   ```

2. **第二次注册**：通过 `FilterConfiguration` 中的 `@Bean` 方法注册
   ```java
   @Configuration
   public class FilterConfiguration {
       
       @Bean  // ← 再次注册为 Bean
       public FilterRegistrationBean<PluginDelegatingFilter> pluginDelegatingFilter(
               PluginDelegatingFilter filter) {
           // ...
       }
   }
   ```

由于 Spring Boot 默认禁止 Bean 覆盖（`spring.main.allow-bean-definition-overriding=false`），因此启动失败。

## 解决方案

### 方案1：移除 @Component 注解（推荐）

**步骤1**：从 `PluginDelegatingFilter` 移除 `@Component` 注解

```java
// @Component  ← 移除此注解
public class PluginDelegatingFilter implements Filter {
    
    private PluginFilterManager pluginFilterManager;
    
    // 由于不使用 @Component，需要通过 setter 方法注入依赖
    @Autowired
    public void setPluginFilterManager(PluginFilterManager pluginFilterManager) {
        this.pluginFilterManager = pluginFilterManager;
    }
    
    // ...
}
```

**步骤2**：在 `FilterConfiguration` 中显式创建 Bean

```java
@Configuration
public class FilterConfiguration {

    /**
     * 创建代理 Filter Bean
     */
    @Bean
    public PluginDelegatingFilter pluginDelegatingFilter() {
        return new PluginDelegatingFilter();
    }
    
    /**
     * 注册代理 Filter
     */
    @Bean
    public FilterRegistrationBean<PluginDelegatingFilter> pluginDelegatingFilterRegistration(
            PluginDelegatingFilter filter) {
        
        FilterRegistrationBean<PluginDelegatingFilter> registration = 
            new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setName("pluginDelegatingFilter");
        registration.setOrder(1);
        
        return registration;
    }
}
```

**优点**：
- ✅ 明确控制 Bean 的创建和注册
- ✅ 避免重复注册
- ✅ 符合 Spring 最佳实践

### 方案2：启用 Bean 覆盖（不推荐）

在 `application.yml` 中添加配置：

```yaml
spring:
  main:
    allow-bean-definition-overriding: true
```

**缺点**：
- ❌ 可能隐藏其他 Bean 冲突问题
- ❌ 不符合 Spring Boot 最佳实践
- ❌ 可能导致意外的 Bean 覆盖

### 方案3：重命名 Bean（不推荐）

修改 `@Bean` 方法的名称：

```java
@Bean
public FilterRegistrationBean<PluginDelegatingFilter> pluginDelegatingFilterRegistration(
        PluginDelegatingFilter filter) {
    // ...
}
```

**缺点**：
- ❌ 仍然存在两个相同类型的 Bean
- ❌ 可能导致混淆

## 为什么 PluginDelegatingInterceptor 不需要修改？

`PluginDelegatingInterceptor` 使用 `@Component` 注解，并通过 `@Autowired` 注入到 `WebMvcConfiguration` 中：

```java
@Component  // ← 使用 @Component 注解
public class PluginDelegatingInterceptor implements HandlerInterceptor {
    // ...
}

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    
    @Autowired  // ← 通过 @Autowired 注入，不是通过 @Bean 创建
    private PluginDelegatingInterceptor pluginDelegatingInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pluginDelegatingInterceptor)
                .addPathPatterns("/**")
                .order(1);
    }
}
```

**关键区别**：
- `PluginDelegatingFilter`：通过 `@Bean` 方法创建 `FilterRegistrationBean`，需要显式创建 Filter 实例
- `PluginDelegatingInterceptor`：通过 `@Autowired` 注入，由 Spring 容器自动管理

## 依赖注入方式对比

### @Component + @Autowired（PluginDelegatingInterceptor）

```java
@Component
public class PluginDelegatingInterceptor implements HandlerInterceptor {
    
    @Autowired  // ← 字段注入
    private PluginInterceptorManager pluginInterceptorManager;
    
    // ...
}
```

**优点**：
- ✅ 简单直接
- ✅ Spring 自动管理生命周期

**缺点**：
- ⚠️ 不能与 `@Bean` 方法同时使用（会导致重复注册）

### @Bean + Setter 注入（PluginDelegatingFilter）

```java
public class PluginDelegatingFilter implements Filter {
    
    private PluginFilterManager pluginFilterManager;
    
    @Autowired  // ← Setter 注入
    public void setPluginFilterManager(PluginFilterManager pluginFilterManager) {
        this.pluginFilterManager = pluginFilterManager;
    }
    
    // ...
}

@Configuration
public class FilterConfiguration {
    
    @Bean
    public PluginDelegatingFilter pluginDelegatingFilter() {
        return new PluginDelegatingFilter();
    }
}
```

**优点**：
- ✅ 明确控制 Bean 创建
- ✅ 可以在 `@Bean` 方法中进行额外配置
- ✅ 避免重复注册

**缺点**：
- ⚠️ 需要额外的 Setter 方法

## 最佳实践

### 1. Filter 注册

对于需要通过 `FilterRegistrationBean` 注册的 Filter：

```java
// 不使用 @Component
public class MyFilter implements Filter {
    
    private SomeDependency dependency;
    
    @Autowired
    public void setDependency(SomeDependency dependency) {
        this.dependency = dependency;
    }
}

@Configuration
public class FilterConfig {
    
    @Bean
    public MyFilter myFilter() {
        return new MyFilter();
    }
    
    @Bean
    public FilterRegistrationBean<MyFilter> myFilterRegistration(MyFilter filter) {
        FilterRegistrationBean<MyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
```

### 2. Interceptor 注册

对于需要通过 `InterceptorRegistry` 注册的 Interceptor：

```java
// 使用 @Component
@Component
public class MyInterceptor implements HandlerInterceptor {
    
    @Autowired
    private SomeDependency dependency;
}

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private MyInterceptor myInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(myInterceptor)
                .addPathPatterns("/**");
    }
}
```

## 总结

### 问题

- ❌ `PluginDelegatingFilter` 通过 `@Component` 和 `@Bean` 两种方式注册，导致重复注册

### 解决方案

- ✅ 移除 `@Component` 注解
- ✅ 通过 `@Bean` 方法显式创建 Bean
- ✅ 使用 Setter 方法注入依赖

### 关键点

1. **Filter 注册**：不使用 `@Component`，通过 `@Bean` + `FilterRegistrationBean`
2. **Interceptor 注册**：使用 `@Component`，通过 `@Autowired` 注入
3. **避免重复注册**：同一个类不要同时使用 `@Component` 和 `@Bean`
4. **依赖注入**：移除 `@Component` 后，使用 Setter 方法注入依赖

### 相关文件

- `PluginDelegatingFilter.java` - 已修复
- `FilterConfiguration.java` - 已修复
- `PluginDelegatingInterceptor.java` - 无需修改
- `WebMvcConfiguration.java` - 无需修改

