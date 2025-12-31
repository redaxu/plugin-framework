package com.gaoding.ska.customize.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 活动插件配置类
 * 
 * 作用：
 * 1. 扫描插件中的组件（Controller、Service、Repository等）
 * 2. 作为SpringBootstrap的配置类参数
 *
 * @author baiye
 * @since 2024/12/19
 */
@Configuration
@ComponentScan(basePackages = "com.gaoding.ska.customize")
public class ActivityPluginConfiguration {

}

