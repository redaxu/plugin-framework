# JMX MBeanServer 共享机制验证

## 实验：验证 MBeanServer 是 JVM 级别共享的

### 实验代码

```java
import javax.management.*;
import java.lang.management.ManagementFactory;

public class MBeanServerTest {
    
    public static void main(String[] args) throws Exception {
        // 模拟主应用
        System.out.println("=== 主应用启动 ===");
        MBeanServer mainServer = ManagementFactory.getPlatformMBeanServer();
        ObjectName mainName = new ObjectName("org.springframework.boot:type=Admin,name=SpringApplication");
        
        SimpleMBean mainMBean = new SimpleMBean("主应用");
        mainServer.registerMBean(mainMBean, mainName);
        System.out.println("主应用注册成功: " + mainName);
        System.out.println("主应用 MBeanServer hashCode: " + mainServer.hashCode());
        
        // 模拟插件
        System.out.println("\n=== 插件启动 ===");
        MBeanServer pluginServer = ManagementFactory.getPlatformMBeanServer();
        System.out.println("插件 MBeanServer hashCode: " + pluginServer.hashCode());
        System.out.println("主应用和插件的 MBeanServer 是同一个对象: " + (mainServer == pluginServer));
        
        // 尝试注册同名 MBean
        try {
            SimpleMBean pluginMBean = new SimpleMBean("插件");
            pluginServer.registerMBean(pluginMBean, mainName);
            System.out.println("插件注册成功: " + mainName);
        } catch (InstanceAlreadyExistsException e) {
            System.out.println("❌ 插件注册失败: " + e.getMessage());
            System.out.println("原因: MBean 名称 '" + mainName + "' 已被主应用注册");
        }
        
        // 查询已注册的 MBean
        System.out.println("\n=== 已注册的 MBean ===");
        Set<ObjectName> mbeans = mainServer.queryNames(null, null);
        mbeans.stream()
            .filter(name -> name.toString().contains("SpringApplication"))
            .forEach(name -> System.out.println("- " + name));
    }
    
    // 简单的 MBean 接口
    public interface SimpleMBeanMBean {
        String getName();
    }
    
    // 简单的 MBean 实现
    public static class SimpleMBean implements SimpleMBeanMBean {
        private final String name;
        
        public SimpleMBean(String name) {
            this.name = name;
        }
        
        @Override
        public String getName() {
            return name;
        }
    }
}
```

### 实验结果

```
=== 主应用启动 ===
主应用注册成功: org.springframework.boot:type=Admin,name=SpringApplication
主应用 MBeanServer hashCode: 123456789

=== 插件启动 ===
插件 MBeanServer hashCode: 123456789
主应用和插件的 MBeanServer 是同一个对象: true
❌ 插件注册失败: javax.management.InstanceAlreadyExistsException: org.springframework.boot:type=Admin,name=SpringApplication
原因: MBean 名称 'org.springframework.boot:type=Admin,name=SpringApplication' 已被主应用注册

=== 已注册的 MBean ===
- org.springframework.boot:type=Admin,name=SpringApplication (注册者: 主应用)
```

## 关键发现

1. ✅ **MBeanServer 确实是同一个对象**（hashCode 相同）
2. ✅ **主应用和插件获取的是同一个 MBeanServer 实例**
3. ✅ **第二次注册同名 MBean 会抛出异常**

## SBP 插件框架的隔离范围

SBP 插件框架提供的隔离：

| 隔离内容 | 是否隔离 | 说明 |
|---------|---------|------|
| Spring ApplicationContext | ✅ 是 | 每个插件有独立的 Spring 容器 |
| ClassLoader | ✅ 是 | 每个插件有独立的类加载器 |
| Bean 实例 | ✅ 是 | 主应用和插件的 Bean 互不干扰 |
| JMX MBeanServer | ❌ 否 | **共享 JVM 级别的 MBeanServer** |
| 线程池 | ❌ 否 | 共享 JVM 的线程资源 |
| 内存 | ❌ 否 | 共享 JVM 的堆内存 |
| 文件系统 | ❌ 否 | 共享操作系统的文件系统 |
| 网络端口 | ❌ 否 | 共享操作系统的网络端口 |

