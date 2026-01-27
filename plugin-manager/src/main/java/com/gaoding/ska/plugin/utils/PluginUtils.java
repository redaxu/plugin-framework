package com.gaoding.ska.plugin.utils;

import org.pf4j.PluginWrapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PluginUtils {

    private static final Map<ClassLoader, PluginWrapper> pluginCache = Collections.synchronizedMap(new HashMap<>());

    public static PluginWrapper getPluginWrapper(ClassLoader classLoader) {
        return pluginCache.get(classLoader);
    }

    public static void putPluginWrapper(ClassLoader classLoader, PluginWrapper pluginWrapper) {
        pluginCache.put(classLoader, pluginWrapper);
    }
}
