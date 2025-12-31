package com.gaoding.ska.customize.plugin;

import com.gaoding.ska.customize.config.ActivityPluginConfiguration;
import org.laxture.sbp.SpringBootPlugin;
import org.laxture.sbp.spring.boot.SpringBootstrap;
import org.pf4j.PluginWrapper;

/**
 * 活动管理插件
 *
 * @author baiye
 * @since 2024/12/19
 */
public class ActivityPlugin extends SpringBootPlugin {

    public ActivityPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected SpringBootstrap createSpringBootstrap() {
        return new SpringBootstrap(this, ActivityPluginConfiguration.class);
    }

    @Override
    public void start() {
        // 设置插件的类加载器为线程上下文类加载器
        // 这样Spring Boot自动配置类在加载依赖类时可以使用插件的类加载器
        // 注意：不要在这里恢复类加载器，让整个插件生命周期都使用插件的类加载器
        Thread.currentThread().setContextClassLoader(getWrapper().getPluginClassLoader());
        System.out.println("ActivityPlugin started with classloader: " + getWrapper().getPluginClassLoader().getClass().getName());
        super.start();
    }

    @Override
    public void stop() {
        System.out.println("ActivityPlugin stopped");
        super.stop();
    }

}

