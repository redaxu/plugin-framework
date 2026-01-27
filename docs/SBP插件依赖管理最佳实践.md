# SBP 插件依赖管理最佳实践

## 问题背景

在使用 SBP（Spring Boot Plugin）插件框架时，经常会遇到插件启动失败的问题，原因通常是：

1. **缺少必需的 Bean**（如 `DynamicDataSourceProperties`、`SqlSessionFactory` 等）
2. **类加载冲突**（插件和主应用加载了不同版本的类）
3. **自动配置失效**（如 `@EnableConfigurationProperties` 在插件环境中不工作）

## 核心原则

**框架组件应该由主应用提供，插件只负责业务逻辑。**

### 为什么？

1. **类加载器优先级**：SBP 插件框架会优先从主应用的类加载器加载类
2. **版本统一**：主应用提供统一的框架版本，避免插件之间的版本冲突
3. **减少插件体积**：插件不需要打包框架组件，减小 JAR 包体积
4. **简化依赖管理**：框架升级只需要更新主应用，不需要重新打包所有插件

## 常见问题和解决方案

### 问题1：DynamicDataSourceProperties Bean 缺失

#### 错误信息

```
Field dynamicDataSourceProperties in com.xxx.config.DataSourceConfiguration 
required a bean of type 'com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties' 
that could not be found.
```

#### 解决方案

在主应用 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>dynamic-datasource-spring-boot-starter</artifactId>
</dependency>
```

**详细文档**：`docs/DynamicDataSource插件集成问题解决.md`

---

### 问题2：BaseRepository 分页查询方法签名错误

#### 错误信息

```
java.lang.IllegalArgumentException: Paging query needs to have a Pageable parameter! 
Offending method: public abstract org.springframework.data.domain.Page com.gaoding.orm.jpa.repository.BaseRepository.findAll(...)
```

#### 解决方案

在主应用 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>com.gaoding.orm</groupId>
    <artifactId>jpa</artifactId>
    <version>0.0.6</version>
</dependency>
```

**详细文档**：`docs/BaseRepository分页查询问题解决.md`

---

### 问题3：MyBatis Mapper 缺少 SqlSessionFactory

#### 错误信息

```
Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required
```

#### 原因

MyBatis Mapper 需要 `SqlSessionFactory` Bean，但在插件的 Spring 上下文中找不到。这是因为 MyBatis 自动配置没有在插件上下文中正确初始化。

#### 解决方案

在主应用 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
</dependency>
```

**注意**：
- 如果使用原生 MyBatis，添加 `mybatis-spring-boot-starter`
- 如果使用 MyBatis Plus，添加 `mybatis-plus-boot-starter`
- 版本号通常在父 POM 的 `<dependencyManagement>` 中管理

---

### 问题4：JMX MBean 名称冲突

#### 错误信息

```
javax.management.InstanceAlreadyExistsException: org.springframework.boot:type=Admin,name=SpringApplication
```

#### 原因

主应用和插件都尝试注册同一个 JMX MBean 名称 `org.springframework.boot:type=Admin,name=SpringApplication`，导致冲突。这是因为 Spring Boot Admin JMX 自动配置在主应用和插件中都被启用了。

#### 解决方案

在主应用的 `application.yml` 中禁用 JMX：

```yaml
spring:
  application:
    name: plugin-manager
    # 禁用 Spring Boot Admin JMX，避免与插件冲突
    admin:
      enabled: false
  # 禁用 JMX，避免主应用和插件的 MBean 名称冲突
  jmx:
    enabled: false
