# 在插件中重置 MyBatis 静态 ResourcePatternResolver

## 问题描述

MyBatis 或 MyBatis Plus 中存在静态的 `ResourcePatternResolver`：

```java
private static final ResourcePatternResolver RESOURCE_PATTERN_RESOLVER = 
    new PathMatchingResourcePatternResolver();
```

这个静态变量：
- 在类第一次加载时初始化（通常是主应用加载时）
- 使用主应用的 ClassLoader
- 整个 JVM 生命周期内只有一个实例
- 无法感知插件的 ClassLoader

导致插件在扫描资源时使用了错误的 ClassLoader。

## 解决方案

### 方案1：通过反射重置静态变量（推荐）

在插件的配置类中，使用反射修改静态变量：

```java
package com.gaoding.ska.statistics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * MyBatis 插件环境修复配置
 * 
 * 解决静态 ResourcePatternResolver 使用错误 ClassLoader 的问题
 */
@Configuration
public class MybatisPluginEnvironmentFix {
    
    @PostConstruct
    public void fixStaticResourcePatternResolver() {
        try {
            // 获取插件的 ClassLoader
            ClassLoader pluginClassLoader = this.getClass().getClassLoader();
            
            System.out.println("=== 修复 MyBatis 静态 ResourcePatternResolver ===");
            System.out.println("插件 ClassLoader: " + pluginClassLoader.getClass().getName());
            
            // 创建使用插件 ClassLoader 的 ResourcePatternResolver
            ResourcePatternResolver pluginResolver = 
                new PathMatchingResourcePatternResolver(pluginClassLoader);
            
            // 需要知道具体是哪个类的静态变量
            // 常见的位置：
            // 1. MyBatis Plus: com.baomidou.mybatisplus.core.MybatisConfiguration
            // 2. MyBatis: org.apache.ibatis.session.Configuration
            
            // 示例：修改 MyBatis Plus 的静态变量
            fixMybatisPlusResourceResolver(pluginResolver);
            
            System.out.println("✓ 成功修复 MyBatis 静态 ResourcePatternResolver");
            
        } catch (Exception e) {
            System.err.println("✗ 修复 MyBatis 静态 ResourcePatternResolver 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 修改类的静态 final 字段
     */
    private void setStaticFinalField(Class<?> clazz, String fieldName, Object newValue) 
            throws Exception {
        
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        
        // 移除 final 修饰符
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        
        // 设置新值
        field.set(null, newValue);
    }
    
    /**
     * 修复 MyBatis Plus 的 ResourcePatternResolver
     */
    private void fixMybatisPlusResourceResolver(ResourcePatternResolver resolver) 
            throws Exception {
        
        try {
            // 尝试加载 MyBatis Plus 的配置类
            Class<?> mybatisConfigClass = Class.forName(
                "com.baomidou.mybatisplus.core.MybatisConfiguration"
            );
            
            // 查找静态字段（需要根据实际源码确定字段名）
            // 这里假设字段名是 RESOURCE_PATTERN_RESOLVER
            setStaticFinalField(mybatisConfigClass, "RESOURCE_PATTERN_RESOLVER", resolver);
            
            System.out.println("✓ 修复 MyBatis Plus ResourcePatternResolver");
            
        } catch (ClassNotFoundException e) {
            System.out.println("! 未找到 MyBatis Plus 配置类，跳过");
        }
    }
}
```

### 方案2：在插件启动事件中修复

如果方案1不够早，可以在插件启动事件中处理：

```java
package com.gaoding.ska.statistics.plugin;

import org.laxture.sbp.SpringBootPlugin;
import org.laxture.sbp.spring.boot.SpringBootstrap;
import org.pf4j.PluginWrapper;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class StatGatewayPlugin extends SpringBootPlugin {
    
    public StatGatewayPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }
    
    @Override
    public void start() {
        // 在启动 Spring Boot 之前修复静态变量
        fixMyBatisStaticFields();
        
        // 启动插件
        super.start();
    }
    
    private void fixMyBatisStaticFields() {
        try {
            ClassLoader pluginClassLoader = this.getClass().getClassLoader();
            
            System.out.println("=== [插件启动] 修复 MyBatis 静态变量 ===");
            
            // 创建使用插件 ClassLoader 的 ResourcePatternResolver
            PathMatchingResourcePatternResolver resolver = 
                new PathMatchingResourcePatternResolver(pluginClassLoader);
            
            // 修复 MyBatis Plus
            fixStaticField(
                "com.baomidou.mybatisplus.core.MybatisConfiguration",
                "RESOURCE_PATTERN_RESOLVER",
                resolver
            );
            
            // 如果还有其他需要修复的静态字段，在这里添加
            
            System.out.println("✓ MyBatis 静态变量修复完成");
            
        } catch (Exception e) {
            System.err.println("✗ 修复失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void fixStaticField(String className, String fieldName, Object newValue) {
        try {
            Class<?> clazz = Class.forName(className);
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            
            // 移除 final 修饰符
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            
            // 设置新值
            field.set(null, newValue);
            
            System.out.println("✓ 修复 " + className + "." + fieldName);
            
        } catch (Exception e) {
            System.out.println("! 跳过 " + className + "." + fieldName + ": " + e.getMessage());
        }
    }
}
```

