package com.gaoding.ska.customize.config;

import org.laxture.sbp.SpringBootPluginManager;
import org.laxture.sbp.internal.SpringBootPluginClassLoader;
import org.laxture.sbp.spring.boot.PropertyPluginStatusProvider;
import org.laxture.sbp.spring.boot.SbpPluginProperties;
import org.laxture.sbp.spring.boot.SbpProperties;
import org.laxture.spring.util.ApplicationContextProvider;
import org.pf4j.CompoundPluginLoader;
import org.pf4j.DefaultPluginLoader;
import org.pf4j.JarPluginLoader;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginLoader;
import org.pf4j.PluginManager;
import org.pf4j.PluginStatusProvider;
import org.pf4j.RuntimeMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Path;

/**
 * SBP配置类
 * 手动创建 SpringBootPluginManager Bean（因为自动配置可能被条件排除）
 *
 * @author baiye
 * @since 2024/12/19
 */
@Configuration
@EnableConfigurationProperties({SbpProperties.class, SbpPluginProperties.class})
public class Pf4jConfiguration {

    private SpringBootPluginManager pluginManager;

//    @Bean
//    @ConditionalOnMissingBean(SpringBootPluginManager.class)
//    public SpringBootPluginManager pluginManager(SbpProperties properties) {
//        // Setup RuntimeMode
//        System.setProperty("pf4j.mode", properties.getRuntimeMode().toString());
//
//        // Setup Plugin folder
//        String pluginsRoot = StringUtils.hasText(properties.getPluginsRoot()) ? properties.getPluginsRoot() : "plugins";
//        System.setProperty("pf4j.pluginsDir", pluginsRoot);
//        String appHome = System.getProperty("app.home");
//        if (RuntimeMode.DEPLOYMENT == properties.getRuntimeMode()
//                && StringUtils.hasText(appHome)) {
//            System.setProperty("pf4j.pluginsDir", appHome + File.separator + pluginsRoot);
//        }
//
//        SpringBootPluginManager pluginManager = new SpringBootPluginManager(
//                new File(pluginsRoot).toPath()) {
//            @Override
//            protected PluginLoader createPluginLoader() {
//                if (properties.getCustomPluginLoader() != null) {
//                    Class<PluginLoader> clazz = properties.getCustomPluginLoader();
//                    try {
//                        Constructor<?> constructor = clazz.getConstructor(PluginManager.class);
//                        return (PluginLoader) constructor.newInstance(this);
//                    } catch (Exception ex) {
//                        throw new IllegalArgumentException(String.format("Create custom PluginLoader %s failed. Make sure" +
//                                "there is a constructor with one argument that accepts PluginLoader", clazz.getName()));
//                    }
//                } else {
//                    return new CompoundPluginLoader()
//                            .add(new DefaultPluginLoader(this) {
//                                @Override
//                                protected PluginClassLoader createPluginClassLoader(Path pluginPath,
//                                                                                    PluginDescriptor pluginDescriptor) {
//                                    if (properties.getClassesDirectories() != null && properties.getClassesDirectories().size() > 0) {
//                                        for (String classesDirectory : properties.getClassesDirectories()) {
//                                            pluginClasspath.addClassesDirectories(classesDirectory);
//                                        }
//                                    }
//                                    if (properties.getLibDirectories() != null && properties.getLibDirectories().size() > 0) {
//                                        for (String libDirectory : properties.getLibDirectories()) {
//                                            pluginClasspath.addJarsDirectories(libDirectory);
//                                        }
//                                    }
//                                    return new SpringBootPluginClassLoader(pluginManager,
//                                            pluginDescriptor, getClass().getClassLoader());
//                                }
//                            }, this::isDevelopment)
//                            .add(new JarPluginLoader(this) {
//                                @Override
//                                public ClassLoader loadPlugin(Path pluginPath, PluginDescriptor pluginDescriptor) {
//                                    PluginClassLoader pluginClassLoader = new SpringBootPluginClassLoader(pluginManager, pluginDescriptor, getClass().getClassLoader());
//                                    pluginClassLoader.addFile(pluginPath.toFile());
//                                    return pluginClassLoader;
//                                }
//                            }, this::isNotDevelopment);
//                }
//            }
//
//            @Override
//            protected PluginStatusProvider createPluginStatusProvider() {
//                if (PropertyPluginStatusProvider.isPropertySet(properties)) {
//                    return new PropertyPluginStatusProvider(properties);
//                }
//                return super.createPluginStatusProvider();
//            }
//        };
//
//        pluginManager.setAutoStartPlugin(properties.isAutoStartPlugin());
//        pluginManager.setProfiles(properties.getPluginProfiles());
//        pluginManager.presetProperties(flatProperties(properties.getPluginProperties()));
//        pluginManager.setExactVersionAllowed(properties.isExactVersionAllowed());
//        pluginManager.setSystemVersion(properties.getSystemVersion());
//
//        return pluginManager;
//    }

    @PreDestroy
    public void cleanup() {
        if (pluginManager != null) {
            pluginManager.stopPlugins();
            pluginManager.unloadPlugins();
        }
    }

}

