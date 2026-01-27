# Filter 注册流程验证

## 架构确认

### ✅ 主应用监听插件事件是有效的

**原因**：
1. Spring 事件机制是全局的，主应用可以监听到插件的事件
2. `SbpPluginStartedEvent` 的 `source` 是插件的 `ApplicationContext`
3. 主应用可以通过插件的 `ApplicationContext` 访问插件的 Bean

**SBP 框架机制**：
```
插件启动流程：
1. PluginManager 加载插件
2. 创建插件的 ApplicationContext (子上下文)
3. 发布 SbpPluginStartedEvent 事件（source = 插件 ApplicationContext）
4. 主应用的 @EventListener 监听到事件 ✅
5. 主应用从插件 ApplicationContext 获取 Filter Bean ✅
6. 主应用将 Filter 注册到 PluginFilterManager ✅
```

### ✅ 跨上下文访问 Bean 是可行的

```java
// 主应用代码
@EventListener
public void onPluginStarted(SbpPluginStartedEvent event) {
    ApplicationContext pluginContext = (ApplicationContext) event.getSource();
    
    // ✅ 可以访问插件的 Bean
    Map<String, Filter> filterBeans = pluginContext.getBeansOfType(Filter.class);
    
    // ✅ 可以获取插件的 Bean 实例
    Filter pluginFilter = pluginContext.getBean("activityPluginFilter", Filter.class);
}
```

**为什么可行**：
- 插件的 ApplicationContext 是一个完整的 Spring 上下文
- 主应用持有插件 ApplicationContext 的引用
- 可以通过 `getBean()` 或 `getBeansOfType()` 获取插件的 Bean

## 当前实现验证

### 步骤1：检查条件注解是否生效

**期望日志**：
```
ConditionalOnMainApplication - ClassLoader: sun.misc.Launcher$AppClassLoader
ConditionalOnMainApplication - isMainApplication: true
```

**如果看到**：
```
ConditionalOnMainApplication - ClassLoader: org.laxture.sbp.spring.boot.SpringBootPluginClassLoader
ConditionalOnMainApplication - isMainApplication: false
```

说明 `FilterConfiguration` 在插件中被创建了，需要检查条件注解是否正确。

### 步骤2：检查主应用启动时的 Bean 创建

**期望日志**：
```
FilterConfiguration - 创建 PluginDelegatingFilter Bean
FilterConfiguration - 注入 PluginFilterManager: config.com.gaoding.ska.plugin.activity.PluginFilterManager@xxx
FilterConfiguration - PluginFilterManager hashCode: 123456789

PluginDelegatingFilter.setPluginFilterManager - 设置 PluginFilterManager: config.com.gaoding.ska.plugin.activity.PluginFilterManager@xxx
PluginDelegatingFilter.setPluginFilterManager - PluginFilterManager hashCode: 123456789

FilterConfiguration - 注册代理 Filter: PluginDelegatingFilter
FilterConfiguration - Filter 实例: config.com.gaoding.ska.plugin.activity.PluginDelegatingFilter@xxx

PluginDelegatingFilter - 初始化代理 Filter
PluginDelegatingFilter - pluginFilterManager: config.com.gaoding.ska.plugin.activity.PluginFilterManager@xxx
PluginDelegatingFilter - 当前已注册 Filter 数量: 0
```

**关键点**：
- ✅ `PluginFilterManager` 的 hashCode 应该一致
- ✅ `PluginDelegatingFilter` 初始化时，Filter 数量为 0（插件还未启动）

### 步骤3：检查插件启动时的事件监听

