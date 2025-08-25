package com.example.community.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Slf4j
@Component
public class OriginValidationInterceptor implements HandlerInterceptor {

    @Value("#{'${app.cors.allowed-origins}'.split(',')}")
    private List<String> allowedOrigins;

    @Value("${app.security.enable-origin-validation:true}")
    private boolean enableOriginValidation;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 개발/테스트 환경에서 Origin 검증 비활성화 가능
        if (!enableOriginValidation) {
            log.debug("🔓 Origin validation is DISABLED for development/testing");
            return true;
        }
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        String requestURI = request.getRequestURI();

        if (isCriticalPath(requestURI)) {
            if (!isValidOrigin(origin, referer)) {
                log.warn(" SECURITY: Invalid origin attempt for {}: origin={}, referer={}, ip={}",
                    requestURI, origin, referer, getClientIp(request));
                
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                    "{\"error\":\"ORIGIN_VALIDATION_FAILED\",\"message\":\"Invalid request origin\"}"
                );
                return false;
            }
        }

        return true;
    }

    private boolean isCriticalPath(String uri) {
        return uri.contains("/auth/refresh") || 
               uri.contains("/auth/logout") ||
               uri.startsWith("/api/admin/");
    }

    private boolean isValidOrigin(String origin, String referer) {
        // Check Origin header first
        if (StringUtils.hasText(origin)) {
            return allowedOrigins.contains(origin);
        }
        
        // Fallback to Referer validation
        if (StringUtils.hasText(referer)) {
            return allowedOrigins.stream()
                .anyMatch(allowed -> referer.startsWith(allowed));
        }
        
        // No valid origin/referer found
        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
