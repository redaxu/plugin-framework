package com.gaoding.ska.activity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;

/**
 * Activity插件SpringBoot启动类
 * 
 * 用于独立运行Activity插件，支持打包成可执行的SpringBoot jar包
 *
 * @author baiye
 * @since 2025/01/02
 */
@SpringBootApplication(exclude = {FlywayAutoConfiguration.class})
public class ActivityPluginApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivityPluginApplication.class, args);
    }
}

