package com.gaoding.ska.activity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * 活动插件拦截器
 * 在 Controller 执行前后进行拦截处理
 * 
 * 工作原理：
 * 1. preHandle：在 Controller 方法执行之前调用
 *    - 检查插件是否过期
 *    - 检查用户权限（示例）
 *    - 如果检查失败，返回 false，阻止 Controller 执行
 * 2. postHandle：在 Controller 方法执行之后，视图渲染之前调用
 *    - 可以修改 ModelAndView
 *    - 添加额外的响应信息
 * 3. afterCompletion：在请求完成后调用（视图渲染完成后或发生异常时）
 *    - 清理资源
 *    - 记录日志
 * 
 * 注意：
 * - 拦截器在 Spring MVC 层面工作，只能拦截 Controller 请求
 * - 拦截器可以访问 Controller 信息（Handler、ModelAndView 等）
 * - 拦截器在 Filter 之后执行
 * - 可以注入插件中的其他 Bean
 *
 * @author baiye
 * @since 2025/01/09
 */
@Component
@Order(1)
public class ActivityPluginInterceptor implements HandlerInterceptor {

    @Autowired
    private ActivityPluginProperties pluginProperties;

    /**
     * 在 Controller 方法执行之前调用
     * 
     * @return 如果返回 true，继续执行 Controller；如果返回 false，停止执行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) throws Exception {
        
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        
        System.out.println("ActivityPluginInterceptor.preHandle - 拦截请求: " + method + " " + requestURI);
        System.out.println("ActivityPluginInterceptor.preHandle - Handler: " + handler.getClass().getName());
        
        // 只拦截活动相关的接口
        if (requestURI != null && requestURI.startsWith("/activities")) {
            System.out.println("ActivityPluginInterceptor.preHandle - 匹配到活动接口，检查过期状态");
            System.out.println("ActivityPluginInterceptor.preHandle - pluginProperties: " + pluginProperties);
            System.out.println("ActivityPluginInterceptor.preHandle - isExpired: " + (pluginProperties != null ? pluginProperties.isExpired() : "pluginProperties is null"));
            
        // 检查插件是否过期
            if (pluginProperties != null && pluginProperties.isExpired()) {
                System.out.println("ActivityPluginInterceptor.preHandle - 插件已过期，拒绝请求");
                
            // 插件已过期，返回错误响应
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            
            Map<String, Object> result = new HashMap<>();
            result.put("code", HttpStatus.FORBIDDEN.value());
                result.put("message", "插件已过期（拦截器检查），无法使用。过期时间：" + pluginProperties.getExpireTimeString());
            result.put("data", null);
                result.put("interceptor", "ActivityPluginInterceptor");
            
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(result);
            response.getWriter().write(json);
            
                return false;  // 返回 false，停止执行 Controller
            }
            
            // 可以在这里添加其他检查，如用户权限检查
            // if (!hasPermission(request)) {
            //     response.setStatus(HttpStatus.FORBIDDEN.value());
            //     // ... 返回错误响应
            //     return false;
            // }
        }
        
        System.out.println("ActivityPluginInterceptor.preHandle - 检查通过，继续执行 Controller");
        return true;  // 返回 true，继续执行 Controller
    }
    
    /**
     * 在 Controller 方法执行之后，视图渲染之前调用
     * 可以修改 ModelAndView，添加额外的响应信息
     */
    @Override
    public void postHandle(HttpServletRequest request, 
                         HttpServletResponse response, 
                         Object handler, 
                         ModelAndView modelAndView) throws Exception {
        
        String requestURI = request.getRequestURI();
        
        System.out.println("ActivityPluginInterceptor.postHandle - 请求: " + request.getMethod() + " " + requestURI);
        System.out.println("ActivityPluginInterceptor.postHandle - ModelAndView: " + (modelAndView != null ? modelAndView.getViewName() : "null"));
        
        // 只处理活动相关的接口
        if (requestURI != null && requestURI.startsWith("/activities")) {
            // 可以在这里修改 ModelAndView，添加额外信息
            if (modelAndView != null) {
                modelAndView.addObject("pluginVersion", "1.0.0");
                modelAndView.addObject("pluginName", "activity-plugin");
                System.out.println("ActivityPluginInterceptor.postHandle - 添加插件信息到 ModelAndView");
            }
            
            // 注意：对于 RESTful API（返回 JSON），ModelAndView 通常为 null
            // 如果需要修改 JSON 响应，建议使用 ResponseBodyAdvice
        }
        
        System.out.println("ActivityPluginInterceptor.postHandle - 执行完毕");
    }
    
    /**
     * 在请求完成后调用（视图渲染完成后或发生异常时）
     * 用于清理资源、记录日志等
     * 
     * 注意：即使 preHandle 返回 false，afterCompletion 也会被调用
     */
    @Override
    public void afterCompletion(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler, 
                               Exception ex) throws Exception {
        
        String requestURI = request.getRequestURI();
        
        System.out.println("ActivityPluginInterceptor.afterCompletion - 请求: " + request.getMethod() + " " + requestURI);
        System.out.println("ActivityPluginInterceptor.afterCompletion - 响应状态: " + response.getStatus());
        System.out.println("ActivityPluginInterceptor.afterCompletion - Exception: " + (ex != null ? ex.getMessage() : "null"));
        
        // 只处理活动相关的接口
        if (requestURI != null && requestURI.startsWith("/api/activities")) {
            // 记录请求日志
            long duration = System.currentTimeMillis();  // 实际应该在 preHandle 中记录开始时间
            System.out.println("ActivityPluginInterceptor.afterCompletion - 请求处理完成");
            
            // 清理资源（如果有）
            // ...
        }
        
        System.out.println("ActivityPluginInterceptor.afterCompletion - 执行完毕");
    }
}
