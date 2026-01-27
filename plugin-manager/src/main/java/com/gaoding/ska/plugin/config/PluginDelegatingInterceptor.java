package com.gaoding.ska.plugin.config;

import com.gaoding.ska.plugin.sdk.PluginInterceptorManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 插件代理拦截器
 * 在主应用启动时注册，动态查找并执行插件中的 HandlerInterceptor
 * 
 * 工作原理：
 * 1. 主应用启动时，此拦截器被注册到 Spring MVC
 * 2. HTTP 请求到达 Controller 之前，此拦截器被调用
 * 3. 从 PluginInterceptorManager 获取所有插件拦截器
 * 4. 依次执行所有插件拦截器的 preHandle 方法
 * 5. Controller 执行后，依次执行所有插件拦截器的 postHandle 方法
 * 6. 请求完成后，依次执行所有插件拦截器的 afterCompletion 方法
 * 
 * 优点：
 * - 支持动态添加和删除插件拦截器
 * - 不需要在应用启动后注册拦截器
 * - 插件拦截器由插件容器管理，可以注入插件中的 Bean
 * - 可以访问 Controller 信息（Handler、ModelAndView 等）
 * 
 * 注意：
 * - 使用 @OnMainApplication 注解，确保只在主应用中生效
 * - 避免插件 Spring 上下文扫描到此拦截器，导致创建多个实例
 *
 * @author baiye
 * @since 2025/01/09
 */
@Component
public class PluginDelegatingInterceptor implements HandlerInterceptor {
    
    @Autowired
    private PluginInterceptorManager pluginInterceptorManager;
    
    /**
     * 在 Controller 方法执行之前调用
     * 依次执行所有插件拦截器的 preHandle 方法
     * 
     * @return 如果所有插件拦截器都返回 true，则返回 true；否则返回 false
     */
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) throws Exception {
        
        // 获取所有插件拦截器
        List<HandlerInterceptor> pluginInterceptors = pluginInterceptorManager.getAllInterceptors();
        
        if (pluginInterceptors.isEmpty()) {
            // 没有插件拦截器，直接返回 true
            return true;
        }
        
        // 打印调试信息
        System.out.println("PluginDelegatingInterceptor.preHandle - 拦截请求: " + request.getMethod() + " " + request.getRequestURI());
        System.out.println("PluginDelegatingInterceptor.preHandle - 当前插件拦截器数量: " + pluginInterceptors.size());
        System.out.println("PluginDelegatingInterceptor.preHandle - Handler: " + handler.getClass().getName());
        
        // 依次执行所有插件拦截器的 preHandle 方法
        for (HandlerInterceptor interceptor : pluginInterceptors) {
            System.out.println("PluginDelegatingInterceptor.preHandle - 执行插件拦截器: " + interceptor.getClass().getName());
            
            boolean result = interceptor.preHandle(request, response, handler);
            
            if (!result) {
                // 如果某个拦截器返回 false，停止执行后续拦截器，并返回 false
                System.out.println("PluginDelegatingInterceptor.preHandle - 拦截器 [" + interceptor.getClass().getName() + "] 返回 false，停止执行");
                return false;
            }
        }
        
        System.out.println("PluginDelegatingInterceptor.preHandle - 所有插件拦截器执行完毕，返回 true");
        return true;
    }
    
    /**
     * 在 Controller 方法执行之后，视图渲染之前调用
     * 依次执行所有插件拦截器的 postHandle 方法
     */
    @Override
    public void postHandle(HttpServletRequest request, 
                          HttpServletResponse response, 
                          Object handler, 
                          ModelAndView modelAndView) throws Exception {
        
        // 获取所有插件拦截器
        List<HandlerInterceptor> pluginInterceptors = pluginInterceptorManager.getAllInterceptors();
        
        if (pluginInterceptors.isEmpty()) {
            return;
        }
        
        // 打印调试信息
        System.out.println("PluginDelegatingInterceptor.postHandle - 请求: " + request.getMethod() + " " + request.getRequestURI());
        System.out.println("PluginDelegatingInterceptor.postHandle - 当前插件拦截器数量: " + pluginInterceptors.size());
        System.out.println("PluginDelegatingInterceptor.postHandle - ModelAndView: " + (modelAndView != null ? modelAndView.getViewName() : "null"));
        
        // 依次执行所有插件拦截器的 postHandle 方法
        for (HandlerInterceptor interceptor : pluginInterceptors) {
            System.out.println("PluginDelegatingInterceptor.postHandle - 执行插件拦截器: " + interceptor.getClass().getName());
            interceptor.postHandle(request, response, handler, modelAndView);
        }
        
        System.out.println("PluginDelegatingInterceptor.postHandle - 所有插件拦截器执行完毕");
    }
    
    /**
     * 在请求完成后调用（视图渲染完成后或发生异常时）
     * 依次执行所有插件拦截器的 afterCompletion 方法
     * 
     * 注意：即使 preHandle 返回 false，afterCompletion 也会被调用
     */
    @Override
    public void afterCompletion(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler, 
                               Exception ex) throws Exception {
        
        // 获取所有插件拦截器
        List<HandlerInterceptor> pluginInterceptors = pluginInterceptorManager.getAllInterceptors();
        
        if (pluginInterceptors.isEmpty()) {
            return;
        }
        
        // 打印调试信息
        System.out.println("PluginDelegatingInterceptor.afterCompletion - 请求: " + request.getMethod() + " " + request.getRequestURI());
        System.out.println("PluginDelegatingInterceptor.afterCompletion - 当前插件拦截器数量: " + pluginInterceptors.size());
        System.out.println("PluginDelegatingInterceptor.afterCompletion - Exception: " + (ex != null ? ex.getMessage() : "null"));
        
        // 依次执行所有插件拦截器的 afterCompletion 方法
        for (HandlerInterceptor interceptor : pluginInterceptors) {
            try {
                System.out.println("PluginDelegatingInterceptor.afterCompletion - 执行插件拦截器: " + interceptor.getClass().getName());
                interceptor.afterCompletion(request, response, handler, ex);
            } catch (Exception e) {
                // 捕获异常，避免影响其他拦截器的执行
                System.out.println("PluginDelegatingInterceptor.afterCompletion - 拦截器 [" + interceptor.getClass().getName() + "] 执行失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("PluginDelegatingInterceptor.afterCompletion - 所有插件拦截器执行完毕");
    }
}