### 方案3：自定义 SqlSessionFactory（最可靠）

如果反射方案不稳定，可以自定义 `SqlSessionFactory`：

```java
package com.gaoding.ska.statistics.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

@Configuration
public class CustomSqlSessionFactoryConfiguration {
    
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        
        // 获取插件的 ClassLoader
        ClassLoader pluginClassLoader = this.getClass().getClassLoader();
        
        // 创建使用插件 ClassLoader 的 ResourcePatternResolver
        PathMatchingResourcePatternResolver resolver = 
            new PathMatchingResourcePatternResolver(pluginClassLoader);
        
        // 创建 SqlSessionFactoryBean
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        
        // 使用插件 ClassLoader 扫描 Mapper XML
        factory.setMapperLocations(
            resolver.getResources("classpath*:com/gaoding/ska/statistics/mapper/*.xml")
        );
        
        // 配置类型别名包
        factory.setTypeAliasesPackage(
            "com.gaoding.ska.statistics.dto,com.gaoding.ska.statistics.entity"
        );
        
        // 重要：确保使用插件的 ClassLoader
        org.apache.ibatis.session.Configuration configuration = 
            new org.apache.ibatis.session.Configuration();
        configuration.setDefaultScriptingLanguage(
            org.apache.ibatis.scripting.xmltags.XMLLanguageDriver.class
        );
        
        factory.setConfiguration(configuration);
        
        return factory.getObject();
    }
}
```

## 查找需要修复的静态字段

### 步骤1：反编译查找静态字段

```bash
# 查找 MyBatis Plus JAR 中的静态 ResourcePatternResolver
cd ~/.m2/repository/com/baomidou/mybatis-plus-core/3.4.1/
jar -xf mybatis-plus-core-3.4.1.jar
grep -r "RESOURCE_PATTERN_RESOLVER" .

# 或者使用 javap
javap -c -p com.baomidou.mybatisplus.core.MybatisConfiguration | grep -A 10 "RESOURCE"
```

### 步骤2：运行时诊断

```java
@Configuration
public class DiagnosticConfiguration {
    
    @PostConstruct
    public void diagnose() {
        System.out.println("=== 诊断 MyBatis 静态字段 ===");
        
        // 检查可能包含静态 ResourcePatternResolver 的类
        String[] classesToCheck = {
            "com.baomidou.mybatisplus.core.MybatisConfiguration",
            "com.baomidou.mybatisplus.core.MybatisMapperAnnotationBuilder",
            "org.apache.ibatis.session.Configuration",
            "org.apache.ibatis.builder.xml.XMLMapperBuilder"
        };
        
        for (String className : classesToCheck) {
            try {
                Class<?> clazz = Class.forName(className);
                Field[] fields = clazz.getDeclaredFields();
                
                for (Field field : fields) {
                    if (Modifier.isStatic(field.getModifiers()) &&
                        field.getType().getName().contains("ResourcePatternResolver")) {
                        
                        field.setAccessible(true);
                        Object value = field.get(null);
                        
                        System.out.println("找到静态字段: " + className + "." + field.getName());
                        System.out.println("  类型: " + field.getType().getName());
                        System.out.println("  值: " + value);
                        
                        if (value instanceof PathMatchingResourcePatternResolver) {
                            PathMatchingResourcePatternResolver resolver = 
                                (PathMatchingResourcePatternResolver) value;
                            System.out.println("  ClassLoader: " + 
                                resolver.getClassLoader().getClass().getName());
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略
            }
        }
    }
}
```

## 注意事项

### 1. Java 9+ 模块系统

在 Java 9+ 中，反射访问可能受限，需要添加 JVM 参数：

```bash
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
```

### 2. 安全性

修改静态 final 字段是一种黑科技，需要注意：
- 可能影响其他插件
- 需要在正确的时机执行
- 需要充分测试

### 3. 版本兼容性

不同版本的 MyBatis/MyBatis Plus 可能字段名不同，需要根据实际情况调整。

## 测试验证

```java
@Configuration
public class ValidationConfiguration {
    
    @PostConstruct
    public void validate() throws Exception {
        // 验证修复是否生效
        ClassLoader pluginCL = this.getClass().getClassLoader();
        
        // 尝试扫描资源
        PathMatchingResourcePatternResolver resolver = 
            new PathMatchingResourcePatternResolver(pluginCL);
        
        Resource[] resources = resolver.getResources(
            "classpath*:com/gaoding/ska/statistics/dto/**/*.class"
        );
        
        System.out.println("=== 验证资源扫描 ===");
        System.out.println("找到 " + resources.length + " 个类文件");
        
        if (resources.length > 0) {
            System.out.println("✓ ResourcePatternResolver 工作正常");
        } else {
            System.out.println("✗ ResourcePatternResolver 可能有问题");
        }
    }
}
```

## 参考资料

- [Java Reflection API](https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/package-summary.html)
- [Spring ResourcePatternResolver](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/support/ResourcePatternResolver.html)
- [Modifying final fields via reflection](https://stackoverflow.com/questions/3301635/change-private-static-final-field-using-java-reflection)

