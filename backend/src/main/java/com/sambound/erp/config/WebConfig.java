package com.sambound.erp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 用于配置静态资源服务和前端路由支持
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态资源路径（前端构建产物）
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/")
                .setCachePeriod(3600); // 缓存1小时
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 配置前端路由支持：所有非 API 路径都返回 index.html（用于 Vue Router 的 history 模式）
        // 注意：API 路径由 SecurityConfig 处理，这里只处理前端路由
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}

