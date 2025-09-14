package com.example.community.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 인증된 사용자가 권한이 없는 리소스에 접근할 때 호출되는 핸들러
 * 403 Forbidden 응답을 일관된 형식으로 반환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                     AccessDeniedException accessDeniedException) throws IOException, ServletException {
        log.warn("접근 거부: {}, 경로: {}", accessDeniedException.getMessage(), request.getRequestURI());
        
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        Map<String, Object> body = Map.of(
            "success", false,
            "status", HttpStatus.FORBIDDEN.value(),
            "error", "Forbidden",
            "message", "해당 리소스에 접근할 권한이 없습니다",
            "path", request.getRequestURI()
        );
        
        objectMapper.writeValue(response.getWriter(), body);
    }
}
