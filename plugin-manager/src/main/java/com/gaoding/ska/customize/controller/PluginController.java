package com.gaoding.ska.customize.controller;

import org.laxture.sbp.SpringBootPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 插件管理控制器
 *
 * @author baiye
 * @since 2024/12/19
 */
//@RestController
//@RequestMapping("/api/plugins")
public class PluginController {

    @Resource
    private PluginManager pluginManager;

    @GetMapping
    public List<PluginInfo> listPlugins() {
        return pluginManager.getPlugins().stream()
                .map(this::toPluginInfo)
                .collect(Collectors.toList());
    }

    @GetMapping("/{pluginId}")
    public PluginInfo getPlugin(@PathVariable String pluginId) {
        PluginWrapper pluginWrapper = pluginManager.getPlugin(pluginId);
        if (pluginWrapper == null) {
            return null;
        }
        return toPluginInfo(pluginWrapper);
    }

    @PostMapping("/{pluginId}/start")
    public String startPlugin(@PathVariable String pluginId) {
        PluginWrapper pluginWrapper = pluginManager.getPlugin(pluginId);
        if (pluginWrapper == null) {
            return "Plugin not found: " + pluginId;
        }
        if (pluginWrapper.getPluginState().toString().equals("STARTED")) {
            return "Plugin already started: " + pluginId;
        }
        pluginManager.startPlugin(pluginId);
        return "Plugin started: " + pluginId;
    }

    @PostMapping("/{pluginId}/stop")
    public String stopPlugin(@PathVariable String pluginId) {
        PluginWrapper pluginWrapper = pluginManager.getPlugin(pluginId);
        if (pluginWrapper == null) {
            return "Plugin not found: " + pluginId;
        }
        if (pluginWrapper.getPluginState().toString().equals("STOPPED")) {
            return "Plugin already stopped: " + pluginId;
        }
        pluginManager.stopPlugin(pluginId);
        return "Plugin stopped: " + pluginId;
    }

    private PluginInfo toPluginInfo(PluginWrapper pluginWrapper) {
        PluginInfo info = new PluginInfo();
        info.setPluginId(pluginWrapper.getPluginId());
        info.setPluginVersion(pluginWrapper.getDescriptor().getVersion());
        info.setPluginState(pluginWrapper.getPluginState().toString());
        info.setPluginDescription(pluginWrapper.getDescriptor().getPluginDescription());
        return info;
    }

    public static class PluginInfo {
        private String pluginId;
        private String pluginVersion;
        private String pluginState;
        private String pluginDescription;

        public String getPluginId() {
            return pluginId;
        }

        public void setPluginId(String pluginId) {
            this.pluginId = pluginId;
        }

        public String getPluginVersion() {
            return pluginVersion;
        }

        public void setPluginVersion(String pluginVersion) {
            this.pluginVersion = pluginVersion;
        }

        public String getPluginState() {
            return pluginState;
        }

        public void setPluginState(String pluginState) {
            this.pluginState = pluginState;
        }

        public String getPluginDescription() {
            return pluginDescription;
        }

        public void setPluginDescription(String pluginDescription) {
            this.pluginDescription = pluginDescription;
        }
    }

}

