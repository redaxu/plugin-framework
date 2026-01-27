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

## 项目功能

### 1. 插件管理功能

本项目实现了完整的插件生命周期管理功能，包括：

- **插件加载**：自动扫描并加载 `plugins/` 目录下的插件 JAR 文件
- **插件启动**：支持动态启动已加载的插件
- **插件停止**：支持动态停止运行中的插件
- **插件列表**：查询所有已加载的插件及其状态信息
- **插件重载**：支持重新加载所有插件，实现热更新

### 2. 活动管理插件

实现了完整的活动管理功能作为插件示例，包括：

- **活动创建**：支持创建新的活动，包含活动名称、描述、开始时间、结束时间、状态等信息
- **活动查询**：
  - 查询所有活动列表
  - 根据活动 ID 查询单个活动详情
  - 支持按状态（ACTIVE、INACTIVE）筛选活动
- **活动更新**：支持更新活动信息
- **活动删除**：支持删除指定活动

### 3. Spring Boot 集成

- **自动配置**：基于 SBP Spring Boot Starter 实现插件框架的自动配置
- **独立 Spring 上下文**：每个插件拥有独立的 Spring 应用上下文，实现插件间的隔离
- **Bean 注册**：插件中的 Controller、Service、Repository 等组件自动注册到 Spring 容器
- **RESTful API**：插件提供的 API 自动注册到主应用的路由中

### 4. 数据库集成

- **JPA 数据访问**：使用 Spring Data JPA 实现数据持久化
- **MySQL 数据库**：活动插件使用 MySQL 数据库存储数据
- **自动建表**：支持通过 JPA 自动创建和更新数据库表结构
- **事务管理**：支持 Spring 事务管理，确保数据一致性

### 5. 插件隔离机制

- **依赖隔离**：使用 `maven-shade-plugin` 将插件的所有依赖打包到插件 JAR 中
- **类加载隔离**：每个插件使用独立的类加载器，避免类冲突
- **配置隔离**：每个插件拥有独立的配置文件和应用上下文

### 6. 插件开发框架

- **标准化开发**：提供标准的插件开发规范和模板
- **快速集成**：基于 Spring Boot 的插件开发，简化开发流程
- **热部署**：支持插件热更新，无需重启主应用

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

### 插件中使用过滤器和拦截器（代理模式）

插件中可以定义 Filter 和 Interceptor 来拦截和处理 HTTP 请求。由于插件所在的 Spring 容器并非 Web 容器，且无法在应用启动后直接注册组件，因此采用**代理模式**实现动态注册。

#### 方案1：Filter（过滤器）- Servlet 层

1. **主应用启动时注册代理 Filter**：`PluginDelegatingFilter` 在主应用启动时注册到 ServletContext
2. **在插件中定义 Filter**：使用 `@Component` 注解将 Filter 注册为 Spring Bean
3. **主应用监听插件启动**：`FilterConfiguration` 监听 `SbpPluginStartedEvent` 事件
4. **注册到 Filter 管理器**：从插件上下文获取 Filter Bean，注册到 `PluginFilterManager`
5. **代理 Filter 动态执行**：HTTP 请求到达时，`PluginDelegatingFilter` 从管理器获取所有插件 Filter 并执行

**详细说明**：请参考 [Filter注册机制说明-v2.md](docs/Filter注册机制说明-v2.md)

#### 方案2：Interceptor（拦截器）- Spring MVC 层

1. **主应用启动时注册代理 Interceptor**：`PluginDelegatingInterceptor` 在主应用启动时注册到 Spring MVC
2. **在插件中定义 Interceptor**：使用 `@Component` 注解将 `HandlerInterceptor` 注册为 Spring Bean
3. **主应用监听插件启动**：`FilterConfiguration` 监听 `SbpPluginStartedEvent` 事件
4. **注册到 Interceptor 管理器**：从插件上下文获取 Interceptor Bean，注册到 `PluginInterceptorManager`
5. **代理 Interceptor 动态执行**：HTTP 请求到达 Controller 时，`PluginDelegatingInterceptor` 从管理器获取所有插件 Interceptor 并执行

**详细说明**：请参考 [方案2-Interceptor注册实现.md](docs/方案2-Interceptor注册实现.md)

#### Filter vs Interceptor

