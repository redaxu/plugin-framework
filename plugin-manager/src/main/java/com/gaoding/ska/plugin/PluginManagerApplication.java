package com.gaoding.ska.plugin;

import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration;
import com.gaoding.framework.web.swagger.SwaggerConfig;
import org.laxture.spring.util.ApplicationContextProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;

/**
 * 插件管理主应用
 *
 * @author baiye
 * @since 2024/12/19
 */
@SpringBootApplication(exclude = {FlywayAutoConfiguration.class, DynamicDataSourceAutoConfiguration.class, SwaggerConfig.class})
public class PluginManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PluginManagerApplication.class, args);
    }

    @Bean
    public ApplicationContextAware multiApplicationContextProviderRegister() {
        return ApplicationContextProvider::registerApplicationContext;
    }

}

