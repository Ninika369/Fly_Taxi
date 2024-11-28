package com.geroge.apipassenger.interceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Author: George Sun
 * @Date: 2024-10-19-13:40
 * @Description: com.geroge.apipassenger.interceptor
 */
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    // before interceptor is executed, firstly initialize the Bean variables in interceptor
    @Bean
    public JwtInterceptor getJwtInterceptor() {
        return new JwtInterceptor();
    }

    /**
     * This function is used to set interceptors to examine the correctness of token
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(getJwtInterceptor())
                // intercept all paths
                .addPathPatterns("/**")
                // pass through specific paths
                .excludePathPatterns("/noAuthTest")
                .excludePathPatterns("/test-real-time-order/**")
                .excludePathPatterns("/verification-code")
                .excludePathPatterns("/verification-code_check")
                .excludePathPatterns("/token-refresh");
    }
}
