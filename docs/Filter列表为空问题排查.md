# Filter 列表为空问题排查

## 问题描述

在 `PluginDelegatingFilter.doFilter()` 中，`pluginFilterManager.getAllFilters()` 返回空列表。

## 可能的原因

### 1. 插件还没有启动

**现象**：
- 主应用已经启动
- 请求到达时，插件还没有启动完成
- `SbpPluginStartedEvent` 事件还没有触发

**排查方法**：
- 查看日志，确认是否看到 `FilterConfiguration - 收到插件启动事件`
- 确认插件启动的时机

**解决方案**：
- 确保插件在主应用启动时自动加载
- 检查插件目录配置是否正确

### 2. 插件中没有定义 Filter

**现象**：
- 插件启动成功
- 但插件中没有 `@Component` 注解的 Filter Bean

**排查方法**：
- 查看日志：`FilterConfiguration - 找到 0 个 Filter Bean`
- 检查插件中是否有 Filter 类
- 检查 Filter 类是否有 `@Component` 注解

**解决方案**：
- 在插件中添加 Filter：
  ```java
  @Component
  public class ActivityPluginFilter extends OncePerRequestFilter {
      // ...
  }
  ```

### 3. Filter 注册失败

**现象**：
- 插件中有 Filter Bean
- 但注册到 `PluginFilterManager` 时失败

**排查方法**：
- 查看日志：`FilterConfiguration - 注册 Filter [xxx] 失败`
- 检查是否有异常堆栈

**解决方案**：
- 查看异常信息，修复注册失败的原因

### 4. pluginFilterManager 为 null

**现象**：
- `PluginDelegatingFilter` 中的 `pluginFilterManager` 为 null
- 依赖注入失败

**排查方法**：
- 查看日志：`PluginDelegatingFilter - pluginFilterManager 为 null！`
- 检查 `PluginFilterManager` 是否有 `@Component` 注解
- 检查 `PluginDelegatingFilter` 的 setter 方法是否有 `@Autowired` 注解

**解决方案**：
- 确保 `PluginFilterManager` 有 `@Component` 注解
- 确保 `PluginDelegatingFilter.setPluginFilterManager()` 有 `@Autowired` 注解

### 5. 事件监听器没有被触发

**现象**：
- 插件启动了
- 但 `FilterConfiguration.onPluginStarted()` 没有被调用

**排查方法**：
- 查看日志，确认是否看到 `FilterConfiguration - 收到插件启动事件`
- 检查 `@EventListener` 注解是否正确

**解决方案**：
- 确保 `FilterConfiguration` 有 `@Configuration` 注解
- 确保 `onPluginStarted()` 方法有 `@EventListener` 注解

### 6. 插件 ID 提取错误

**现象**：
- Filter 被注册，但使用了错误的插件 ID
- 导致 Filter Key 不匹配

**排查方法**：
- 查看日志：`FilterConfiguration - 插件ID: xxx`
- 检查插件 ID 是否正确

**解决方案**：
- 修改 `getPluginId()` 方法，正确提取插件 ID

## 排查步骤

### 步骤1：检查日志输出

启动应用后，查看日志，按以下顺序检查：

1. **PluginDelegatingFilter 初始化**
   ```
   PluginDelegatingFilter - 初始化代理 Filter
   PluginDelegatingFilter - pluginFilterManager: xxx
   PluginDelegatingFilter - 当前已注册 Filter 数量: 0
   ```

2. **插件启动事件**
   ```
   =================================================
   FilterConfiguration - 收到插件启动事件: xxx
   FilterConfiguration - pluginFilterManager: xxx
   FilterConfiguration - Event source type: xxx
   FilterConfiguration - 成功获取插件上下文: xxx
   FilterConfiguration - 插件ID: xxx
   ```

3. **Filter 注册**
   ```
   FilterConfiguration - 开始从插件上下文获取 Filter Bean
   FilterConfiguration - 找到 X 个 Filter Bean
   FilterConfiguration - 发现 Filter Bean: activityPluginFilter
   FilterConfiguration - 成功注册插件 Filter [xxx:activityPluginFilter] 到 PluginFilterManager
   FilterConfiguration - 注册完成后，Filter 数量: 1
   =================================================
   ```

4. **请求拦截**
   ```
   PluginDelegatingFilter.doFilter - 当前插件 Filter 数量: 1
   PluginDelegatingFilter - 拦截请求: GET /api/activities
   PluginFilterChain - 执行插件 Filter: xxx
   ```

### 步骤2：检查插件中的 Filter

确认插件中有 Filter 类：