| 维度 | Filter（方案1） | Interceptor（方案2） |
|------|----------------|---------------------|
| **工作层次** | Servlet 容器层 | Spring MVC 层 |
| **拦截范围** | 所有请求（包括静态资源） | 仅 Controller 请求 |
| **执行时机** | 在 DispatcherServlet 之前 | 在 DispatcherServlet 之后，Controller 之前 |
| **访问 Controller 信息** | ❌ 无法访问 | ✅ 可以访问 Handler、ModelAndView 等 |
| **适用场景** | 编码、认证、日志、跨域等 | 权限检查、日志、性能监控等 |

**推荐**：优先使用 Filter（方案1），如果需要访问 Controller 信息，可以使用 Interceptor（方案2），或者两者配合使用。

#### 实现步骤

**1. 在插件中定义 Filter**

```java
@Component
@Order(1)
public class ActivityPluginFilter extends OncePerRequestFilter {
    
    @Autowired
    private ActivityPluginProperties pluginProperties;
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 定义需要过滤的 URL 模式
        String requestURI = request.getRequestURI();
        return !(requestURI != null && requestURI.startsWith("/api/activities"));
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) 
            throws ServletException, IOException {
        // 实现过滤逻辑
        // ...
        filterChain.doFilter(request, response);
    }
}
```

**2. 在主应用中配置 Filter 注册（代理模式）**

主应用需要实现以下组件来支持动态 Filter 注册：

**PluginFilterManager（Filter 管理器）**：

```java
@Component
public class PluginFilterManager {
    
    private final Map<String, Filter> pluginFilters = new ConcurrentHashMap<>();
    
    public void registerFilter(String filterKey, Filter filter) {
        pluginFilters.put(filterKey, filter);
    }
    
    public void unregisterPluginFilters(String pluginId) {
        String prefix = pluginId + ":";
        pluginFilters.keySet().removeIf(key -> key.startsWith(prefix));
    }
    
    public Collection<Filter> getAllFilters() {
        return pluginFilters.values();
    }
}
```

**PluginDelegatingFilter（代理 Filter）**：

```java
@Component
public class PluginDelegatingFilter implements Filter {
    
    @Autowired
    private PluginFilterManager pluginFilterManager;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        Collection<Filter> pluginFilters = pluginFilterManager.getAllFilters();
        
        if (pluginFilters.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        
        // 创建包含插件 Filter 的 Filter 链并执行
        FilterChain pluginFilterChain = new PluginFilterChain(pluginFilters.iterator(), chain);
        pluginFilterChain.doFilter(request, response);
    }
}
```

**FilterConfiguration（配置类）**：

```java
@Configuration
public class FilterConfiguration {

    @Autowired
    private PluginFilterManager pluginFilterManager;

    // 注册代理 Filter
    @Bean
    public FilterRegistrationBean<PluginDelegatingFilter> pluginDelegatingFilter(
            PluginDelegatingFilter filter) {
        
        FilterRegistrationBean<PluginDelegatingFilter> registration = 
            new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setName("pluginDelegatingFilter");
        registration.setOrder(1);
        
        return registration;
    }

    // 监听插件启动事件
    @EventListener
    public void onPluginStarted(SbpPluginStartedEvent event) {
        SpringBootPlugin plugin = (SpringBootPlugin) event.getSource();
        String pluginId = plugin.getWrapper().getPluginId();
        ApplicationContext pluginContext = plugin.getApplicationContext();
        
        // 从插件上下文中获取所有 Filter Bean
        String[] filterBeanNames = pluginContext.getBeanNamesForType(Filter.class);
        
        for (String beanName : filterBeanNames) {
            Filter filter = pluginContext.getBean(beanName, Filter.class);
            String filterKey = pluginId + ":" + beanName;
            pluginFilterManager.registerFilter(filterKey, filter);
        }
    }

    // 监听插件停止事件
    @EventListener
    public void onPluginStopped(SbpPluginStoppedEvent event) {
        SpringBootPlugin plugin = (SpringBootPlugin) event.getSource();
        String pluginId = plugin.getWrapper().getPluginId();
        pluginFilterManager.unregisterPluginFilters(pluginId);
    }
}
```

#### 注意事项

- **Filter 定义在插件中**：Filter 类使用 `@Component` 注解，由插件的 Spring 容器管理
- **使用代理模式**：主应用启动时注册代理 Filter，动态查找并执行插件 Filter
- **不能直接注册到 ServletContext**：应用启动后无法调用 `ServletContext.addFilter()`，会抛出 `IllegalStateException`
- **事件驱动注册**：主应用监听 `SbpPluginStartedEvent` 事件，将插件 Filter 注册到管理器
- **支持动态添加/删除**：插件启动时自动注册 Filter，插件停止时自动注销 Filter
- **URL 模式匹配**：代理 Filter 拦截所有请求，插件 Filter 在 `shouldNotFilter()` 方法中判断是否需要过滤
- **保持插件隔离性**：插件 Filter 由插件容器管理，可以注入插件中的 Bean

