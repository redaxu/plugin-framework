# Maven 编译缓存问题解决

## 问题描述

编译时报错：

```
java: 无法访问Filter
  找不到Filter的类文件
```

需要执行 `mvn clean package` 才能正常编译，直接 `mvn compile` 或 `mvn package` 会报错。

## 问题原因

### 1. Maven 编译缓存问题

Maven 在编译时会缓存已编译的类文件。当依赖关系发生变化时（如添加新的依赖、修改依赖版本等），Maven 可能不会自动清理旧的缓存，导致：

- 旧的类文件仍然存在
- 新的依赖没有被正确加载
- 编译时找不到类文件

### 2. 依赖传递问题

`javax.servlet.Filter` 是通过 `spring-boot-starter-web` 传递依赖引入的：

```
spring-boot-starter-web
  └── spring-boot-starter-tomcat
      └── tomcat-embed-core
          └── javax.servlet-api (provided scope)
```

如果编译缓存中缺少这个传递依赖，就会导致找不到 `Filter` 类。

### 3. IDE 和 Maven 缓存不一致

IDE（如 IntelliJ IDEA）和 Maven 使用不同的编译缓存：

- **IDE 缓存**：`.idea/` 目录下的缓存
- **Maven 缓存**：`target/` 目录下的编译输出

当两者不一致时，IDE 可能显示编译错误，但 Maven 编译成功（或反之）。

## 解决方案

### 方案1：使用 clean package（推荐）

```bash
mvn clean package
```

**优点**：
- ✅ 清理所有编译缓存
- ✅ 重新下载依赖
- ✅ 确保编译环境干净

**缺点**：
- ⚠️ 编译时间较长
- ⚠️ 需要每次都执行 clean

### 方案2：使用 clean compile

如果只需要编译，不需要打包：

```bash
mvn clean compile
```

### 方案3：清理 IDE 缓存

如果 IDE 显示错误，但 Maven 编译成功：

**IntelliJ IDEA**：
1. `File` → `Invalidate Caches / Restart...`
2. 选择 `Invalidate and Restart`

**Eclipse**：
1. `Project` → `Clean...`
2. 选择项目，点击 `Clean`

### 方案4：配置 Maven 自动清理（不推荐）

在 `pom.xml` 中配置，但会影响编译性能：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-clean-plugin</artifactId>
            <executions>
                <execution>
                    <id>auto-clean</id>
                    <phase>initialize</phase>
                    <goals>
                        <goal>clean</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**不推荐的原因**：
- ❌ 每次编译都会清理，编译时间变长
- ❌ 影响开发效率

### 方案5：显式添加依赖（推荐）

虽然 `spring-boot-starter-web` 已经包含了 `javax.servlet-api`，但它是 `provided` scope，在某些情况下可能不会被正确加载。

**显式添加依赖**：

```xml
<dependency>
    <groupId>javax.servlet</groupId>
    <artifactId>javax.servlet-api</artifactId>
    <scope>provided</scope>
</dependency>
```

**或者使用 Spring Boot 管理的版本**：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<!-- 显式声明 servlet-api，确保编译时可用 -->
<dependency>
    <groupId>javax.servlet</groupId>
    <artifactId>javax.servlet-api</artifactId>
    <scope>provided</scope>
</dependency>
```

## 为什么需要 clean？

### Maven 编译流程

```
1. validate - 验证项目
2. compile - 编译源代码
   ├── 检查 target/classes 目录
   ├── 如果类文件存在且源文件未修改，跳过编译
   └── 如果类文件不存在或源文件已修改，重新编译
3. test-compile - 编译测试代码
4. package - 打包
```

### 问题场景

**场景1：添加新依赖**
```
1. 修改 pom.xml，添加新依赖
2. 执行 mvn compile
   ├── Maven 检查 target/classes
   ├── 发现类文件已存在
   ├── 检查源文件，未修改
   └── 跳过编译 ❌（但依赖已变化）