```

**说明**：
- `spring.application.admin.enabled=false` 禁用 Spring Boot Admin MXBean
- `spring.jmx.enabled=false` 完全禁用 JMX 支持
- 如果需要 JMX 监控，可以只禁用 Admin MXBean，保留其他 MBean

**替代方案**（如果需要保留 JMX）：

在插件的配置中禁用 JMX（需要修改插件源码）：

```properties
# 插件的 application.properties
spring.application.admin.enabled=false
```

或者在插件启动类中排除自动配置：

```java
@SpringBootApplication(exclude = {
    SpringApplicationAdminJmxAutoConfiguration.class
})
public class PluginApplication {
    // ...
}
```

---

### 问题5：MyBatis Type Alias 类加载失败

#### 错误信息

```
Could not resolve type alias 'DistributeDto'.  
Cause: java.lang.ClassNotFoundException: Cannot find class: DistributeDto
```

#### 原因

MyBatis 在解析 Mapper XML 时，使用类型别名（如 `resultType="DistributeDto"`）无法在插件的类加载器中找到对应的类。这是因为：

1. **类加载器隔离**：插件有独立的 `PluginClassLoader`
2. **MyBatis 类加载机制**：MyBatis 尝试使用多个 ClassLoader 加载类，但可能使用了错误的 ClassLoader
3. **类型别名配置**：Mapper XML 使用简单别名而不是全限定类名

#### 解决方案

**最佳方案**：修改插件的 Mapper XML，使用全限定类名：

```xml
<!-- 原来的写法（使用别名） -->
<select id="selectData" resultType="DistributeDto">
    SELECT * FROM table
</select>

<!-- 修改为全限定类名 -->
<select id="selectData" resultType="com.gaoding.ska.statistics.dto.account.response.DistributeDto">
    SELECT * FROM table
</select>
```

**替代方案**：在插件的 MyBatis 配置中手动注册类型别名：

```java
@Configuration
public class MybatisPlusConfiguration {
    
    @Bean
    public ConfigurationCustomizer configurationCustomizer() {
        return configuration -> {
            TypeAliasRegistry registry = configuration.getTypeAliasRegistry();
            registry.registerAlias("DistributeDto", DistributeDto.class);
        };
    }
}
```

**详细文档**：`docs/MyBatis-TypeAlias类加载问题解决.md`

---

## 主应用依赖清单

根据实际项目需求，主应用应该包含以下依赖（供插件使用）：

### 0. 主应用配置

**重要**：在主应用的 `application.yml` 中添加以下配置，避免与插件冲突：

```yaml
spring:
  application:
    name: plugin-manager
    # 禁用 Spring Boot Admin JMX，避免与插件冲突
    admin:
      enabled: false
  # 禁用 JMX，避免主应用和插件的 MBean 名称冲突
  jmx:
    enabled: false
  sbp:
    enabled: true
    plugins-dir: plugins
```

### 1. 核心框架依赖

```xml
<!-- Spring Boot 核心依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- SBP 插件框架 -->
<dependency>
    <groupId>org.laxture</groupId>
    <artifactId>sbp-spring-boot-starter</artifactId>
</dependency>
```

### 2. 数据访问依赖

```xml
<!-- JPA 支持 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- 稿定 ORM 组件 -->
<dependency>
    <groupId>com.gaoding.orm</groupId>
    <artifactId>jpa</artifactId>
    <version>0.0.6</version>
</dependency>

<!-- MyBatis Plus -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
</dependency>

<!-- 多数据源支持 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>dynamic-datasource-spring-boot-starter</artifactId>
</dependency>

<!-- MySQL 驱动 -->
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
</dependency>

<!-- 数据库连接池 -->
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>druid</artifactId>
</dependency>
```

### 3. Redis 依赖

```xml
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-redis</artifactId>
</dependency>

<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
</dependency>

<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
</dependency>
```

### 4. Elasticsearch 依赖

```xml
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-elasticsearch</artifactId>
</dependency>
```

### 5. gRPC 依赖

```xml
<dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-client-spring-boot-autoconfigure</artifactId>
</dependency>

<dependency>
    <groupId>com.gaoding.grpc</groupId>
    <artifactId>common</artifactId>
</dependency>
```

### 6. 工具类依赖

```xml
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpclient</artifactId>
</dependency>

<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
</dependency>

<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

---

## 插件依赖管理

### 1. 设置依赖作用域为 `provided`

对于主应用已提供的依赖，插件应该设置为 `provided` 作用域：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <scope>provided</scope>
</dependency>

<dependency>
    <groupId>com.gaoding.orm</groupId>
    <artifactId>jpa</artifactId>
    <scope>provided</scope>
