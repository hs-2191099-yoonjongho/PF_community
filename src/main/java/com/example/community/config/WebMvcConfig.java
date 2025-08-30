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
                // GET 메소드만 제외하는 것으로 변경 - 인터셉터에서 GET은 이미 허용하기 때문
                // "/api/posts",             // 게시글 목록 조회는 제외
                // "/api/posts/filter",      // 필터링된 게시글 목록은 제외
                // "/api/posts/popular",     // 인기 게시글 목록은 제외
                // "/api/posts/best",        // 베스트 게시글 목록은 제외
                // "/api/posts/recommended", // 추천 게시글 목록은 제외
                // "/api/posts/board/**",    // 게시판별 게시글 목록은 제외
                // "/api/posts/*"            // 숫자 ID에 대응 (like는 제외됨: /api/posts/*/like)
            );
    }
}
