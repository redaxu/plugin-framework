# Bean 实例不一致问题解决

## 问题描述

在 `FilterConfiguration` 中注册 Filter 时使用的 `PluginFilterManager` 实例，与 `PluginDelegatingFilter` 中获取的 `PluginFilterManager` 实例不是同一个对象，导致：

- Filter 注册到了一个 `PluginFilterManager` 实例
- 但请求时从另一个 `PluginFilterManager` 实例获取 Filter
- 结果是获取到空的 Filter 列表

## 问题原因

### 原有代码

```java
@Configuration
public class FilterConfiguration {

    @Autowired
    private PluginFilterManager pluginFilterManager;  // ← 实例 A

    @Bean
    public PluginDelegatingFilter pluginDelegatingFilter() {
        return new PluginDelegatingFilter();  // ← 创建 Filter
    }
    
    @Bean
    public FilterRegistrationBean<PluginDelegatingFilter> pluginDelegatingFilterRegistration(
            PluginDelegatingFilter filter) {
        // ...
    }
}

public class PluginDelegatingFilter implements Filter {
    
    private PluginFilterManager pluginFilterManager;
    
    @Autowired  // ← Spring 自动注入，可能是实例 B
    public void setPluginFilterManager(PluginFilterManager pluginFilterManager) {
        this.pluginFilterManager = pluginFilterManager;
    }
}
```

### 问题分析

1. **FilterConfiguration 中的 PluginFilterManager**
   - 通过 `@Autowired` 注入到 `FilterConfiguration`
   - 这是实例 A

2. **PluginDelegatingFilter 中的 PluginFilterManager**
   - 通过 `@Autowired` setter 方法注入
   - 但由于 `PluginDelegatingFilter` 是通过 `new` 关键字创建的
   - Spring 可能在不同的时机注入，导致注入了不同的实例（实例 B）

3. **为什么会有多个实例？**
   - 虽然 `PluginFilterManager` 有 `@Component` 注解，应该是单例
   - 但在某些情况下（如类加载器隔离、Spring 上下文隔离等），可能会创建多个实例
   - 或者 `@Autowired` 的时机问题，导致注入的不是同一个实例

## 解决方案

### 方案1：在 @Bean 方法中手动注入（推荐）

**修改后的代码**：

```java
@Configuration
public class FilterConfiguration {

    @Autowired
    private PluginFilterManager pluginFilterManager;

    /**
     * 创建代理 Filter Bean
     * 直接在创建时注入 PluginFilterManager，确保使用同一个实例
     */
    @Bean
    public PluginDelegatingFilter pluginDelegatingFilter(PluginFilterManager pluginFilterManager) {
        System.out.println("FilterConfiguration - 创建 PluginDelegatingFilter Bean");
        System.out.println("FilterConfiguration - 注入 PluginFilterManager: " + pluginFilterManager);
        System.out.println("FilterConfiguration - PluginFilterManager hashCode: " + pluginFilterManager.hashCode());
        
        PluginDelegatingFilter filter = new PluginDelegatingFilter();
        filter.setPluginFilterManager(pluginFilterManager);  // ← 手动注入
        
        return filter;
    }
    
    @Bean
    public FilterRegistrationBean<PluginDelegatingFilter> pluginDelegatingFilterRegistration(
            PluginDelegatingFilter filter) {
        // ...
    }
}

public class PluginDelegatingFilter implements Filter {
    
    private PluginFilterManager pluginFilterManager;
    
    // ❌ 移除 @Autowired 注解
    public void setPluginFilterManager(PluginFilterManager pluginFilterManager) {
        System.out.println("PluginDelegatingFilter.setPluginFilterManager - 设置 PluginFilterManager: " + pluginFilterManager);
        System.out.println("PluginDelegatingFilter.setPluginFilterManager - PluginFilterManager hashCode: " + pluginFilterManager.hashCode());
        this.pluginFilterManager = pluginFilterManager;
    }
}
```

**优点**：
- ✅ 明确控制依赖注入
- ✅ 确保使用同一个 `PluginFilterManager` 实例
- ✅ 易于调试（可以打印 hashCode 确认）

**关键点**：
1. 在 `@Bean` 方法参数中声明 `PluginFilterManager`，Spring 会自动注入
2. 手动调用 `setPluginFilterManager()` 方法注入
3. 移除 `setPluginFilterManager()` 方法上的 `@Autowired` 注解

### 方案2：使用构造函数注入

```java
public class PluginDelegatingFilter implements Filter {
    
    private final PluginFilterManager pluginFilterManager;
    
    // 构造函数注入
    public PluginDelegatingFilter(PluginFilterManager pluginFilterManager) {
        this.pluginFilterManager = pluginFilterManager;
    }
}

@Configuration
public class FilterConfiguration {

    @Bean
    public PluginDelegatingFilter pluginDelegatingFilter(PluginFilterManager pluginFilterManager) {
        return new PluginDelegatingFilter(pluginFilterManager);
    }
}
```

**优点**：
- ✅ 依赖是 final 的，不可变
- ✅ 更符合依赖注入最佳实践

**缺点**：
- ⚠️ 需要修改构造函数

### 方案3：使用 @Lazy 注解

```java
@Configuration
public class FilterConfiguration {

    @Autowired
    @Lazy  // ← 延迟注入
    private PluginFilterManager pluginFilterManager;
}
```

**优点**：
- ✅ 简单，只需添加注解

