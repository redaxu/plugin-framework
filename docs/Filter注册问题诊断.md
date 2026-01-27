# Filter 注册问题诊断

## 问题

用户反馈：`pluginFilterManager` 改完后，直接不注册 Filter。

## 架构确认

### ✅ 架构是正确的

**主应用监听插件事件是有效的**：

1. ✅ Spring 事件机制是全局的
2. ✅ 主应用可以监听到插件的 `SbpPluginStartedEvent`
3. ✅ 事件的 `source` 是插件的 `ApplicationContext`
4. ✅ 主应用可以通过插件 `ApplicationContext` 获取插件的 Bean

**证据**：SBP 框架源码

```java
// org.laxture.sbp.spring.boot.SpringBootPlugin
protected ApplicationContext createApplicationContext() {
    // 创建插件的 ApplicationContext
    ApplicationContext pluginContext = ...;
    
    // 发布事件，source 是插件的 ApplicationContext
    mainApplicationContext.publishEvent(new SbpPluginStartedEvent(pluginContext));
    
    return pluginContext;
}
```

**主应用可以访问插件的 Bean**：

```java
@EventListener
public void onPluginStarted(SbpPluginStartedEvent event) {
    // ✅ 获取插件的 ApplicationContext
    ApplicationContext pluginContext = (ApplicationContext) event.getSource();
    
    // ✅ 获取插件的 Filter Bean
    Map<String, Filter> filterBeans = pluginContext.getBeansOfType(Filter.class);
    
    // ✅ 注册到主应用的 PluginFilterManager
    for (Map.Entry<String, Filter> entry : filterBeans.entrySet()) {
        pluginFilterManager.registerFilter(pluginId + ":" + entry.getKey(), entry.getValue());
    }
}
```

## 可能的原因

### 原因1：`@OnMainApplication` 条件注解阻止了 FilterConfiguration 创建

**症状**：
- 没有看到 "收到插件启动事件" 的日志
- 没有看到 "开始从插件上下文获取 Filter Bean" 的日志

**原因**：
- `@OnMainApplication` 条件注解判断错误
- `FilterConfiguration` 没有被创建

**验证**：
```bash
# 启动应用，查看日志
mvn spring-boot:run

# 查找 FilterConfiguration 创建日志
grep "FilterConfiguration" logs/application.log

# 查找条件注解日志
grep "ConditionalOnMainApplication" logs/application.log
```

**期望日志**：
```
ConditionalOnMainApplication - ClassLoader: sun.misc.Launcher$AppClassLoader
ConditionalOnMainApplication - isMainApplication: true
FilterConfiguration - 创建 PluginDelegatingFilter Bean
```

**如果没有日志**：说明 `FilterConfiguration` 没有被创建，条件注解有问题。

### 原因2：插件的 Filter 没有被扫描到

**症状**：
```
FilterConfiguration - 找到 0 个 Filter Bean
```

**原因**：
1. 插件的 Filter 不在 `@ComponentScan` 扫描范围内
2. 插件的 Filter 有条件注解，不满足条件
3. 插件的 ApplicationContext 没有正确初始化

**验证**：
```bash
# 检查插件的配置类
cat activity-plugin/src/main/java/com/gaoding/ska/customize/config/ActivityPluginConfiguration.java

# 检查插件的 Filter 类
cat activity-plugin/src/main/java/com/gaoding/ska/customize/config/ActivityPluginFilter.java
```

**期望**：
- `ActivityPluginConfiguration` 有 `@ComponentScan(basePackages = "com.gaoding.ska.plugin")`
- `ActivityPluginFilter` 有 `@Component` 注解
- `ActivityPluginFilter` 在 `com.gaoding.ska.plugin.config` 包下

**当前状态**：
- ✅ `ActivityPluginConfiguration` 有 `@ComponentScan(basePackages = "com.gaoding.ska.plugin")`
- ✅ `ActivityPluginFilter` 有 `@Component` 注解
- ✅ `ActivityPluginFilter` 在 `com.gaoding.ska.plugin.config` 包下

### 原因3：插件启动事件没有被触发

**症状**：
- 没有看到 "收到插件启动事件" 的日志

**原因**：
1. 插件没有正确启动
2. 插件启动失败
3. 事件监听器没有注册

**验证**：
```bash
# 查看插件启动日志
grep "Plugin" logs/application.log | grep -i "start"

# 查看 SBP 框架日志
grep "sbp" logs/application.log -i
```

