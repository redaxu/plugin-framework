package com.gaoding.ska.activity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 活动插件过滤器
 * 检查插件是否过期，如果过期则拒绝所有请求
 *
 * @author baiye
 * @since 2024/12/30
 */
@Component
@Order(1)
public class ActivityPluginFilter extends OncePerRequestFilter {

    @Autowired
    private ActivityPluginProperties pluginProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String requestURI = request.getRequestURI();
        // 只过滤活动相关的接口
        boolean shouldFilter = requestURI != null && requestURI.startsWith("/api/activities");
        System.out.println("ActivityPluginFilter.shouldNotFilter - URI: " + requestURI + ", shouldFilter: " + shouldFilter);
        return !shouldFilter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        
        System.out.println("ActivityPluginFilter.doFilterInternal - 过滤器执行, URI: " + requestURI + ", Method: " + method);
        
        // 只拦截活动相关的接口
        if (requestURI != null && requestURI.startsWith("/api/activities")) {
            System.out.println("ActivityPluginFilter - 匹配到活动接口，检查过期状态");
            System.out.println("ActivityPluginFilter - pluginProperties: " + pluginProperties);
            System.out.println("ActivityPluginFilter - isExpired: " + (pluginProperties != null ? pluginProperties.isExpired() : "pluginProperties is null"));
            
            // 检查插件是否过期
            if (pluginProperties != null && pluginProperties.isExpired()) {
                System.out.println("ActivityPluginFilter - 插件已过期，拒绝请求");
                // 插件已过期，返回错误响应
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                
                Map<String, Object> result = new HashMap<>();
                result.put("code", HttpStatus.FORBIDDEN.value());
                result.put("message", "插件已过期，无法使用。过期时间：" + pluginProperties.getExpireTimeString());
                result.put("data", null);
                
                ObjectMapper objectMapper = new ObjectMapper();
                String json = objectMapper.writeValueAsString(result);
                response.getWriter().write(json);
                
                return;
            }
        }
        
        // 继续执行过滤器链
        filterChain.doFilter(request, response);
    }
}