#### 示例

参考项目中的 `ActivityPluginFilter`（插件中定义）和 `FilterConfiguration`（主应用中注册）的实现。

## 注意事项

- 确保使用 JDK 8 编译和运行（稿定默认jdk版本）
- 插件模块需要独立打包为 JAR 文件（使用 `maven-shade-plugin` 打包所有依赖）
- 插件 JAR 文件需要放置在配置的插件目录中
- 插件启动时会自动加载并注册到 Spring 容器中
- 插件的依赖会被封装在插件 JAR 中，实现完全隔离
- 类加载器优先从插件中加载类，避免与主应用的依赖冲突

### 已知问题

- **插件中无法使用在启动时使用类似ServletContext的bean**：只有主应用才有，插件所在的spring容器并非web spring容器
- **稿定原有组件无法使用**：稿定原有的Prometheus插件、通用的CoreFrameworkConfiguration，也无法使用，需要去除


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

### 插件类加载机制与 spring.factories 共享

#### 容器隔离 vs 类加载器隔离

**重要概念**：Spring 容器隔离 ≠ 类加载器完全隔离

| 维度 | 主应用 | 插件 | 是否隔离 |
|------|--------|------|---------|
| **Spring ApplicationContext** | 独立 | 独立 | ✅ 完全隔离 |
| **Bean 实例** | 独立 | 独立 | ✅ 完全隔离 |
| **类加载器** | AppClassLoader | PluginClassLoader (parent: AppClassLoader) | ⚠️ 父子关系 |
| **spring.factories** | 主应用的 | 主应用的 + 插件的 | ❌ 共享（向上委托） |
| **框架类（Spring Boot）** | 共享 | 共享 | ❌ 共享 |
| **业务类** | 主应用的 | 插件的 | ✅ 隔离 |

#### 类加载器的父子委托机制

插件的 `PluginClassLoader` 使用主应用的类加载器作为父类加载器：

```java
// CustomPluginLoader.java
PluginClassLoader pluginClassLoader = new SpringBootPluginClassLoader(
    pluginManager, 
    pluginDescriptor, 
    getClass().getClassLoader()  // 主应用的类加载器作为父类加载器
);
```

**类加载器层次结构**：

```
AppClassLoader (主应用)
    ↑ parent (父子委托)
PluginClassLoader (插件)
```

当插件加载类时：
1. **先委托给父类加载器**（主应用的类加载器）
2. 父类加载器找不到，才由插件的类加载器加载
3. 这样可以共享 Spring Boot 框架类、JDK 类等

#### spring.factories 的加载机制

Spring Boot 使用 `SpringFactoriesLoader.loadFactoryNames()` 加载 `spring.factories`，该方法会通过 `classLoader.getResources("META-INF/spring.factories")` 搜索所有类路径，**包括父类加载器的类路径**。

**加载范围**：

```
主应用类路径:
  ├── spring-boot-xxx.jar
  │   └── META-INF/spring.factories (Spring Boot 核心配置)
  └── com.gaoding.grpc:common-0.0.12.jar
      └── META-INF/spring.factories (包含 GrpcEnvironmentPostProcessor)

插件类路径:
  └── activity-plugin.jar
      └── META-INF/spring.factories (插件自己的配置)

插件启动时，SpringFactoriesLoader 会加载:
  ✅ 主应用类路径中的所有 spring.factories
  ✅ 插件 JAR 中的 spring.factories
```

#### 为什么这样设计？

1. **共享框架类**：Spring Boot、Spring Framework 的类应该共享，避免重复加载，节省内存
2. **确保框架功能正常**：Spring Boot 的核心机制（如 `EnvironmentPostProcessor`）需要在插件中也能工作
3. **部分隔离，部分共享**：
   - **隔离**：插件的业务类、依赖类（在插件 JAR 中）
   - **共享**：框架类、SPI 机制（`spring.factories`）

#### 插件继承主应用的 EnvironmentPostProcessor

**问题描述**：

如果主应用依赖了某些框架（如 `com.gaoding.grpc:common`），这些依赖的 `spring.factories` 中可能包含 `EnvironmentPostProcessor`（如 `GrpcEnvironmentPostProcessor`）。当插件启动时，这些 `EnvironmentPostProcessor` 会被触发，如果插件的配置文件中缺少必需的配置项，会导致启动失败。

