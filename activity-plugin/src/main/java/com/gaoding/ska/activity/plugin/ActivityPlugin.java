package com.gaoding.ska.activity.plugin;

import com.gaoding.ska.activity.config.ActivityPluginConfiguration;
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
        // 在创建 SpringBootstrap 之前设置插件的 ClassLoader
        // 确保 Spring 上下文创建时使用插件的 ClassLoader
        ClassLoader pluginClassLoader = wrapper.getPluginClassLoader();
        if (pluginClassLoader != null) {
            Thread.currentThread().setContextClassLoader(pluginClassLoader);
        }
        return new SpringBootstrap(this, ActivityPluginConfiguration.class);
    }

    @Override
    public void start() {
        System.out.println("ActivityPlugin starting");
        // 设置插件的 ClassLoader 为当前线程的上下文 ClassLoader
        // 确保 ClassUtils.getDefaultClassLoader() 返回插件的 ClassLoader 而不是主应用的 ClassLoader
        ClassLoader pluginClassLoader = wrapper.getPluginClassLoader();
        if (pluginClassLoader != null) {
            Thread.currentThread().setContextClassLoader(pluginClassLoader);
        }
        super.start();
    }

    @Override
    public void stop() {
        System.out.println("ActivityPlugin stopped");
        super.stop();
    }

}