**期望日志**：
```
=================================================
FilterConfiguration - 收到插件启动事件: org.laxture.sbp.spring.boot.SbpPluginStartedEvent[source=xxx]
FilterConfiguration - 当前执行上下文: org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext@xxx
FilterConfiguration - 当前执行上下文 ID: application
FilterConfiguration - 当前类加载器: sun.misc.Launcher$AppClassLoader
FilterConfiguration - pluginFilterManager: config.com.gaoding.ska.plugin.activity.PluginFilterManager@xxx
FilterConfiguration - pluginFilterManager hashCode: 123456789
FilterConfiguration - pluginInterceptorManager: config.com.gaoding.ska.plugin.activity.PluginInterceptorManager@xxx
FilterConfiguration - pluginInterceptorManager hashCode: 987654321
FilterConfiguration - Event source type: org.springframework.context.support.GenericApplicationContext
FilterConfiguration - 成功获取插件上下文: org.springframework.context.support.GenericApplicationContext@xxx
FilterConfiguration - 插件上下文 ID: application-activity-plugin
FilterConfiguration - 插件上下文 DisplayName: activity-plugin
FilterConfiguration - 插件ID: activity-plugin
```

**关键点**：
- ✅ 当前执行上下文是主应用的上下文（ID: application）
- ✅ 当前类加载器是 AppClassLoader（主应用）
- ✅ pluginFilterManager hashCode 与步骤2一致
- ✅ 插件上下文是独立的（ID: application-activity-plugin）

### 步骤4：检查 Filter 注册

**期望日志**：
```
FilterConfiguration - 开始从插件上下文获取 Filter Bean
FilterConfiguration - 找到 1 个 Filter Bean
FilterConfiguration - 发现 Filter Bean: activityPluginFilter
FilterConfiguration - 成功注册插件 Filter [activity-plugin:activityPluginFilter] 到 PluginFilterManager

PluginFilterManager - 注册 Filter: activity-plugin:activityPluginFilter (config.com.gaoding.ska.plugin.activity.ActivityPluginFilter)
PluginFilterManager - 当前已注册 Filter 数量: 1

FilterConfiguration - 注册完成后，Filter 数量: 1
```

**关键点**：
- ✅ 找到插件的 Filter Bean
- ✅ 成功注册到 PluginFilterManager
- ✅ Filter 数量增加

### 步骤5：检查请求时的 Filter 执行

**期望日志**：
```
PluginDelegatingFilter.doFilter - pluginFilterManager hashCode: 123456789
PluginDelegatingFilter.doFilter - 拦截请求: GET /api/activities
PluginDelegatingFilter.doFilter - 当前插件 Filter 数量: 1

PluginFilterChain - 执行插件 Filter: config.com.gaoding.ska.plugin.activity.ActivityPluginFilter
ActivityPluginFilter - 拦截请求: GET /api/activities
ActivityPluginFilter - 执行 Filter 逻辑
```

**关键点**：
- ✅ pluginFilterManager hashCode 与步骤2、步骤3一致
- ✅ Filter 数量为 1
- ✅ 执行插件的 Filter

## 可能的问题和排查

### 问题1：条件注解不生效

**症状**：
```
ConditionalOnMainApplication - ClassLoader: org.laxture.sbp.spring.boot.SpringBootPluginClassLoader
ConditionalOnMainApplication - isMainApplication: false
```

**原因**：
- `FilterConfiguration` 在插件中被创建
- 插件的 `@ComponentScan` 扫描到了主应用的类

**解决方案**：
- ✅ 已添加 `@OnMainApplication` 注解
- ✅ 条件注解应该阻止在插件中创建

**验证**：
- 查看日志，确认只有一个 `FilterConfiguration` 被创建
- 确认 `isMainApplication: true`

### 问题2：找不到插件的 Filter Bean

**症状**：
```
FilterConfiguration - 找到 0 个 Filter Bean
```

**原因**：
1. 插件的 Filter 没有使用 `@Component` 注解
2. 插件的 Filter 不在 `@ComponentScan` 扫描范围内
3. 插件的 Filter 有条件注解，不满足条件

**解决方案**：
- 检查插件的 Filter 类是否有 `@Component` 注解
- 检查插件的 `@ComponentScan` 是否包含 Filter 所在的包
- 检查插件的 Filter 是否有条件注解（如 `@OnPluginMode`）

