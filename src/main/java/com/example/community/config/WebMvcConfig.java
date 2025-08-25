package com.example.community.config;

import com.example.community.security.OriginValidationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final OriginValidationInterceptor originValidationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(originValidationInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/auth/login",     // 로그인은 제외 (초기 요청)
                "/api/auth/signup",    // 회원가입은 제외 (register -> signup)
                "/api/posts/**"        // 공개 게시물 조회는 제외
            );
    }
}
