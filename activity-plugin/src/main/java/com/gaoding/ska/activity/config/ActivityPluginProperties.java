package com.gaoding.ska.activity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 活动插件配置属性
 *
 * @author baiye
 * @since 2024/12/30
 */
@Component
@ConfigurationProperties(prefix = "activity.plugin")
public class ActivityPluginProperties {

    /**
     * 插件过期时间
     */
    private String expireTime;

    public String getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(String expireTime) {
        this.expireTime = expireTime;
    }

    /**
     * 检查插件是否过期
     *
     * @return true 如果已过期，false 如果未过期
     */
    public boolean isExpired() {
        if (expireTime == null || expireTime.trim().isEmpty()) {
            // 如果没有配置过期时间，默认不过期
            return false;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date expireDate = sdf.parse(expireTime);
            Date now = new Date();
            return now.after(expireDate);
        } catch (ParseException e) {
            // 如果解析失败，默认不过期
            return false;
        }
    }

    /**
     * 获取过期时间字符串
     *
     * @return 过期时间字符串
     */
    public String getExpireTimeString() {
        return expireTime;
    }
}