**缺点**：
- ❌ 不能保证一定解决问题
- ❌ 可能只是推迟了问题的出现

## 验证方法

### 1. 打印 hashCode

在关键位置打印 `PluginFilterManager` 的 hashCode：

```java
// FilterConfiguration.pluginDelegatingFilter()
System.out.println("FilterConfiguration - PluginFilterManager hashCode: " + pluginFilterManager.hashCode());

// PluginDelegatingFilter.setPluginFilterManager()
System.out.println("PluginDelegatingFilter - PluginFilterManager hashCode: " + pluginFilterManager.hashCode());

// FilterConfiguration.onPluginStarted()
System.out.println("FilterConfiguration - PluginFilterManager hashCode: " + pluginFilterManager.hashCode());
```

**如果 hashCode 一致**：说明是同一个实例 ✅  
**如果 hashCode 不一致**：说明是不同实例 ❌

### 2. 使用断点调试

在以下位置设置断点，检查 `pluginFilterManager` 的对象地址：

1. `FilterConfiguration.pluginDelegatingFilter()` - 创建 Filter 时
2. `PluginDelegatingFilter.setPluginFilterManager()` - 注入时
3. `FilterConfiguration.onPluginStarted()` - 注册 Filter 时
4. `PluginDelegatingFilter.doFilter()` - 获取 Filter 时

### 3. 添加日志

```java
@Bean
public PluginDelegatingFilter pluginDelegatingFilter(PluginFilterManager pluginFilterManager) {
    System.out.println("=== 创建 PluginDelegatingFilter ===");
    System.out.println("PluginFilterManager: " + pluginFilterManager);
    System.out.println("PluginFilterManager class: " + pluginFilterManager.getClass().getName());
    System.out.println("PluginFilterManager hashCode: " + pluginFilterManager.hashCode());
    System.out.println("PluginFilterManager identity: " + System.identityHashCode(pluginFilterManager));
    
    PluginDelegatingFilter filter = new PluginDelegatingFilter();
    filter.setPluginFilterManager(pluginFilterManager);
    
    return filter;
}
```

## 常见原因

### 1. 类加载器隔离

如果主应用和插件使用不同的类加载器，可能会导致同一个类被加载两次，创建两个实例。

**解决方案**：
- 确保 `PluginFilterManager` 在主应用中定义
- 不要在插件中定义同名的类

### 2. Spring 上下文隔离

如果有多个 Spring 上下文，每个上下文会创建自己的 Bean 实例。

**解决方案**：
- 确保 `PluginFilterManager` 在主应用的 Spring 上下文中
- 不要在插件的 Spring 上下文中创建 `PluginFilterManager`

### 3. @Autowired 时机问题

`@Autowired` 的注入时机可能不确定，导致注入的实例不一致。

**解决方案**：
- 使用方案1，在 `@Bean` 方法中手动注入
- 避免使用 `@Autowired` setter 方法

### 4. FilterRegistrationBean 的问题

`FilterRegistrationBean` 可能会创建 Filter 的新实例。

**解决方案**：
- 确保 `FilterRegistrationBean` 使用的是 Spring 容器中的 Filter Bean
- 不要让 `FilterRegistrationBean` 创建新实例

## 最佳实践

### 1. 在 @Bean 方法中手动注入依赖

```java
@Bean
public MyFilter myFilter(MyDependency dependency) {
    MyFilter filter = new MyFilter();
    filter.setDependency(dependency);  // 手动注入
    return filter;
}
```

**优点**：
- ✅ 明确控制依赖注入
- ✅ 确保使用正确的实例
- ✅ 易于调试

### 2. 使用构造函数注入

```java
public class MyFilter implements Filter {
    private final MyDependency dependency;
    
    public MyFilter(MyDependency dependency) {
        this.dependency = dependency;
    }
}

@Bean
public MyFilter myFilter(MyDependency dependency) {
    return new MyFilter(dependency);
}
```

**优点**：
- ✅ 依赖是 final 的，不可变
- ✅ 更符合依赖注入最佳实践
- ✅ 避免 setter 方法被多次调用

### 3. 打印调试信息

在关键位置打印 Bean 的 hashCode 和 identity hashCode：

```java
System.out.println("Bean: " + bean);
System.out.println("hashCode: " + bean.hashCode());
System.out.println("identity: " + System.identityHashCode(bean));
```

这样可以快速确认是否是同一个实例。

## 总结

### 问题

- ❌ `PluginFilterManager` 被创建了多个实例
- ❌ 注册和获取时使用的不是同一个实例
- ❌ 导致 Filter 列表为空

### 解决方案

- ✅ 在 `@Bean` 方法中手动注入 `PluginFilterManager`
- ✅ 移除 `@Autowired` 注解，避免 Spring 自动注入
- ✅ 打印 hashCode 确认是同一个实例

### 验证

- ✅ 打印 hashCode，确认一致
- ✅ 查看日志，确认注册和获取的是同一个实例
- ✅ 测试请求，确认 Filter 生效

### 关键点

1. **不要同时使用 `new` 和 `@Autowired`**：容易导致实例不一致
2. **在 `@Bean` 方法中手动注入**：明确控制依赖注入
3. **打印调试信息**：确认 Bean 实例一致
4. **使用构造函数注入**：更符合最佳实践

这个问题是 Spring 依赖注入中的常见陷阱，通过手动注入可以完美解决！

