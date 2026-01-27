# BaseRepository 分页查询方法签名错误解决方案

## 问题描述

插件启动时报错：

```
java.lang.IllegalArgumentException: Paging query needs to have a Pageable parameter! 
Offending method public abstract org.springframework.data.domain.Page com.gaoding.orm.jpa.repository.BaseRepository.findAll(com.gaoding.orm.jpa.search.BaseSearchable)
```

## 问题原因

### 1. Spring Data JPA 规范

根据 Spring Data JPA 的规范，当一个 Repository 方法返回 `Page<T>` 类型时，方法参数中**必须包含 `Pageable` 参数**，用于指定分页信息（页码、每页大小、排序等）。

例如：

```java
// ✅ 正确：返回 Page 类型，包含 Pageable 参数
Page<User> findAll(Pageable pageable);

// ✅ 正确：返回 Page 类型，包含查询条件和 Pageable 参数
Page<User> findAll(Specification<User> spec, Pageable pageable);

// ❌ 错误：返回 Page 类型，但缺少 Pageable 参数
Page<User> findAll(Specification<User> spec);
```

### 2. BaseRepository 的问题

稿定框架的 `com.gaoding.orm.jpa.repository.BaseRepository` 中定义了一个不符合规范的方法：

```java
// 这个方法签名不符合 Spring Data JPA 规范
Page<T> findAll(BaseSearchable searchable);
```

这个方法返回 `Page<T>` 类型，但缺少 `Pageable` 参数，导致 Spring Data JPA 在初始化 Repository 时抛出异常。

### 3. 类加载冲突

在 SBP 插件框架中：

1. **插件打包了 `com.gaoding.orm:jpa` 依赖**（包含 `BaseRepository`）
2. **主应用没有提供这个依赖**
3. **插件加载了自己打包的版本**，导致使用了不符合规范的 `BaseRepository`

## 解决方案

### 方案1：在主应用中添加 orm jpa 依赖（推荐）

在 `plugin-manager/pom.xml` 中添加 `com.gaoding.orm:jpa` 依赖，让主应用提供正确版本的 `BaseRepository`：

```xml
<dependency>
    <groupId>com.gaoding.orm</groupId>
    <artifactId>jpa</artifactId>
    <version>0.0.6</version>
</dependency>
```

**为什么这样做？**

1. **类加载器优先级**：SBP 插件框架会优先从主应用的类加载器加载类
2. **版本统一**：主应用提供统一的 `orm-jpa` 版本，避免插件之间的版本冲突
3. **符合 SBP 最佳实践**：框架组件应该由主应用提供，插件只需要引用

### 方案2：在插件中排除 orm jpa 依赖

如果主应用已经提供了 `orm-jpa`，需要在插件打包时排除这个依赖：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <configuration>
        <artifactSet>
            <excludes>
                <!-- 排除 orm-jpa，由主应用提供 -->
                <exclude>com.gaoding.orm:jpa</exclude>
            </excludes>
        </artifactSet>
    </configuration>
</plugin>
```

或者在插件的 `pom.xml` 中将 `orm-jpa` 设置为 `provided` 作用域：

```xml
<dependency>
    <groupId>com.gaoding.orm</groupId>
    <artifactId>jpa</artifactId>
    <version>0.0.6</version>
    <scope>provided</scope>
</dependency>
```

### 方案3：升级 orm-jpa 版本（如果可能）

如果 `orm-jpa` 有新版本修复了这个问题，可以升级到新版本。

## 实施步骤

### 步骤1：在主应用中添加依赖

编辑 `plugin-manager/pom.xml`：

```xml
<dependencies>
    <!-- ... 其他依赖 ... -->
    
    <!-- 添加 orm jpa 依赖，供插件使用 -->
    <dependency>
        <groupId>com.gaoding.orm</groupId>
        <artifactId>jpa</artifactId>
        <version>0.0.6</version>
    </dependency>
</dependencies>
```

### 步骤2：重新编译主应用

```bash
cd plugin-manager
mvn clean install -Dmaven.test.skip=true
```

### 步骤3：重新启动主应用

```bash
cd plugin-manager
mvn spring-boot:run
```

### 步骤4：验证插件启动

观察插件启动日志，确认不再出现 `Paging query needs to have a Pageable parameter!` 错误。

## 常见问题

### Q1: 为什么不直接修改 BaseRepository 的方法签名？

**A:** `BaseRepository` 是稿定框架的组件，我们不应该直接修改它。应该通过升级框架版本或者在主应用中统一管理来解决。

### Q2: 如果主应用和插件使用不同版本的 orm-jpa 会怎么样？

**A:** 会导致类加载冲突和不可预知的行为。建议在主应用中统一管理版本，插件通过 `provided` 作用域引用。

### Q3: 如何查看插件中打包了哪些依赖？

**A:** 使用以下命令：

```bash
# 查看插件 JAR 中的所有类
jar -tf plugin.jar | head -50

# 查看插件中的 Maven 依赖信息
jar -tf plugin.jar | grep "META-INF/maven"

# 提取并查看特定依赖的版本
jar -xf plugin.jar META-INF/maven/com.gaoding.orm/jpa/pom.properties
cat META-INF/maven/com.gaoding.orm/jpa/pom.properties
```

### Q4: 还有哪些框架组件应该由主应用提供？

**A:** 根据 SBP 最佳实践，以下组件应该由主应用提供：

- `com.gaoding.framework:core` - 核心框架
- `com.gaoding.orm:jpa` - ORM 组件
- `com.gaoding.grpc:common` - gRPC 通用组件
- `com.gaoding.commons:*` - 各类公共组件
- `org.springframework.boot:*` - Spring Boot 组件
- `org.springframework:*` - Spring 核心组件

插件应该将这些依赖设置为 `provided` 作用域，或者在打包时排除。

## 参考资料

- [Spring Data JPA 官方文档 - Paging and Sorting](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.query-methods)
- [SBP 插件框架 - Trouble Shooting](https://github.com/hank-cp/sbp/blob/0.1.13/docs/trouble_shoot.md)
- [Maven Dependency Scopes](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope)

