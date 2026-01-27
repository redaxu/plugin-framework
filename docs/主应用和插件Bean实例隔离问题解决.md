# 主应用和插件 Bean 实例隔离问题解决

## 问题描述

注册 Filter 时使用的 `PluginFilterManager` 实例，与请求时获取的 `PluginFilterManager` 实例不是同一个对象。

**原因**：一个是主应用的 `PluginFilterManager`，一个是插件应用的 `PluginFilterManager`。

## 问题原因

### Spring 上下文隔离

在插件框架中，主应用和插件拥有独立的 Spring 应用上下文：

```
主应用 Spring 上下文
  ├── PluginFilterManager (实例 A)
  ├── FilterConfiguration (使用实例 A)
  └── PluginDelegatingFilter (使用实例 A)

插件 Spring 上下文
  ├── PluginFilterManager (实例 B) ← 问题：插件也创建了实例
  └── FilterConfiguration (使用实例 B) ← 问题：插件也创建了配置类
```

### 为什么会扫描到主应用的类？

**插件配置**：

```java
@Configuration
@OnPluginMode
@ComponentScan(basePackages = "com.gaoding.ska.plugin")  // ← 扫描主应用的包
public class ActivityPluginConfiguration {
    // ...
}
```

**主应用配置**：

```java
@SpringBootApplication  // ← 默认扫描 com.gaoding.ska.plugin 包
public class PluginManagerApplication {
    // ...
}
```

**问题**：
- 插件的 `@ComponentScan(basePackages = "com.gaoding.ska.plugin")` 扫描了主应用的包
- 如果插件的类加载器能够加载主应用的类，插件会创建自己的 `FilterConfiguration` 和 `PluginFilterManager` 实例
- 导致主应用和插件各有一个实例

## 解决方案

### 方案1：使用条件注解（推荐）

创建 `@OnMainApplication` 注解，确保相关类只在主应用中生效。

#### 1. 创建条件注解

**ConditionalOnMainApplication.java**：

```java
public class ConditionalOnMainApplication implements Condition {
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ClassLoader classLoader = context.getClassLoader();
        
        // 检查类加载器是否是 PluginClassLoader
        String classLoaderName = classLoader != null ? classLoader.getClass().getName() : "";
        
        // 如果不是 PluginClassLoader，说明是在主应用中
        boolean isMainApplication = !classLoaderName.contains("PluginClassLoader");
        
        return isMainApplication;
    }
}
```

**OnMainApplication.java**：

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnMainApplication.class)
public @interface OnMainApplication {
}
```

#### 2. 在相关类上添加注解

```java
@Configuration
@OnMainApplication  // ← 添加此注解
public class FilterConfiguration {
    // ...
}

@Component
@OnMainApplication  // ← 添加此注解
public class PluginFilterManager {
    // ...
}

@Component
@OnMainApplication  // ← 添加此注解
public class PluginInterceptorManager {
    // ...
}

@Configuration
@OnMainApplication  // ← 添加此注解
public class WebMvcConfiguration implements WebMvcConfigurer {
    // ...
}
```

**优点**：
- ✅ 明确控制 Bean 的创建位置
- ✅ 避免插件 Spring 上下文扫描到主应用的类
- ✅ 确保只有一个实例

### 方案2：修改插件包扫描路径

修改插件的 `@ComponentScan`，只扫描插件的包：

```java
@Configuration
@OnPluginMode
@ComponentScan(basePackages = "com.gaoding.ska.plugin.activity")  // ← 只扫描插件包
public class ActivityPluginConfiguration {
    // ...
}
```

**缺点**：
- ⚠️ 需要修改每个插件的配置
- ⚠️ 如果插件和主应用共享包名，仍然可能扫描到

### 方案3：使用不同的包名

主应用和插件使用不同的包名：

```
主应用: com.gaoding.ska.plugin.main
插件:   com.gaoding.ska.plugin.plugin.activity
```

**缺点**：
- ⚠️ 需要重构代码
- ⚠️ 影响较大

## 实现细节

### 条件判断逻辑

```java
public class ConditionalOnMainApplication implements Condition {
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ClassLoader classLoader = context.getClassLoader();
        String classLoaderName = classLoader != null ? classLoader.getClass().getName() : "";
        
        // 检查是否是 PluginClassLoader
        // PluginClassLoader 的类名通常是:
        // - org.pf4j.PluginClassLoader
        // - org.laxture.sbp.spring.boot.SpringBootPluginClassLoader
        boolean isMainApplication = !classLoaderName.contains("PluginClassLoader");
        
        System.out.println("ConditionalOnMainApplication - ClassLoader: " + classLoaderName);
        System.out.println("ConditionalOnMainApplication - isMainApplication: " + isMainApplication);
        
