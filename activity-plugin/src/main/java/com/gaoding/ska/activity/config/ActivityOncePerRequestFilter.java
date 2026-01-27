package com.gaoding.ska.activity.config;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Activity 插件的 OncePerRequestFilter 过滤器
 * 用于验证插件的 OncePerRequestFilter 是否能被正确注册和执行
 * 
 * OncePerRequestFilter 特点：
 * - 确保在一次请求中只执行一次
 * - 即使请求被转发（forward）或包含（include），也只执行一次
 *
 * @author baiye
 * @since 2026/01/12
 */
@Component
public class ActivityOncePerRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                     FilterChain filterChain) throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        
        System.out.println("=================================================");
        System.out.println("ActivityOncePerRequestFilter - 开始执行");
        System.out.println("ActivityOncePerRequestFilter - 请求方法: " + method);
        System.out.println("ActivityOncePerRequestFilter - 请求URI: " + requestURI);
        System.out.println("ActivityOncePerRequestFilter - 请求参数: " + request.getQueryString());
        System.out.println("ActivityOncePerRequestFilter - 远程地址: " + request.getRemoteAddr());
        
        // 记录请求开始时间
        long startTime = System.currentTimeMillis();
        
        try {
            // 继续执行过滤器链
            filterChain.doFilter(request, response);
        } finally {
            // 记录请求结束时间
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            System.out.println("ActivityOncePerRequestFilter - 请求完成");
            System.out.println("ActivityOncePerRequestFilter - 响应状态: " + response.getStatus());
            System.out.println("ActivityOncePerRequestFilter - 处理耗时: " + duration + "ms");
            System.out.println("=================================================");
        }
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // 可以在这里配置哪些请求不需要过滤
        // 例如：静态资源、健康检查等
        String path = request.getRequestURI();
        
        // 排除静态资源
        if (path.startsWith("/static/") || 
            path.startsWith("/css/") || 
            path.startsWith("/js/") || 
            path.startsWith("/images/")) {
            System.out.println("ActivityOncePerRequestFilter - 跳过静态资源: " + path);
            return true;
        }
        
        // 排除健康检查
        if (path.equals("/actuator/health") || path.equals("/health")) {
            System.out.println("ActivityOncePerRequestFilter - 跳过健康检查: " + path);
            return true;
        }
        
        return false;
    }
}