**期望日志**：
```
[SBP] Starting plugin: activity-plugin
[SBP] Plugin activity-plugin started successfully
```

## 诊断步骤

### 步骤1：确认条件注解是否生效

**目的**：确认 `FilterConfiguration` 是否在主应用中创建

**操作**：
```bash
# 启动应用
cd /Users/gaoding/projects/IdeaProjects/ska-plugin-framework-demo/plugin-manager
mvn clean spring-boot:run 2>&1 | tee /tmp/plugin-manager.log
```

**查看日志**：
```bash
# 查找条件注解日志
grep "ConditionalOnMainApplication" /tmp/plugin-manager.log

# 查找 FilterConfiguration 创建日志
grep "FilterConfiguration - 创建" /tmp/plugin-manager.log
```

**期望输出**：
```
ConditionalOnMainApplication - ClassLoader: sun.misc.Launcher$AppClassLoader
ConditionalOnMainApplication - isMainApplication: true
FilterConfiguration - 创建 PluginDelegatingFilter Bean
FilterConfiguration - 注入 PluginFilterManager: config.com.gaoding.ska.plugin.activity.PluginFilterManager@xxx
```

**如果没有输出**：
- ❌ `FilterConfiguration` 没有被创建
- ❌ 条件注解有问题或配置类没有被扫描到

**解决方案**：
1. 检查 `ConditionalOnMainApplication` 的逻辑
2. 检查 `FilterConfiguration` 是否在主应用的扫描范围内
3. 临时移除 `@OnMainApplication` 注解，看是否能创建

### 步骤2：确认插件是否启动

**目的**：确认插件是否正确启动

**查看日志**：
```bash
# 查找插件启动日志
grep -i "plugin" /tmp/plugin-manager.log | grep -i "start"

# 查找 SBP 框架日志
grep "laxture" /tmp/plugin-manager.log
```

**期望输出**：
```
[SBP] Loading plugins from: plugins
[SBP] Found plugin: activity-plugin
[SBP] Starting plugin: activity-plugin
[SBP] Plugin activity-plugin started successfully
```

**如果没有输出**：
- ❌ 插件没有启动
- ❌ 插件目录配置错误或插件 JAR 不存在

**解决方案**：
1. 检查 `application.yml` 中的 `spring.sbp.plugins-dir` 配置
2. 检查 `plugins/` 目录是否存在
3. 检查 `plugins/activity-plugin.jar` 是否存在
4. 重新编译插件：`cd activity-plugin && mvn clean package`

### 步骤3：确认事件监听器是否被触发

**目的**：确认 `FilterConfiguration.onPluginStarted()` 是否被调用

**查看日志**：
```bash
# 查找事件监听日志
grep "收到插件启动事件" /tmp/plugin-manager.log
```

**期望输出**：
```
=================================================
FilterConfiguration - 收到插件启动事件: org.laxture.sbp.spring.boot.SbpPluginStartedEvent[source=xxx]
FilterConfiguration - 当前执行上下文: org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext@xxx
FilterConfiguration - 当前执行上下文 ID: application
FilterConfiguration - 当前类加载器: sun.misc.Launcher$AppClassLoader
```

**如果没有输出**：
- ❌ 事件监听器没有被触发
- ❌ `FilterConfiguration` 没有被创建（回到步骤1）
- ❌ 插件没有启动（回到步骤2）

### 步骤4：确认是否找到插件的 Filter

**目的**：确认是否从插件上下文中找到 Filter Bean

**查看日志**：
```bash
# 查找 Filter 注册日志
grep "开始从插件上下文获取 Filter Bean" /tmp/plugin-manager.log
grep "找到.*个 Filter Bean" /tmp/plugin-manager.log
```

**期望输出**：
```
FilterConfiguration - 开始从插件上下文获取 Filter Bean
FilterConfiguration - 找到 1 个 Filter Bean
FilterConfiguration - 发现 Filter Bean: activityPluginFilter
```

**如果输出是 "找到 0 个 Filter Bean"**：
- ❌ 插件的 Filter 没有被扫描到
- ❌ 插件的 ApplicationContext 没有正确初始化

**解决方案**：
1. 检查插件的 `ActivityPluginConfiguration` 是否有 `@ComponentScan`
2. 检查插件的 `ActivityPluginFilter` 是否有 `@Component`
3. 检查插件的 `ActivityPluginFilter` 是否在扫描范围内
4. 在插件的 `ActivityPluginConfiguration` 中添加日志，确认是否被加载

### 步骤5：确认 Filter 是否注册成功

