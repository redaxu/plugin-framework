# MyBatis Type Aliases 在 SBP 插件环境中失效的原因

## 问题现象

在插件中明明配置了：
```properties
mybatis-plus.type-aliases-package=com.gaoding.ska.statistics.dto.account.response
```

而且 `DistributeDto` 类确实在这个包下，但 MyBatis 仍然报错：
```
Could not resolve type alias 'DistributeDto'.
Cause: ClassNotFoundException: Cannot find class: DistributeDto
```

## 深入分析：为什么 Aliases 注册失效？

### 1. MyBatis Type Aliases 注册的时机

```
应用启动
    ↓
Spring 初始化 SqlSessionFactory
    ↓
读取 mybatis-plus.type-aliases-package 配置
    ↓
调用 TypeAliasRegistry.registerAliases()
    ↓
使用 ClassLoader 扫描包
    ↓
注册所有找到的类作为别名
```

**关键点**：这个过程在 `SqlSessionFactory` 创建时完成，且**只执行一次**。

### 2. SBP 插件环境的类加载器层次

```
Bootstrap ClassLoader
    ↓
Extension ClassLoader
    ↓
Application ClassLoader (主应用)
    ↓ 委托加载
Plugin ClassLoader (插件1)
    ↓
Plugin ClassLoader (插件2)
```

**特点**：
- 每个插件有独立的 `PluginClassLoader`
- 插件 ClassLoader 可以访问主应用的类
- 主应用 ClassLoader **无法访问**插件的类

### 3. 问题根源：类加载器上下文不匹配

#### 场景1：Type Aliases 注册时

```java
// MyBatis Plus 自动配置
@Configuration
public class MybatisPlusAutoConfiguration {
    
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) {
        // 创建 Configuration
        MybatisPlusConfiguration configuration = new MybatisPlusConfiguration();
        
        // 设置类型别名包
        String typeAliasesPackage = "com.gaoding.ska.statistics.dto.account.response";
        
        // 关键：这里使用什么 ClassLoader？
        configuration.setTypeAliasesPackage(typeAliasesPackage);
        
        return factory;
    }
}
```

#### 场景2：ResolverUtil 扫描包时

```java
// MyBatis 源码：ResolverUtil
public class ResolverUtil<T> {
    
    public ResolverUtil<T> find(Test test, String packageName) {
        String path = packageName.replace('.', '/');
        
        // 关键：获取 ClassLoader
        ClassLoader classLoader = getClassLoader();
        
        try {
            // 使用 ClassLoader 获取资源
            Enumeration<URL> urls = classLoader.getResources(path);
            
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                // 扫描包下的类
                // 问题：如果 ClassLoader 不对，这里可能找不到任何类！
            }
        } catch (IOException e) {
            // ...
        }
        
        return this;
    }
    
    // 获取 ClassLoader 的逻辑
    public ClassLoader getClassLoader() {
        // 1. 先尝试线程上下文 ClassLoader
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) {
            return cl;
        }
        
        // 2. 然后尝试当前类的 ClassLoader
        cl = getClass().getClassLoader();
        if (cl != null) {
            return cl;
        }
        
        // 3. 最后使用系统 ClassLoader
        return ClassLoader.getSystemClassLoader();
    }
}
```

### 4. 问题的核心

在 SBP 插件环境中，当 MyBatis 扫描类型别名包时：

#### 情况A：使用了主应用的 ClassLoader

```java
// Thread.currentThread().getContextClassLoader()
// 返回：Application ClassLoader (主应用)

ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
// 使用主应用的 ClassLoader 扫描包
Enumeration<URL> urls = classLoader.getResources("com/gaoding/ska/statistics/dto/account/response");

// 结果：找不到！因为这个包在插件的 ClassLoader 中
// urls.hasMoreElements() = false
// 没有类被注册到 TypeAliasRegistry
```

#### 情况B：使用了插件的 ClassLoader（理想情况）

```java
// 假设正确设置了插件的 ClassLoader
ClassLoader classLoader = pluginClassLoader;
Enumeration<URL> urls = classLoader.getResources("com/gaoding/ska/statistics/dto/account/response");

// 结果：找到了！
// urls 包含插件 JAR 中的路径
// 成功扫描到 DistributeDto.class
// 注册到 TypeAliasRegistry
```

### 5. 验证问题：查看实际使用的 ClassLoader

让我们通过日志验证：

```java
// 在插件的 MybatisPlusConfiguration 中添加日志
@Configuration
public class MybatisPlusConfiguration {
    
    @Bean
    public ConfigurationCustomizer configurationCustomizer() {
        return configuration -> {
            // 打印当前线程的 ClassLoader
            ClassLoader contextCL = Thread.currentThread().getContextClassLoader();
            ClassLoader currentCL = this.getClass().getClassLoader();
            
            System.out.println("线程上下文 ClassLoader: " + contextCL.getClass().getName());
            System.out.println("当前类 ClassLoader: " + currentCL.getClass().getName());
            
            // 尝试加载 DistributeDto
            try {
                Class<?> clazz = contextCL.loadClass("com.gaoding.ska.statistics.dto.account.response.DistributeDto");
                System.out.println("线程上下文 CL 能加载 DistributeDto: " + clazz);
            } catch (ClassNotFoundException e) {
                System.out.println("线程上下文 CL 无法加载 DistributeDto");
            }
            
            try {
                Class<?> clazz = currentCL.loadClass("com.gaoding.ska.statistics.dto.account.response.DistributeDto");
                System.out.println("当前类 CL 能加载 DistributeDto: " + clazz);
            } catch (ClassNotFoundException e) {
                System.out.println("当前类 CL 无法加载 DistributeDto");
            }
        };
    }
}
```

