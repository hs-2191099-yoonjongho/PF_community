package com.example.community.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OriginValidationInterceptor implements HandlerInterceptor {

    //  SpEL split 제거, 프로퍼티 누락 시 빈 문자열로 들어오게 함
    @Value("${app.cors.allowed-origins:}")
    private String allowedOriginsStr;

    private Set<String> allowedOriginSet;

    @PostConstruct
    public void init() {
        // 빈 문자열이면 빈 리스트
        List<String> raw = allowedOriginsStr.isBlank()
                ? List.of()
                : Arrays.stream(allowedOriginsStr.split(",")).toList();

        // 오리진 정규화: trim → canonicalOrigin → 빈값 제거 → 불변 Set
        this.allowedOriginSet = raw.stream()
                .map(String::trim)
                .map(this::canonicalOrigin)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());

        if (allowedOriginSet.isEmpty()) {
            //  fail-close 안내: 비-GET 모두 거부됨(읽기만 허용)
            log.error("허용 오리진이 비었습니다. 비-GET 요청은 모두 거부됩니다(읽기 전용 모드).");
        } else {
            log.info("허용된 오리진(정규화): {}", allowedOriginSet);
        }
    }

    private String canonicalOrigin(String urlOrOrigin) {
        if (!StringUtils.hasText(urlOrOrigin)) return "";
        try {
            URI u = URI.create(urlOrOrigin);
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
            String host   = u.getHost()   == null ? "" : u.getHost().toLowerCase();
            int port = u.getPort();

            if (scheme.isEmpty() || host.isEmpty()) return "";
            if (!("http".equals(scheme) || "https".equals(scheme))) return "";

            boolean defaultPort = (port == -1) ||
                    ("http".equals(scheme) && port == 80) ||
                    ("https".equals(scheme) && port == 443);

            return defaultPort ? scheme + "://" + host
                               : scheme + "://" + host + ":" + port;
        } catch (Exception e) {
            log.warn("허용 오리진 정규화 실패: {}, 오류: {}", urlOrOrigin, e.getMessage());
            return "";
        }
    }

    @Value("${app.security.enable-origin-validation:true}")
    private boolean enableOriginValidation;

    private void deny(HttpServletResponse res, String requestURI, String origin, String referer, String clientIp) throws Exception {
        log.warn(" SECURITY: Invalid origin attempt for {}: origin={}, referer={}, ip={}",
                requestURI, origin, referer, clientIp);
        res.setStatus(HttpStatus.FORBIDDEN.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"error\":\"ORIGIN_VALIDATION_FAILED\",\"message\":\"Invalid request origin\"}");
    }

    private void deny(HttpServletResponse res) throws Exception {
        res.setStatus(HttpStatus.FORBIDDEN.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"error\":\"ORIGIN_VALIDATION_FAILED\",\"message\":\"Invalid request origin\"}");
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object h) throws Exception {
        if (!enableOriginValidation) return true;

        //  읽기 메서드는 항상 허용
        String m = req.getMethod();
        if ("GET".equalsIgnoreCase(m) || "HEAD".equalsIgnoreCase(m) || "OPTIONS".equalsIgnoreCase(m)) {
            return true;
        }

        //  허용 오리진이 비면 비-GET 전면 거부(읽기만 허용 모드)
        if (allowedOriginSet.isEmpty()) {
            log.warn("허용 오리진 비어있음 → {} {} 거부", m, req.getRequestURI());
            deny(res);
            return false;
        }

        String origin = req.getHeader("Origin");
        String referer = req.getHeader("Referer");
        String uri = req.getRequestURI();

        //  비-GET은 Origin 또는 Referer 필수
        if (!StringUtils.hasText(origin) && !StringUtils.hasText(referer)) {
            deny(res, uri, "[헤더 누락]", "[헤더 누락]", getClientIp(req));
            return false;
        }

        //  모든 /api/** 경로는 중요 경로로 보고 오리진 유효성 검사
        if (uri.startsWith("/api/") && !isValidOrigin(origin, referer)) {
            deny(res, uri, origin, referer, getClientIp(req));
            return false;
        }

        return true;
    }

    private boolean isValidOrigin(String origin, String referer) {
        if (!StringUtils.hasText(origin) && !StringUtils.hasText(referer)) return false;

        if (StringUtils.hasText(origin)) {
            String normalized = canonicalOrigin(origin);
            return !normalized.isEmpty() && allowedOriginSet.contains(normalized);
        }

        // Referer fallback
        try {
            URI r = URI.create(referer);
            String refOrigin = canonicalOrigin(
                    r.getScheme() + "://" + r.getHost() + (r.getPort() == -1 ? "" : ":" + r.getPort()));
            return !refOrigin.isEmpty() && allowedOriginSet.contains(refOrigin);
        } catch (Exception e) {
            return false;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) return xff.split(",")[0].trim();
        String cf = request.getHeader("CF-Connecting-IP");
        if (StringUtils.hasText(cf)) return cf.trim();
        String tci = request.getHeader("True-Client-IP");
        if (StringUtils.hasText(tci)) return tci.trim();
        return request.getRemoteAddr();
    }
}
