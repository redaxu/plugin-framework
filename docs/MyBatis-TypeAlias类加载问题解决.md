# MyBatis Type Alias 类加载问题解决方案

## 问题描述

插件启动时报错：

```
Could not resolve type alias 'DistributeDto'.  
Cause: java.lang.ClassNotFoundException: Cannot find class: DistributeDto
```

完整错误：

```
org.apache.ibatis.type.TypeException: Could not resolve type alias 'DistributeDto'.  
Cause: java.lang.ClassNotFoundException: Cannot find class: DistributeDto
at org.apache.ibatis.type.TypeAliasRegistry.resolveAlias(TypeAliasRegistry.java:120)
```

## 问题原因

### 1. MyBatis Type Alias 机制

MyBatis 使用 `TypeAliasRegistry` 来管理类型别名：

```java
// MyBatis 源码
public class TypeAliasRegistry {
    private final Map<String, Class<?>> typeAliases = new HashMap<>();
    
    public void registerAliases(String packageName) {
        // 扫描包下的所有类并注册别名
        ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
        resolverUtil.find(new ResolverUtil.IsA(Object.class), packageName);
        Set<Class<? extends Class<?>>> typeSet = resolverUtil.getClasses();
        for (Class<?> type : typeSet) {
            registerAlias(type);
        }
    }
}
```

### 2. SBP 插件框架的类加载器隔离

在 SBP 插件框架中：

```
主应用 ClassLoader (AppClassLoader)
    ↓
插件 ClassLoader (PluginClassLoader)
    ↓
插件中的类（包括 DistributeDto）
```

**问题**：MyBatis 在解析 Mapper XML 时，使用的类加载器可能无法正确加载插件中的 DTO 类。

### 3. 类加载顺序问题

```java
// MyBatis 解析 Mapper XML 时
public class XMLMapperBuilder {
    public void parse() {
        // 1. 解析 resultType="DistributeDto"
        // 2. 尝试通过 TypeAliasRegistry 解析别名
        // 3. TypeAliasRegistry 使用当前线程的 ContextClassLoader
        // 4. 如果 ContextClassLoader 不是 PluginClassLoader，就找不到类
        Class<?> resultType = resolveClass(alias);
    }
}
```

## 解决方案

由于 `stat-gateway-plugin` 是已经打包好的 JAR，我们无法直接修改其源码。以下是可行的解决方案：

### 方案1：重新打包插件（推荐，如果有源码）

如果有插件源码，可以在插件的 MyBatis 配置中添加更明确的类型别名配置：

#### 1.1 在插件的配置类中手动注册类型别名

```java
@Configuration
public class MybatisPlusConfiguration {
    
    @Bean
    public ConfigurationCustomizer configurationCustomizer() {
        return configuration -> {
            // 手动注册类型别名
            TypeAliasRegistry registry = configuration.getTypeAliasRegistry();
            registry.registerAlias("DistributeDto", DistributeDto.class);
            // 或者注册整个包
            registry.registerAliases("com.gaoding.ska.statistics.dto");
        };
    }
}
```

#### 1.2 在 application.properties 中配置完整的包路径

```properties
# 确保包含所有 DTO 类的包
mybatis-plus.type-aliases-package=com.gaoding.ska.statistics.dto,\
  com.gaoding.ska.statistics.dto.account,\
  com.gaoding.ska.statistics.dto.account.response,\
  com.gaoding.ska.statistics.entity
```

### 方案2：修改 Mapper XML 使用全限定类名

如果可以修改 Mapper XML，将类型别名改为全限定类名：

```xml
<!-- 原来的写法（使用别名） -->
<select id="selectDistribute" resultType="DistributeDto">
    SELECT * FROM distribute
</select>

<!-- 修改为全限定类名 -->
<select id="selectDistribute" resultType="com.gaoding.ska.statistics.dto.account.response.DistributeDto">
    SELECT * FROM distribute
</select>
```

### 方案3：在主应用中配置 MyBatis 类型别名（❌ 无效）

**注意：这个方案是无效的！**

有人可能会想在主应用的 `application.yml` 中配置：

```yaml
mybatis-plus:
  type-aliases-package: com.gaoding.ska.statistics.dto
```

**为什么无效？**

1. **主应用和插件的 MyBatis 配置完全独立**：插件有自己的 `SqlSessionFactory` 和 `Configuration`
2. **主应用的 ClassLoader 无法加载插件中的类**：由于类加载器隔离，主应用无法访问插件的类
3. **插件已经正确配置了类型别名包**：问题不在配置，而在类加载时机