        return isMainApplication;
    }
}
```

### 验证方法

在关键位置打印日志，确认 Bean 实例：

```java
@EventListener
public void onPluginStarted(SbpPluginStartedEvent event) {
    // 打印当前执行上下文
    System.out.println("FilterConfiguration - 当前执行上下文: " + applicationContext);
    System.out.println("FilterConfiguration - 当前执行上下文 ID: " + applicationContext.getId());
    System.out.println("FilterConfiguration - 当前类加载器: " + this.getClass().getClassLoader().getClass().getName());
    
    // 打印 PluginFilterManager 实例
    System.out.println("FilterConfiguration - pluginFilterManager: " + pluginFilterManager);
    System.out.println("FilterConfiguration - pluginFilterManager hashCode: " + pluginFilterManager.hashCode());
}
```

**期望输出**：

```
ConditionalOnMainApplication - ClassLoader: sun.misc.Launcher$AppClassLoader
ConditionalOnMainApplication - isMainApplication: true

FilterConfiguration - 当前执行上下文: org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext@xxx
FilterConfiguration - 当前执行上下文 ID: application
FilterConfiguration - 当前类加载器: sun.misc.Launcher$AppClassLoader
FilterConfiguration - pluginFilterManager hashCode: 123456789
```

**如果看到 PluginClassLoader**：

```
ConditionalOnMainApplication - ClassLoader: org.laxture.sbp.spring.boot.SpringBootPluginClassLoader
ConditionalOnMainApplication - isMainApplication: false

FilterConfiguration - 当前类加载器: org.laxture.sbp.spring.boot.SpringBootPluginClassLoader
```

说明条件注解生效，插件不会创建这些 Bean。

## 关键要点

### 1. Spring 上下文隔离

- ✅ 主应用和插件有独立的 Spring 上下文
- ✅ 每个上下文有自己的 Bean 实例
- ✅ 需要确保关键 Bean 只在主应用中创建

### 2. 类加载器隔离

- ✅ 主应用使用 `AppClassLoader`
- ✅ 插件使用 `PluginClassLoader`
- ✅ 可以通过类加载器判断是否在主应用中

### 3. 包扫描问题

- ⚠️ 插件的 `@ComponentScan` 可能扫描到主应用的包
- ⚠️ 如果类加载器能够加载主应用的类，会创建 Bean 实例
- ✅ 使用条件注解避免这个问题

### 4. Bean 实例一致性

- ✅ 确保 `PluginFilterManager` 只有一个实例（在主应用中）
- ✅ 确保 `FilterConfiguration` 只在主应用中生效
- ✅ 确保所有组件使用同一个 `PluginFilterManager` 实例

## 验证步骤

### 步骤1：检查条件注解是否生效

启动应用，查看日志：

```
ConditionalOnMainApplication - ClassLoader: sun.misc.Launcher$AppClassLoader
ConditionalOnMainApplication - isMainApplication: true
```

**如果看到 `isMainApplication: true`**：说明条件注解生效 ✅

### 步骤2：检查 Bean 实例

查看日志中的 hashCode：

```
FilterConfiguration - pluginFilterManager hashCode: 123456789
PluginDelegatingFilter - pluginFilterManager hashCode: 123456789
```

**如果 hashCode 一致**：说明是同一个实例 ✅

### 步骤3：检查执行上下文

查看日志：

```
FilterConfiguration - 当前执行上下文 ID: application
FilterConfiguration - 当前类加载器: sun.misc.Launcher$AppClassLoader
```

**如果类加载器是 `AppClassLoader`**：说明在主应用中执行 ✅

## 总结

### 问题

- ❌ 主应用和插件各有一个 `PluginFilterManager` 实例
- ❌ 注册和获取时使用的不是同一个实例
- ❌ 导致 Filter 列表为空

### 解决方案

- ✅ 创建 `@OnMainApplication` 条件注解
- ✅ 在关键类上添加注解，确保只在主应用中生效
- ✅ 避免插件 Spring 上下文扫描到主应用的类

### 关键类

1. **FilterConfiguration** - 添加 `@OnMainApplication`
2. **PluginFilterManager** - 添加 `@OnMainApplication`
3. **PluginInterceptorManager** - 添加 `@OnMainApplication`
4. **WebMvcConfiguration** - 添加 `@OnMainApplication`

### 验证

- ✅ 查看日志，确认条件注解生效
- ✅ 查看 hashCode，确认是同一个实例
- ✅ 查看类加载器，确认在主应用中执行

现在应该可以确保只有一个 `PluginFilterManager` 实例了！🎉