**错误示例**：

```
java.lang.NullPointerException: application.properties中未找到配置: {app.env}
	at com.gaoding.grpc.common.GrpcEnvironmentPostProcessor.postProcessEnvironment(GrpcEnvironmentPostProcessor.java:49)
```

**解决方案**：

在插件的 `application.yml` 中添加必需的配置项：

```yaml
app:
  env: fat  # 添加环境配置，解决 GrpcEnvironmentPostProcessor 报错

spring:
  application:
    name: your-plugin
  # ... 其他配置
```

**说明**：
- 插件会继承主应用的 `EnvironmentPostProcessor`，即使插件代码中没有使用相关功能
- 需要在插件配置文件中添加这些 `EnvironmentPostProcessor` 需要的配置项
- 这是 PF4J 和 SBP 框架的设计特性，不是 Bug
- 目的是让插件能使用 Spring Boot 的完整功能

#### 总结

- ✅ **Spring 容器是隔离的**：Bean 不共享，各自独立的 `ApplicationContext`
- ⚠️ **类加载器是父子关系**：插件类加载器的父类加载器是主应用类加载器
- ❌ **spring.factories 会被共享**：因为类加载器的父子委托机制
- 💡 **这是设计权衡**：为了共享框架类和保证框架功能正常

### 插件日志配置问题

#### Logback 配置共享问题

**重要**：Logback 在 JVM 中是单例的，所有应用共享同一个 `LoggerContext`，因此插件的 `logback.xml` 配置会影响整个应用的日志输出。

**问题描述**：

如果插件的 JAR 中包含 `logback.xml` 或 `logback-spring.xml`，当插件启动时，Logback 可能会重新加载配置，导致：
1. 插件的日志配置覆盖主应用的配置
2. 主应用和其他插件的日志级别被改变
3. 出现意外的日志输出（如 log4jdbc 的 SQL 日志）

**常见现象**：

```
jdbc.audit                               : 2. Statement.close() returned
jdbc.sqltiming                           : SELECT * FROM table_name
```

即使在插件的 `logback.xml` 中设置了 `level="ERROR"`，仍然会输出大量日志。

**原因分析**：

1. **Logback 是 JVM 级别的单例**：所有类加载器共享同一个 `LoggerContext`
2. **插件的 logback.xml 会被加载**：Logback 会扫描类路径中的所有 `logback.xml`
3. **配置可能被覆盖或合并**：后加载的配置可能覆盖先前的配置

**解决方案**：

**方案 1：在主应用的 application.yml 中统一配置日志级别（推荐）**

在 `plugin-manager/src/main/resources/application.yml` 中添加：

```yaml
logging:
  level:
    root: info
    # 禁用 log4jdbc 日志（插件可能包含 log4jdbc）
    jdbc.connection: OFF
    jdbc.resultset: OFF
    jdbc.sqltiming: OFF
    jdbc.audit: OFF
    jdbc.sqlonly: OFF
```

**方案 2：从插件 JAR 中移除 logback.xml**

在插件的 `pom.xml` 中配置 Maven Shade Plugin，排除 `logback.xml`：

```xml
<transformer implementation="org.apache.maven.plugins.shade.resource.DontIncludeResourceTransformer">
    <resource>logback.xml</resource>
</transformer>
<transformer implementation="org.apache.maven.plugins.shade.resource.DontIncludeResourceTransformer">
    <resource>logback-spring.xml</resource>
</transformer>
```

**方案 3：在插件的 logback.xml 中使用 OFF 级别**

如果必须在插件中保留 `logback.xml`，将不需要的日志级别设置为 `OFF`：

```xml
<logger name="jdbc.connection" level="OFF"/>
<logger name="jdbc.resultset" level="OFF"/>
<logger name="jdbc.sqltiming" level="OFF"/>
<logger name="jdbc.audit" level="OFF"/>
<logger name="jdbc.sqlonly" level="OFF" additivity="false"/>
```

**最佳实践**：

1. **主应用统一管理日志配置**：所有日志级别在主应用的 `application.yml` 中配置
2. **插件不包含 logback.xml**：插件使用主应用的日志配置
3. **使用 Spring Boot 的日志配置**：通过 `application.yml` 的 `logging.level.*` 配置日志级别

**注意事项**：

- Logback 配置不像 Spring 容器那样隔离，是全局共享的
- 插件的日志配置会影响整个 JVM 中的所有应用
- 建议在主应用中统一管理所有日志配置，避免插件干扰