**目的**：确认 Filter 是否成功注册到 `PluginFilterManager`

**查看日志**：
```bash
# 查找 Filter 注册成功日志
grep "PluginFilterManager - 注册 Filter" /tmp/plugin-manager.log
grep "注册完成后，Filter 数量" /tmp/plugin-manager.log
```

**期望输出**：
```
PluginFilterManager - 注册 Filter: activity-plugin:activityPluginFilter (config.com.gaoding.ska.plugin.activity.ActivityPluginFilter)
PluginFilterManager - 当前已注册 Filter 数量: 1
FilterConfiguration - 注册完成后，Filter 数量: 1
```

**如果没有输出**：
- ❌ Filter 注册失败
- ❌ 可能抛出了异常

**查看异常**：
```bash
grep -A 5 "Exception" /tmp/plugin-manager.log
grep -A 5 "Error" /tmp/plugin-manager.log
```

## 临时解决方案：移除条件注解

如果条件注解导致问题，可以临时移除 `@OnMainApplication`，验证架构是否正确：

### 1. 移除 @OnMainApplication

```java
@Configuration
// @OnMainApplication  // ← 临时注释掉
public class FilterConfiguration implements ApplicationContextAware {
    // ...
}

@Component
// @OnMainApplication  // ← 临时注释掉
public class PluginFilterManager {
    // ...
}
```

### 2. 重新编译和启动

```bash
cd /Users/gaoding/projects/IdeaProjects/ska-plugin-framework-demo/plugin-manager
mvn clean compile
mvn spring-boot:run 2>&1 | tee /tmp/plugin-manager.log
```

### 3. 查看日志

```bash
# 查找 FilterConfiguration 创建日志
grep "FilterConfiguration - 创建" /tmp/plugin-manager.log

# 查找事件监听日志
grep "收到插件启动事件" /tmp/plugin-manager.log

# 查找 Filter 注册日志
grep "注册 Filter:" /tmp/plugin-manager.log
```

### 4. 发送测试请求

```bash
curl -X GET http://localhost:8081/api/activities
```

### 5. 查看 Filter 执行日志

```bash
grep "PluginDelegatingFilter.doFilter" /tmp/plugin-manager.log
grep "ActivityPluginFilter" /tmp/plugin-manager.log
```

**如果移除条件注解后可以正常工作**：
- ✅ 架构是正确的
- ❌ 条件注解有问题

**如果移除条件注解后仍然不工作**：
- ❌ 架构有问题
- ❌ 需要重新审视整个设计

## 最终验证

### 完整的启动日志应该包含

```
1. 主应用启动
   ConditionalOnMainApplication - ClassLoader: sun.misc.Launcher$AppClassLoader
   ConditionalOnMainApplication - isMainApplication: true
   FilterConfiguration - 创建 PluginDelegatingFilter Bean
   FilterConfiguration - 注入 PluginFilterManager: xxx
   PluginDelegatingFilter - 初始化代理 Filter
   PluginDelegatingFilter - 当前已注册 Filter 数量: 0

2. 插件启动
   [SBP] Loading plugins from: plugins
   [SBP] Found plugin: activity-plugin
   [SBP] Starting plugin: activity-plugin
   
3. 事件监听
   FilterConfiguration - 收到插件启动事件
   FilterConfiguration - 当前执行上下文 ID: application
   FilterConfiguration - 插件ID: activity-plugin
   
4. Filter 注册
   FilterConfiguration - 开始从插件上下文获取 Filter Bean
   FilterConfiguration - 找到 1 个 Filter Bean
   FilterConfiguration - 发现 Filter Bean: activityPluginFilter
   PluginFilterManager - 注册 Filter: activity-plugin:activityPluginFilter
   PluginFilterManager - 当前已注册 Filter 数量: 1
   FilterConfiguration - 注册完成后，Filter 数量: 1
   
5. 请求处理
   PluginDelegatingFilter.doFilter - 拦截请求: GET /api/activities
   PluginDelegatingFilter.doFilter - 当前插件 Filter 数量: 1
   PluginFilterChain - 执行插件 Filter: ActivityPluginFilter
   ActivityPluginFilter - 过滤器执行, URI: /api/activities
```

## 下一步

1. **按照诊断步骤逐一检查**
2. **提供完整的启动日志**（从应用启动到插件加载完成）
3. **如果某个步骤失败，提供该步骤的详细日志**
4. **如果需要，临时移除条件注解验证架构**

请启动应用并提供日志，我会帮你分析具体问题。