3. 编译失败：找不到新依赖的类
```

**场景2：修改依赖版本**
```
1. 修改 pom.xml，更新依赖版本
2. 执行 mvn compile
   ├── Maven 检查 target/classes
   ├── 发现类文件已存在
   ├── 检查源文件，未修改
   └── 跳过编译 ❌（但依赖版本已变化）
3. 编译失败：找不到类文件
```

**场景3：删除依赖**
```
1. 修改 pom.xml，删除依赖
2. 执行 mvn compile
   ├── Maven 检查 target/classes
   ├── 发现类文件已存在（旧的）
   ├── 检查源文件，未修改
   └── 跳过编译 ❌（但依赖已删除）
3. 编译失败：找不到类文件
```

### clean 的作用

`mvn clean` 会删除 `target/` 目录，强制 Maven 重新编译：

```
1. mvn clean
   └── 删除 target/ 目录
2. mvn compile
   ├── 检查 target/classes 目录
   ├── 目录不存在
   └── 重新编译所有源文件 ✅
```

## 最佳实践

### 1. 开发时

**日常开发**：
```bash
# 第一次编译或修改依赖后
mvn clean compile

# 只修改源代码时
mvn compile
```

**打包发布**：
```bash
mvn clean package
```

### 2. CI/CD 流程

```bash
# 确保编译环境干净
mvn clean install
```

### 3. IDE 配置

**IntelliJ IDEA**：
- 配置 Maven Runner：`Settings` → `Build, Execution, Deployment` → `Build Tools` → `Maven` → `Runner`
- 勾选 `Delegate IDE build/run actions to Maven`
- 这样 IDE 的编译会使用 Maven，避免缓存不一致

### 4. 显式声明依赖

对于关键依赖（如 `javax.servlet-api`），即使是通过传递依赖引入的，也建议显式声明：

```xml
<dependency>
    <groupId>javax.servlet</groupId>
    <artifactId>javax.servlet-api</artifactId>
    <scope>provided</scope>
</dependency>
```

**优点**：
- ✅ 明确依赖关系
- ✅ 避免编译缓存问题
- ✅ 提高可维护性

## 排查步骤

### 步骤1：检查依赖

```bash
mvn dependency:tree | grep servlet
```

应该看到：
```
[INFO] +- org.springframework.boot:spring-boot-starter-web:jar:2.2.13.RELEASE:compile
[INFO] |  +- org.springframework.boot:spring-boot-starter-tomcat:jar:2.2.13.RELEASE:compile
[INFO] |  |  +- org.apache.tomcat.embed:tomcat-embed-core:jar:9.0.41:compile
[INFO] |  |  |  \- javax.servlet:javax.servlet-api:jar:4.0.1:provided
```

### 步骤2：清理缓存

```bash
# 清理 Maven 本地仓库缓存（可选）
rm -rf ~/.m2/repository/javax/servlet

# 清理项目编译缓存
mvn clean

# 重新编译
mvn compile
```

### 步骤3：检查编译输出

```bash
# 查看编译后的类文件
ls -la target/classes/com/gaoding/ska/customize/config/

# 查看 Filter 类是否存在
find target/classes -name "*.class" | grep Filter
```

## 总结

### 问题

- ❌ 编译时找不到 `Filter` 类
- ❌ 需要 `mvn clean package` 才能编译成功

### 原因

- Maven 编译缓存问题
- 依赖传递问题
- IDE 和 Maven 缓存不一致

### 解决方案

1. ✅ **使用 clean package**：确保编译环境干净
2. ✅ **显式添加依赖**：避免传递依赖问题
3. ✅ **清理 IDE 缓存**：解决 IDE 和 Maven 不一致
4. ✅ **配置 IDE**：使用 Maven 编译，避免缓存问题

### 最佳实践

- 修改依赖后，使用 `mvn clean compile`
- 打包发布时，使用 `mvn clean package`
- 关键依赖显式声明
- 配置 IDE 使用 Maven 编译

这样可以避免大部分编译缓存问题！

