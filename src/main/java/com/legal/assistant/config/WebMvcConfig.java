package com.legal.assistant.config;

import com.legal.assistant.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private AuthInterceptor authInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                    "/api/auth/send-code",
                    "/api/auth/login",
                    "/api/share/**",  // 分享接口无需认证
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/doc.html",
                    "/webjars/**"
                );
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置Swagger/Knife4j静态资源
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