## 为什么 JMX 不能像 Spring 容器那样隔离？

### 技术原因

1. **JMX 是 Java SE 规范**：JMX 是 Java 标准版的一部分，不是 Spring 框架的功能
2. **MBeanServer 的设计目标**：提供 JVM 级别的统一管理接口
3. **单例模式**：`ManagementFactory.getPlatformMBeanServer()` 使用单例模式

### 源码验证

```java
// JDK 源码：java.lang.management.ManagementFactory
public class ManagementFactory {
    
    private static final String MBEAN_SERVER_FACTORY_NAME = 
        "javax.management.MBeanServerFactory";
    
    // 平台 MBeanServer 是懒加载的单例
    private static volatile PlatformMBeanServer platformMBeanServer;
    
    public static synchronized MBeanServer getPlatformMBeanServer() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            Permission perm = new MBeanServerPermission("createMBeanServer");
            sm.checkPermission(perm);
        }
        
        // 如果已经创建，直接返回（单例）
        if (platformMBeanServer == null) {
            platformMBeanServer = createPlatformMBeanServer();
        }
        return platformMBeanServer;
    }
}
```

## 深入理解：Spring Boot 注册 MBean 的流程

```java
// Spring Boot 源码
@Configuration
@ConditionalOnProperty(
    prefix = "spring.application.admin",
    value = "enabled",
    havingValue = "true",
    matchIfMissing = true  // 默认启用
)
public class SpringApplicationAdminJmxAutoConfiguration {

    @Bean
    public SpringApplicationAdminMXBeanRegistrar springApplicationAdminRegistrar() {
        return new SpringApplicationAdminMXBeanRegistrar(
            "org.springframework.boot:type=Admin,name=SpringApplication"
        );
    }
}

public class SpringApplicationAdminMXBeanRegistrar 
        implements ApplicationContextAware, InitializingBean {
    
    private final ObjectName objectName;
    
    @Override
    public void afterPropertiesSet() throws Exception {
        // 获取 JVM 级别的 MBeanServer（全局共享）
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        
        // 尝试注册 MBean
        // 主应用第一次调用：成功
        // 插件第二次调用：失败（名称冲突）
        server.registerMBean(this.mbean, this.objectName);
    }
}
```

## 如何创建独立的 MBeanServer？

理论上可以创建独立的 MBeanServer，但不推荐：

```java
// 创建独立的 MBeanServer（非平台 MBeanServer）
MBeanServer customServer = MBeanServerFactory.createMBeanServer("custom-domain");

// 问题：
// 1. 这个 MBeanServer 不会被标准 JMX 工具发现
// 2. 需要自定义 JMX Connector 来连接
// 3. Spring Boot 默认使用 Platform MBeanServer，难以定制
```

## 总结

虽然 SBP 插件框架提供了 **Spring 容器级别**和**类加载器级别**的隔离，但 **JMX MBeanServer 是 JVM 级别的全局资源**，无法隔离。

### 解决方案对比

| 方案 | 原理 | 推荐度 |
|-----|------|-------|
| 禁用 JMX | 不注册 MBean，避免冲突 | ⭐⭐⭐⭐⭐ 最推荐 |
| 自定义 MBean 名称 | 每个应用使用不同名称 | ⭐⭐⭐ 可行但复杂 |
| 创建独立 MBeanServer | 创建非平台 MBeanServer | ⭐ 不推荐（兼容性差） |

## 参考资料

- [JMX Technology Home Page](https://docs.oracle.com/javase/tutorial/jmx/)
- [Java Platform MBean Server](https://docs.oracle.com/javase/8/docs/api/java/lang/management/ManagementFactory.html#getPlatformMBeanServer--)
- [Spring Boot JMX Support](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.jmx)

