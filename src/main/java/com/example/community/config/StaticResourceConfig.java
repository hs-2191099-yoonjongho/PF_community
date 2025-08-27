package com.example.community.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry reg) {
        // /files/** → 로컬 디렉토리(uploads/)의 실제 파일 제공
        reg.addResourceHandler("/files/**")
           .addResourceLocations("file:uploads/")
           .setCachePeriod(60 * 60) // 1시간 캐싱
           .resourceChain(true);
    }
}
