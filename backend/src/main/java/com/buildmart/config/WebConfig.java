package com.buildmart.config;

import com.buildmart.interceptor.JwtTokenAdminInterceptor;
import com.buildmart.interceptor.JwtTokenUserInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig {
    private final JwtTokenAdminInterceptor adminInterceptor;
    private final JwtTokenUserInterceptor userInterceptor;
    private final String uploadDir;

    public WebConfig(JwtTokenAdminInterceptor adminInterceptor, JwtTokenUserInterceptor userInterceptor,
                     @Value("${app.upload.dir:uploads}") String uploadDir) {
        this.adminInterceptor = adminInterceptor;
        this.userInterceptor = userInterceptor;
        this.uploadDir = uploadDir;
    }

    @Bean
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOriginPatterns("*").allowedMethods("*").allowedHeaders("*");
            }

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(adminInterceptor)
                        .addPathPatterns("/admin/**")
                        .excludePathPatterns("/admin/auth/login");
                registry.addInterceptor(userInterceptor)
                        .addPathPatterns("/miniapp/**")
                        .excludePathPatterns("/miniapp/auth/login", "/miniapp/auth/login-phone",
                                "/miniapp/auth/sms-code", "/miniapp/auth/login-sms", "/miniapp/catalog/**");
            }

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // Uploaded files (user avatars). Publicly readable by design:
                // avatar URLs are shown on every page of the mini-program.
                String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
                registry.addResourceHandler("/files/**").addResourceLocations(location);
            }
        };
    }
}