**验证**：
```bash
# 检查插件的 Filter 类
cat activity-plugin/src/main/java/com/gaoding/ska/customize/config/ActivityPluginFilter.java | grep "@Component"

# 检查插件的配置类
cat activity-plugin/src/main/java/com/gaoding/ska/customize/config/ActivityPluginConfiguration.java | grep "@ComponentScan"
```

### 问题3：PluginFilterManager 实例不一致

**症状**：
```
FilterConfiguration - pluginFilterManager hashCode: 123456789
PluginDelegatingFilter.doFilter - pluginFilterManager hashCode: 987654321
```

**原因**：
- `PluginFilterManager` 在主应用和插件中各创建了一个实例
- `FilterConfiguration` 使用的是插件的实例
- `PluginDelegatingFilter` 使用的是主应用的实例

**解决方案**：
- ✅ 已在 `PluginFilterManager` 上添加 `@OnMainApplication` 注解
- ✅ 确保只在主应用中创建

**验证**：
- 查看日志，确认 hashCode 一致
- 确认只有一个 `PluginFilterManager` 被创建

### 问题4：Filter 数量为 0

**症状**：
```
FilterConfiguration - 注册完成后，Filter 数量: 1
PluginDelegatingFilter.doFilter - 当前插件 Filter 数量: 0
```

**原因**：
- 注册和获取使用的不是同一个 `PluginFilterManager` 实例
- 这是问题3的表现

**解决方案**：
- 解决问题3

## 验证脚本

创建测试脚本，自动验证整个流程：

```bash
#!/bin/bash

echo "=== 启动主应用 ==="
cd /Users/gaoding/projects/IdeaProjects/ska-plugin-framework-demo/plugin-manager
mvn spring-boot:run > /tmp/plugin-manager.log 2>&1 &
PID=$!

echo "等待应用启动..."
sleep 10

echo "=== 检查日志 ==="

echo "1. 检查条件注解是否生效"
grep "ConditionalOnMainApplication" /tmp/plugin-manager.log | tail -5

echo ""
echo "2. 检查 PluginFilterManager 创建"
grep "PluginFilterManager hashCode" /tmp/plugin-manager.log | head -5

echo ""
echo "3. 检查插件启动事件"
grep "收到插件启动事件" /tmp/plugin-manager.log

echo ""
echo "4. 检查 Filter 注册"
grep "注册 Filter:" /tmp/plugin-manager.log

echo ""
echo "5. 发送测试请求"
curl -X GET http://localhost:8081/api/activities

echo ""
echo "6. 检查 Filter 执行"
grep "PluginDelegatingFilter.doFilter" /tmp/plugin-manager.log | tail -3

echo ""
echo "=== 停止应用 ==="
kill $PID
```

## 总结

### 架构是正确的

- ✅ 主应用可以监听插件的启动事件
- ✅ 主应用可以访问插件的 ApplicationContext
- ✅ 主应用可以获取插件的 Bean
- ✅ 主应用可以将插件的 Filter 注册到 PluginFilterManager
- ✅ 代理 Filter 可以从 PluginFilterManager 获取插件 Filter 并执行

### 关键要点

1. **条件注解必须生效**：确保 `FilterConfiguration`、`PluginFilterManager` 等只在主应用中创建
2. **Bean 实例必须一致**：确保所有组件使用同一个 `PluginFilterManager` 实例
3. **插件 Filter 必须可见**：确保插件的 Filter 有 `@Component` 注解，且在扫描范围内
4. **日志是关键**：通过日志确认每个步骤是否正常

### 下一步

1. 启动应用，查看日志
2. 按照上面的步骤逐一验证
3. 如果有问题，根据症状查找对应的解决方案
4. 发送测试请求，确认 Filter 正常工作

如果还是不注册 Filter，请提供完整的启动日志，我会帮你分析具体问题。