```java
// activity-plugin/src/main/java/com/gaoding/ska/customize/config/ActivityPluginFilter.java
@Component  // ← 必须有这个注解
@Order(1)
public class ActivityPluginFilter extends OncePerRequestFilter {
    // ...
}
```

### 步骤3：检查 Bean 注册

确认 `PluginFilterManager` 被正确注册：

```java
@Component  // ← 必须有这个注解
public class PluginFilterManager {
    // ...
}
```

### 步骤4：检查依赖注入

确认 `PluginDelegatingFilter` 的依赖注入：

```java
public class PluginDelegatingFilter implements Filter {
    
    private PluginFilterManager pluginFilterManager;
    
    @Autowired  // ← 必须有这个注解
    public void setPluginFilterManager(PluginFilterManager pluginFilterManager) {
        this.pluginFilterManager = pluginFilterManager;
    }
}
```

### 步骤5：检查事件监听

确认 `FilterConfiguration` 的事件监听：

```java
@Configuration  // ← 必须有这个注解
public class FilterConfiguration {
    
    @EventListener  // ← 必须有这个注解
    public void onPluginStarted(SbpPluginStartedEvent event) {
        // ...
    }
}
```

## 常见问题

### Q1: 日志显示 "找到 0 个 Filter Bean"

**原因**：插件中没有 Filter Bean，或者 Filter 类没有 `@Component` 注解。

**解决方案**：
1. 检查插件中是否有 Filter 类
2. 确认 Filter 类有 `@Component` 注解
3. 确认 Filter 类在 `@ComponentScan` 的扫描范围内

### Q2: 日志显示 "pluginFilterManager 为 null"

**原因**：依赖注入失败。

**解决方案**：
1. 确认 `PluginFilterManager` 有 `@Component` 注解
2. 确认 `setPluginFilterManager()` 方法有 `@Autowired` 注解
3. 确认 `PluginDelegatingFilter` 是通过 `@Bean` 方法创建的

### Q3: 没有看到 "收到插件启动事件" 的日志

**原因**：事件监听器没有被触发。

**解决方案**：
1. 确认 `FilterConfiguration` 有 `@Configuration` 注解
2. 确认 `onPluginStarted()` 方法有 `@EventListener` 注解
3. 确认插件确实启动了

### Q4: Filter 被注册了，但请求时仍然是空列表

**原因**：可能是时序问题，或者 `pluginFilterManager` 实例不一致。

**解决方案**：
1. 确认 `PluginFilterManager` 是单例
2. 确认 `PluginDelegatingFilter` 和 `FilterConfiguration` 注入的是同一个 `PluginFilterManager` 实例
3. 在日志中打印 `pluginFilterManager` 的对象地址，确认是否一致

## 调试技巧

### 1. 添加更多日志

在关键位置添加日志输出：

```java
// PluginDelegatingFilter
@Override
public void init(FilterConfig filterConfig) throws ServletException {
    System.out.println("PluginDelegatingFilter - 初始化");
    System.out.println("PluginDelegatingFilter - pluginFilterManager: " + pluginFilterManager);
    System.out.println("PluginDelegatingFilter - pluginFilterManager hashCode: " + 
        (pluginFilterManager != null ? pluginFilterManager.hashCode() : "null"));
}

// FilterConfiguration
@EventListener
public void onPluginStarted(SbpPluginStartedEvent event) {
    System.out.println("FilterConfiguration - pluginFilterManager: " + pluginFilterManager);
    System.out.println("FilterConfiguration - pluginFilterManager hashCode: " + 
        (pluginFilterManager != null ? pluginFilterManager.hashCode() : "null"));
}
```

### 2. 使用断点调试

在以下位置设置断点：

1. `PluginDelegatingFilter.doFilter()` - 第59行
2. `FilterConfiguration.onPluginStarted()` - 事件处理方法
3. `FilterConfiguration.registerPluginFilters()` - Filter 注册方法
4. `PluginFilterManager.registerFilter()` - Filter 注册到管理器

### 3. 检查 Bean 实例

在调试时，检查以下内容：

1. `pluginFilterManager` 是否为 null
2. `pluginFilterManager` 的 hashCode 是否一致
3. `pluginFilters` Map 中是否有数据

## 总结

Filter 列表为空的最常见原因：

1. ✅ **插件还没有启动** - 等待插件启动完成
2. ✅ **插件中没有 Filter Bean** - 添加 `@Component` 注解
3. ✅ **依赖注入失败** - 检查 `@Autowired` 注解
4. ✅ **事件监听器没有被触发** - 检查 `@EventListener` 注解
5. ✅ **Bean 实例不一致** - 确认是单例

按照上述步骤排查，应该能够找到问题所在。

