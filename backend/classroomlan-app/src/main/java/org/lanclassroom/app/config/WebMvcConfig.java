package org.lanclassroom.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * SPA fallback - 修复 Vue Router history 模式刷新 404。
 *
 * 任何 GET 请求若：
 *   - 不是 /api/**
 *   - 不是 /ws/**
 *   - 静态资源不存在
 * 就返回 classpath:/static/index.html，让 Vue Router 接管。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws java.io.IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // /api 与 /ws 由各自 controller / endpoint 处理，这里不会进
                        // 静态资源也不存在 → 把所有"看起来像页面路径"的请求落到 index.html
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("ws/")) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 显式声明根路径 → index.html
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
