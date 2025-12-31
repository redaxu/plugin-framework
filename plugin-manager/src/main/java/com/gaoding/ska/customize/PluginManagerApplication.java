package com.gaoding.ska.customize;

import org.laxture.spring.util.ApplicationContextProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;

/**
 * 插件管理主应用
 *
 * @author baiye
 * @since 2024/12/19
 */
@SpringBootApplication(exclude = {FlywayAutoConfiguration.class})
public class PluginManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PluginManagerApplication.class, args);
    }

    @Bean
    public ApplicationContextAware multiApplicationContextProviderRegister() {
        return ApplicationContextProvider::registerApplicationContext;
    }

}