</dependency>

<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <scope>provided</scope>
</dependency>
```

### 2. 打包时排除依赖

使用 `maven-shade-plugin` 打包时，排除主应用已提供的依赖：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <configuration>
        <artifactSet>
            <excludes>
                <!-- Spring Boot 相关 -->
                <exclude>org.springframework.boot:*</exclude>
                <exclude>org.springframework:*</exclude>
                
                <!-- SBP 框架 -->
                <exclude>org.laxture:sbp-spring-boot-starter</exclude>
                <exclude>org.pf4j:pf4j</exclude>
                
                <!-- 数据访问相关 -->
                <exclude>com.gaoding.orm:jpa</exclude>
                <exclude>com.baomidou:mybatis-plus-*</exclude>
                <exclude>com.baomidou:dynamic-datasource-*</exclude>
                
                <!-- 通用组件 -->
                <exclude>com.gaoding.framework:*</exclude>
                <exclude>com.gaoding.grpc:*</exclude>
                
                <!-- 数据库驱动 -->
                <exclude>mysql:mysql-connector-java</exclude>
                <exclude>com.alibaba:druid</exclude>
            </excludes>
        </artifactSet>
    </configuration>
</plugin>
```

---

## 问题排查流程

当插件启动失败时，按以下流程排查：

### 1. 查看错误信息

关键信息：
- 缺少哪个 Bean？
- 缺少哪个类？
- 哪个自动配置失败？

### 2. 确定缺失的依赖

根据错误信息确定缺失的依赖，例如：

| 错误关键词 | 缺失的依赖或配置 |
|-----------|----------|
| `DynamicDataSourceProperties` | `dynamic-datasource-spring-boot-starter` |
| `BaseRepository` | `com.gaoding.orm:jpa` |
| `SqlSessionFactory` | `mybatis-plus-boot-starter` 或 `mybatis-spring-boot-starter` |
| `RedisTemplate` | `spring-data-redis` |
| `ElasticsearchTemplate` | `spring-data-elasticsearch` |
| `InstanceAlreadyExistsException: SpringApplication` | 禁用 JMX：`spring.jmx.enabled=false` |

### 3. 检查插件中打包的版本

```bash
# 查看插件中的 Maven 依赖信息
jar -tf plugin.jar | grep "META-INF/maven"

# 查看特定依赖的版本
jar -xf plugin.jar META-INF/maven/groupId/artifactId/pom.properties
cat META-INF/maven/groupId/artifactId/pom.properties
```

### 4. 在主应用中添加依赖

在 `plugin-manager/pom.xml` 中添加缺失的依赖：

```xml
<dependency>
    <groupId>com.xxx</groupId>
    <artifactId>xxx</artifactId>
    <version>x.x.x</version>
</dependency>
```

### 5. 重新编译和启动

```bash
cd plugin-manager
mvn clean install -Dmaven.test.skip=true
mvn spring-boot:run
```

### 6. 观察启动日志

确认插件是否成功启动，如果还有错误，重复步骤 1-5。

---

## 注意事项

### 1. 版本兼容性

- 确保主应用和插件使用兼容的框架版本
- 建议在父 POM 中统一管理版本号

### 2. 类加载顺序

- SBP 插件框架会优先从主应用的类加载器加载类
- 如果插件中打包了相同的类，可能导致不可预知的行为

### 3. Spring 上下文隔离

- 主应用和插件有各自独立的 Spring 上下文
- Bean 不能直接跨上下文注入（需要通过 `ApplicationContextProvider` 等方式）

### 4. 自动配置

- 某些自动配置在插件环境中可能不工作（如 `@EnableConfigurationProperties`）
- 需要手动创建 Bean 或在主应用中提供

---

## 参考资料

- [SBP 插件框架官方文档](https://github.com/hank-cp/sbp)
- [SBP Trouble Shooting](https://github.com/hank-cp/sbp/blob/0.1.13/docs/trouble_shoot.md)
- [Spring Boot 依赖管理](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.build-systems.dependency-management)
- [Maven 依赖作用域](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope)