**结论**：在主应用中配置是无效的，不要浪费时间尝试这个方案。

### 方案4：检查插件打包配置（最可能的问题）

检查插件打包时是否正确包含了所有 DTO 类：

```bash
# 检查插件 JAR 中是否包含 DistributeDto
jar -tf stat-gateway-plugin.jar | grep DistributeDto

# 检查插件的 MyBatis 配置
jar -xf stat-gateway-plugin.jar application.properties
cat application.properties | grep mybatis
```

### 方案5：临时禁用有问题的 Mapper（快速解决）

如果只是为了让插件能够启动，可以临时禁用有问题的 Mapper：

在主应用的 `application.yml` 中添加：

```yaml
mybatis-plus:
  # 排除有问题的 Mapper XML
  mapper-locations: classpath*:mapper/**/*.xml
  # 不扫描插件的 Mapper XML
  # mapper-locations: classpath*:com/gaoding/**/mapper/*.xml
```

**问题**：这会导致插件的功能不完整。

## 深入分析：为什么会找不到类？

### 1. MyBatis 的类加载机制

```java
// MyBatis 源码：ClassLoaderWrapper
public class ClassLoaderWrapper {
    
    ClassLoader defaultClassLoader;
    ClassLoader systemClassLoader;
    
    Class<?> classForName(String name) throws ClassNotFoundException {
        return classForName(name, getClassLoaders(null));
    }
    
    ClassLoader[] getClassLoaders(ClassLoader classLoader) {
        return new ClassLoader[]{
            classLoader,                    // 1. 传入的 ClassLoader
            defaultClassLoader,             // 2. 默认 ClassLoader
            Thread.currentThread().getContextClassLoader(),  // 3. 线程上下文 ClassLoader
            getClass().getClassLoader(),    // 4. 当前类的 ClassLoader
            systemClassLoader               // 5. 系统 ClassLoader
        };
    }
}
```

### 2. SBP 插件框架的类加载器层次

```
Bootstrap ClassLoader
    ↓
Extension ClassLoader
    ↓
Application ClassLoader (主应用)
    ↓
Plugin ClassLoader (插件)
```

### 3. 问题所在

当 MyBatis 解析 Mapper XML 时：

1. **线程上下文 ClassLoader** 可能是主应用的 ClassLoader
2. **MyBatis 尝试加载 `DistributeDto`**，但这个类在插件的 ClassLoader 中
3. **主应用的 ClassLoader 无法加载插件中的类**
4. **结果**：`ClassNotFoundException`

## 推荐解决方案总结

| 方案 | 可行性 | 推荐度 | 说明 |
|-----|--------|--------|------|
| 重新打包插件 | ✅ 可行 | ⭐⭐⭐⭐⭐ | 最彻底的解决方案 |
| 使用全限定类名 | ✅ 可行 | ⭐⭐⭐⭐ | 需要修改 Mapper XML |
| 预加载类 | ❌ 不可行 | ⭐ | 类加载器隔离导致无法实现 |
| 排除 Mapper | ✅ 可行 | ⭐⭐ | 临时方案，功能不完整 |

## 最佳实践建议

### 1. 插件开发时的注意事项

在开发插件时，MyBatis Mapper XML 应该：

- **使用全限定类名**而不是类型别名
- **明确配置类型别名包**
- **测试类加载器隔离场景**

### 2. Mapper XML 编写规范

```xml
<!-- ❌ 不推荐：使用简单别名 -->
<select id="selectUser" resultType="UserDto">
    SELECT * FROM user
</select>

<!-- ✅ 推荐：使用全限定类名 -->
<select id="selectUser" resultType="com.example.plugin.dto.UserDto">
    SELECT * FROM user
</select>
```

### 3. MyBatis 配置规范

```properties
# 配置完整的类型别名包路径
mybatis-plus.type-aliases-package=com.example.plugin.dto,\
  com.example.plugin.entity,\
  com.example.plugin.vo

# 配置 Mapper XML 位置
mybatis-plus.mapper-locations=classpath*:mapper/**/*.xml
```

## 参考资料

- [MyBatis Type Aliases](https://mybatis.org/mybatis-3/configuration.html#typeAliases)
- [MyBatis Plus Configuration](https://baomidou.com/config/)
- [SBP Plugin Framework - ClassLoader](https://github.com/hank-cp/sbp)
- [Java ClassLoader 机制](https://docs.oracle.com/javase/8/docs/api/java/lang/ClassLoader.html)

