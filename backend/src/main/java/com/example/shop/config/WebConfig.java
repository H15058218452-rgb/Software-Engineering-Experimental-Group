package com.example.shop.config;

import com.example.shop.auth.SellerAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SellerAuthInterceptor sellerAuthInterceptor;
    private final String uploadDir;

    public WebConfig(SellerAuthInterceptor sellerAuthInterceptor,
                     @Value("${app.upload-dir}") String uploadDir) {
        this.sellerAuthInterceptor = sellerAuthInterceptor;
        this.uploadDir = uploadDir;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sellerAuthInterceptor)
                .addPathPatterns("/api/product/**", "/api/seller/**", "/api/upload/**")
                .excludePathPatterns("/api/seller/login");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:5173", "http://localhost:5174")
                .allowedMethods("*")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 必须以 "/" 结尾，否则 Spring 会把该路径当文件而非目录，导致 /uploads/** 全部 404
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