### 6. 为什么使用全限定类名可以工作？

当使用全限定类名时：

```xml
<select id="selectData" resultType="com.gaoding.ska.statistics.dto.account.response.DistributeDto">
```

MyBatis 的处理逻辑：

```java
// XMLStatementBuilder
public void parseStatementNode() {
    String resultType = "com.gaoding.ska.statistics.dto.account.response.DistributeDto";
    
    // 判断：这不是别名（包含了点号）
    if (resultType.contains(".")) {
        // 直接使用 ClassLoader 加载
        Class<?> clazz = Resources.classForName(resultType);
    } else {
        // 从 TypeAliasRegistry 查找别名
        Class<?> clazz = typeAliasRegistry.resolveAlias(resultType);
    }
}

// Resources.classForName
public static Class<?> classForName(String className) {
    // 使用 ClassLoaderWrapper 尝试多个 ClassLoader
    ClassLoaderWrapper classLoaderWrapper = new ClassLoaderWrapper();
    return classLoaderWrapper.classForName(className);
}

// ClassLoaderWrapper 会尝试多个 ClassLoader
public Class<?> classForName(String name) {
    ClassLoader[] classLoaders = new ClassLoader[]{
        classLoader,                                     // 1. 传入的 ClassLoader
        defaultClassLoader,                              // 2. 默认 ClassLoader
        Thread.currentThread().getContextClassLoader(), // 3. 线程上下文 ClassLoader ✓ 可能是插件CL
        getClass().getClassLoader(),                     // 4. 当前类的 ClassLoader
        systemClassLoader                                // 5. 系统 ClassLoader
    };
    
    // 逐个尝试，直到成功
    for (ClassLoader cl : classLoaders) {
        if (cl != null) {
            try {
                return Class.forName(name, true, cl);
            } catch (ClassNotFoundException e) {
                // 尝试下一个
            }
        }
    }
}
```

**关键差异**：

| 方式 | Type Alias | 全限定类名 |
|-----|-----------|----------|
| 扫描时机 | 应用启动时（一次性） | 每次使用时 |
| ClassLoader | 可能用错 | 尝试多个 |
| 失败表现 | 注册为空，查找失败 | 多次尝试，更容易成功 |

## 总结

### Type Aliases 失效的根本原因

1. **类加载器上下文不匹配**：
   - MyBatis 扫描类型别名包时使用了错误的 ClassLoader（主应用的）
   - 导致无法扫描到插件中的类
   - TypeAliasRegistry 中没有注册任何别名

2. **一次性注册的局限性**：
   - Type Aliases 只在 SqlSessionFactory 创建时注册一次
   - 如果当时的 ClassLoader 上下文不对，后续无法补救

3. **SBP 插件框架的特殊性**：
   - 插件有独立的 ClassLoader
   - 线程上下文 ClassLoader 可能指向主应用
   - 导致 ClassLoader 上下文混乱

### 为什么全限定类名可以工作

1. **动态加载**：每次使用时都尝试加载类
2. **多 ClassLoader 尝试**：会尝试多个 ClassLoader，包括线程上下文 CL
3. **更高的容错性**：即使第一个 ClassLoader 失败，还有其他备选

### 解决方案对比

| 方案 | 优点 | 缺点 |
|-----|------|------|
| 使用全限定类名 | 100% 可靠，绕过别名机制 | Mapper XML 冗长 |
| 修复 ClassLoader 上下文 | 保留别名机制 | 需要深入修改框架 |
| 手动注册别名 | 灵活控制 | 需要修改插件源码 |

## 验证脚本

如果有插件源码，可以添加以下调试代码验证：

```java
@Configuration
public class MybatisPlusConfiguration {
    
    @Bean
    public ConfigurationCustomizer configurationCustomizer() {
        return configuration -> {
            System.out.println("=== MyBatis ClassLoader 诊断 ===");
            
            // 1. 打印 ClassLoader 信息
            ClassLoader threadCL = Thread.currentThread().getContextClassLoader();
            ClassLoader thisCL = this.getClass().getClassLoader();
            
            System.out.println("线程上下文 CL: " + threadCL.getClass().getName());
            System.out.println("当前类 CL: " + thisCL.getClass().getName());
            
            // 2. 检查 Type Aliases Registry
            TypeAliasRegistry registry = configuration.getTypeAliasRegistry();
            try {
                Class<?> clazz = registry.resolveAlias("DistributeDto");
                System.out.println("✓ DistributeDto 别名已注册: " + clazz.getName());
            } catch (Exception e) {
                System.out.println("✗ DistributeDto 别名未注册: " + e.getMessage());
            }
            
            // 3. 尝试直接加载类
            try {
                Class<?> clazz = threadCL.loadClass("com.gaoding.ska.statistics.dto.account.response.DistributeDto");
                System.out.println("✓ 线程上下文 CL 能加载类: " + clazz.getName());
            } catch (ClassNotFoundException e) {
                System.out.println("✗ 线程上下文 CL 无法加载类");
            }
            
            System.out.println("=====================================");
        };
    }
}
```

## 参考资料

- [MyBatis TypeAliasRegistry 源码](https://github.com/mybatis/mybatis-3/blob/master/src/main/java/org/apache/ibatis/type/TypeAliasRegistry.java)
- [MyBatis ClassLoaderWrapper 源码](https://github.com/mybatis/mybatis-3/blob/master/src/main/java/org/apache/ibatis/io/ClassLoaderWrapper.java)
- [Java ClassLoader 机制](https://docs.oracle.com/javase/8/docs/api/java/lang/ClassLoader.html)
- [SBP Plugin ClassLoader](https://github.com/hank-cp/sbp)

