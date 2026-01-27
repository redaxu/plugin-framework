# DynamicDataSource 插件集成问题解决方案

## 问题描述

在 SBP 插件框架中使用 `dynamic-datasource-spring-boot-starter` 时，插件启动报错：

```
Field dynamicDataSourceProperties in com.gaoding.ska.statistics.config.DataSourceConfiguration 
required a bean of type 'com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties' 
that could not be found.
```

## 问题原因

`DynamicDataSourceAutoConfiguration` 的源码如下：

```java
@Slf4j
@Configuration
@AllArgsConstructor
@EnableConfigurationProperties(DynamicDataSourceProperties.class)
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@Import(value = {DruidDynamicDataSourceConfiguration.class, DynamicDataSourceCreatorAutoConfiguration.class})
@ConditionalOnProperty(prefix = DynamicDataSourceProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class DynamicDataSourceAutoConfiguration {
    // ...
}
```

问题的根本原因是：

1. **类加载器隔离**：插件有独立的类加载器，与主应用隔离
2. **`@EnableConfigurationProperties` 失效**：在插件环境中，`@EnableConfigurationProperties` 注解可能无法正确工作，导致 `DynamicDataSourceProperties` Bean 没有被创建
3. **自动配置条件不满足**：虽然 `META-INF/spring.factories` 中包含了 `DynamicDataSourceAutoConfiguration`，但由于上述原因，自动配置没有生效

## 解决方案

### 方案1：手动注册 DynamicDataSourceProperties Bean（推荐）

在插件的配置类中手动创建 `DynamicDataSourceProperties` Bean：

```java
package com.gaoding.ska.statistics.config;

import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 动态数据源配置
 * 
 * 注意：在插件环境中，@EnableConfigurationProperties 可能不工作，
 * 因此需要手动创建 DynamicDataSourceProperties Bean
 */
@Configuration
public class DynamicDataSourceConfig {

    /**
     * 手动创建 DynamicDataSourceProperties Bean
     * 这样可以确保在插件环境中也能正确加载配置
     */
    @Bean
    @ConfigurationProperties(prefix = DynamicDataSourceProperties.PREFIX)
    public DynamicDataSourceProperties dynamicDataSourceProperties() {
        return new DynamicDataSourceProperties();
    }
}
```

**说明：**
- `@ConfigurationProperties(prefix = DynamicDataSourceProperties.PREFIX)` 会自动绑定 `spring.datasource.dynamic.*` 配置
- `DynamicDataSourceProperties.PREFIX` 的值是 `"spring.datasource.dynamic"`
- 这个 Bean 会被 `DynamicDataSourceAutoConfiguration` 自动注入使用

### 方案2：确保 spring.factories 正确合并

如果插件使用 `maven-shade-plugin` 打包，需要确保 `META-INF/spring.factories` 被正确合并：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <executions>
        <execution>
            <id>plugin-jar</id>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <transformers>
                    <!-- 合并 META-INF/spring.factories -->
                    <transformer implementation="org.apache.maven.plugins.shade.resource.AppendingTransformer">
                        <resource>META-INF/spring.factories</resource>
                    </transformer>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 方案3：在主应用中提供 DynamicDataSourceProperties（不推荐）

如果多个插件都需要使用 `dynamic-datasource`，可以考虑在主应用中提供这个 Bean，但这会导致：
- 主应用和插件共享同一个配置实例
- 可能导致配置冲突
- 不符合插件独立性原则

**因此不推荐这种方案。**

## 验证方案

### 1. 检查 Bean 是否创建成功

在插件启动时，添加日志验证：

```java
@Configuration
public class DynamicDataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = DynamicDataSourceProperties.PREFIX)
    public DynamicDataSourceProperties dynamicDataSourceProperties() {
        DynamicDataSourceProperties properties = new DynamicDataSourceProperties();
        System.out.println("=== 手动创建 DynamicDataSourceProperties Bean ===");
        return properties;
    }
}
```

### 2. 检查配置是否正确加载

在 `DataSourceConfiguration` 中添加日志：

```java
@Configuration
public class DataSourceConfiguration {

    @Autowired
    private DynamicDataSourceProperties dynamicDataSourceProperties;

    @PostConstruct
    public void init() {
        System.out.println("=== DynamicDataSourceProperties 注入成功 ===");
        System.out.println("Primary: " + dynamicDataSourceProperties.getPrimary());
        System.out.println("Datasources: " + dynamicDataSourceProperties.getDatasource().keySet());
    }
}
```

### 3. 检查数据源是否正常工作

启动插件后，尝试访问数据库，确认数据源切换功能正常。

## 配置示例

插件的 `application.properties` 配置示例：

```properties
# 多数据源配置
spring.datasource.dynamic.primary=master
spring.datasource.dynamic.strict=false

# ska 数据源
spring.datasource.dynamic.datasource.master.url=jdbc:mysql://localhost:3306/ska
spring.datasource.dynamic.datasource.master.username=root
spring.datasource.dynamic.datasource.master.password=password
spring.datasource.dynamic.datasource.master.driver-class-name=com.mysql.cj.jdbc.Driver

# uc 数据源
spring.datasource.dynamic.datasource.uc.url=jdbc:mysql://localhost:3306/uc
spring.datasource.dynamic.datasource.uc.username=root
spring.datasource.dynamic.datasource.uc.password=password
spring.datasource.dynamic.datasource.uc.driver-class-name=com.mysql.cj.jdbc.Driver
```

## 常见问题

### Q1: 为什么主应用中不需要手动创建这个 Bean？

**A:** 主应用使用标准的 Spring Boot 自动配置机制，`@EnableConfigurationProperties` 可以正常工作。但在插件环境中，由于类加载器隔离和 Spring 上下文隔离，自动配置可能不完全生效。

### Q2: 这个方案会影响其他自动配置吗？

**A:** 不会。我们只是手动创建了 `DynamicDataSourceProperties` Bean，其他自动配置（如 `DynamicDataSourceAutoConfiguration`）仍然会正常工作。

### Q3: 如果插件不需要使用 dynamic-datasource，应该怎么办？

**A:** 如果插件不需要使用多数据源功能，可以在插件的启动类上排除这个自动配置：

```java
@SpringBootApplication(exclude = {DynamicDataSourceAutoConfiguration.class})
public class MyPluginApplication {
    // ...
}
```

或者在 `application.properties` 中禁用：

```properties
spring.datasource.dynamic.enabled=false
```

## 参考资料

- [dynamic-datasource-spring-boot-starter 官方文档](https://github.com/baomidou/dynamic-datasource-spring-boot-starter)
- [Spring Boot @ConfigurationProperties 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.typesafe-configuration-properties)
- [SBP 插件框架文档](https://github.com/hank-cp/sbp)

