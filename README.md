# SKA Plugin Framework Demo

基于 PF4J 和 Spring Boot 2.2 的插件管理与运行框架演示项目。

## 项目结构

```
ska-plugin-framework-demo/
├── plugin-manager/          # 插件管理模块（主应用）
│   ├── src/main/java/
│   │   └── com/gaoding/ska/customize/
│   │       ├── PluginManagerApplication.java    # 主启动类
│   │       ├── config/
│   │       │   └── Pf4jConfiguration.java      # PF4J 配置
│   │       └── controller/
│   │           └── PluginController.java      # 插件管理 API
│   └── pom.xml
├── activity-plugin/          # 活动管理插件模块
│   ├── src/main/java/
│   │   └── com/gaoding/ska/customize/
│   │       ├── plugin/
│   │       │   └── ActivityPlugin.java        # 插件入口类
│   │       ├── entity/
│   │       │   └── Activity.java              # 活动实体
│   │       ├── dto/
│   │       │   ├── ActivityDTO.java           # 活动 DTO
│   │       │   └── ActivityCreateRequest.java # 创建请求 DTO
│   │       ├── dao/
│   │       │   └── ActivityRepository.java    # 活动数据访问层
│   │       ├── service/
│   │       │   └── ActivityService.java       # 活动服务层
│   │       └── controller/
│   │           └── ActivityController.java    # 活动控制器
│   └── pom.xml
├── plugins/                 # 插件存放目录（编译后的插件 JAR 文件）
└── pom.xml                   # 父 POM
```

## 技术栈

- Java 8
- Spring Boot 2.2.13.RELEASE
- SBP Spring Boot Starter 0.1.13 (org.laxture:sbp-spring-boot-starter)
- PF4J 3.9.0
- Spring Data JPA
- MySQL
- Maven Shade Plugin（插件打包，包含所有依赖）

## 构建和运行

### 1. 构建项目

```bash
mvn clean install
```

### 2. 构建插件

```bash
cd activity-plugin
mvn clean package
```

构建完成后，插件 JAR 文件将位于 `activity-plugin/target/activity-plugin-1.0-SNAPSHOT.jar`

### 3. 部署插件

将插件 JAR 文件复制到插件目录：

```bash
mkdir -p plugins
cp activity-plugin/target/activity-plugin-1.0-SNAPSHOT.jar plugins/
```

或者使用构建脚本：

```bash
./build-plugin.sh
```

### 4. 运行主应用

```bash
cd plugin-manager
mvn spring-boot:run
```

或者直接运行：

```bash
java -jar plugin-manager/target/plugin-manager-1.0-SNAPSHOT.jar
```

## API 接口

### 插件管理 API

- `GET /sbp/list` - 获取所有插件列表
- `GET /sbp/reload-all` - 重新加载所有插件
- `POST /sbp/start/{pluginId}` - 启动插件
- `POST /sbp/stop/{pluginId}` - 停止插件

### 活动管理 API（插件提供）

- `POST /api/activities` - 创建活动
- `GET /api/activities` - 获取所有活动（支持 status 查询参数）
- `GET /api/activities/{id}` - 获取指定活动
- `PUT /api/activities/{id}` - 更新活动
- `DELETE /api/activities/{id}` - 删除活动

## 配置说明

### 插件目录配置

在 `plugin-manager/src/main/resources/application.yml` 中配置：

```yaml
spring:
  pf4j:
    enabled: true
    plugins-dir: plugins
    mode: development
```

插件 JAR 文件应放置在项目根目录下的 `plugins/` 目录中。

**注意**：`pf4j-spring-boot-starter` 会自动配置 `PluginManager` Bean，无需手动创建配置类。

## 数据库

### 活动插件数据库配置

活动插件使用 MySQL 数据库，需要先创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS activity_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 数据库配置

在 `activity-plugin/src/main/resources/application.yml` 中配置 MySQL 连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/activity_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: root  # 请修改为您的 MySQL 密码
```

#### 表结构

活动表会在应用启动时自动创建（`ddl-auto: update`），或者可以手动执行 `activity-plugin/docs/schema.sql` 中的 SQL 脚本。

## 开发说明

### 创建新插件

1. 在项目根目录下创建新的 Maven 模块
2. 实现 `org.pf4j.Plugin` 接口
3. 在 `src/main/resources/plugin.properties` 中配置插件信息
4. 在 `pom.xml` 中配置插件清单信息
5. 构建插件并部署到 `plugins` 目录

### 插件开发规范

- 插件类必须继承 `org.laxture.sbp.SpringBootPlugin`
- 插件必须实现 `createSpringBootstrap()` 方法，返回 `SpringBootstrap` 实例
- 插件 JAR 文件的 MANIFEST.MF 必须包含插件元数据（Plugin-Id, Plugin-Version, Plugin-Class）
- 使用 `maven-shade-plugin` 将插件的所有依赖打包到插件 JAR 中
- 插件的依赖会被封装在插件 JAR 中，实现完全隔离

## 注意事项

- 确保使用 JDK 8 编译和运行（稿定默认jdk版本）
- 插件模块需要独立打包为 JAR 文件（使用 `maven-shade-plugin` 打包所有依赖）
- 插件 JAR 文件需要放置在配置的插件目录中
- 插件启动时会自动加载并注册到 Spring 容器中
- 插件的依赖会被封装在插件 JAR 中，实现完全隔离
- 类加载器优先从插件中加载类，避免与主应用的依赖冲突

### 插件依赖类加载问题

**重要**：当插件使用 Spring Boot 自动配置功能时（如数据源自动配置），需要注意以下问题：

#### 问题描述

Spring Boot 的自动配置类（如 `DataSourceConfiguration$c`）在主应用的类加载器中运行。当这些自动配置类使用 `new` 关键字实例化 bean 时，会使用主应用的类加载器来加载依赖类（如 `HikariDataSource`）。即使插件 JAR 中包含这些类，主应用的类加载器也无法访问插件中的类，导致 `ClassNotFoundException` 或 `NoClassDefFoundError` 错误。

#### 解决方案

根据 SBP 官方文档建议，**需要在主应用中添加插件可能使用的第三方库依赖**。

**示例**：如果插件使用了 HikariCP 作为数据源连接池，需要在主应用的 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
</dependency>
```

**原因**：
- Spring Boot 自动配置类在主应用的类加载器中
- 自动配置类使用 `new` 关键字实例化 bean 时，类会被主应用的类加载器加载
- 主应用的类加载器无法访问插件中的类
- 因此需要在主应用中添加相关依赖

**其他可能需要的依赖**：
- 如果插件使用了 MySQL 驱动，可能也需要在主应用中添加 `mysql-connector-java` 依赖
- 如果插件使用了其他第三方库，且这些库被 Spring Boot 自动配置类引用，也需要在主应用中添加相应依赖

**参考**：SBP 官方文档 - [I get ClassNotFoundException](https://github.com/laxture/sbp-spring-boot-starter)

