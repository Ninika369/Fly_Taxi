package com.george.apidriver.interceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * This class provides a interceptor for all the paths
 */
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    @Bean
    public JwtInterceptor jwtInterceptor(){
        return new JwtInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor())
                // intercept paths
                .addPathPatterns("/**")
                // paths without interceptions
                .excludePathPatterns("/noauth")
                .excludePathPatterns("/verification-code")
                .excludePathPatterns("/verification-code-check")
                ;
    }
}