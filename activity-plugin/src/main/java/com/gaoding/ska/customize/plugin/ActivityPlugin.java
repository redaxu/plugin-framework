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
        System.out.println("ActivityPlugin starting");
        super.start();
    }

    @Override
    public void stop() {
        System.out.println("ActivityPlugin stopped");
        super.stop();
    }

}

