package com.example.community.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

import java.nio.file.Paths;
import java.net.URI;

/**
 * 정적 리소스 서빙 설정
 * 업로드된 파일을 웹에서 접근할 수 있도록 매핑합니다.
 * publicBaseUrl의 경로와 자동으로 일치시켜 설정 오류를 방지합니다.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.storage.local.base-path:uploads}")
    private String storagePath;

    @Value("${app.public-base-url}")
    private String publicBaseUrl; // 예: http://localhost:8080/uploads

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get(storagePath).toAbsolutePath().normalize().toString();

        // publicBaseUrl의 path만 추출 (예: /uploads)
        String handlerPath = URI.create(publicBaseUrl).getPath();
        if (handlerPath.isEmpty()) {
            handlerPath = "/uploads"; // 기본값
        }
        if (!handlerPath.endsWith("/")) {
            handlerPath += "/";
        }

        // 추출된 경로로 정적 리소스 핸들러 설정 (예: '/uploads/**')
        registry.addResourceHandler(handlerPath + "**")
                .addResourceLocations("file:" + absolutePath + "/")
                .setCachePeriod(3600) // 1시간 캐싱
                .resourceChain(true);

    }
}
