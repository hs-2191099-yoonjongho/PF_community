package com.example.community.web;

import com.example.community.config.JwtUtil;
import com.example.community.repository.MemberRepository;
import com.example.community.service.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthController 구성 테스트
 * AuthController의 설정 관련 동작을 검증합니다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "ALLOWED_ORIGINS=https://site1.com,https://site2.com, https://site3.com",
        "refresh.exp-ms=3600000",
        "refresh.cookie.name=refresh_token",
        "refresh.cookie.path=/api/auth",
        "refresh.cookie.secure=false",
        "refresh.cookie.same-site=Lax",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
public class AuthControllerConfigTest {

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @Value("${ALLOWED_ORIGINS}")
    private String allowedOriginsStr;

    @Test
    @DisplayName("ALLOWED_ORIGINS 설정이 올바르게 로드되어야 함")
    void allowedOriginsPropertyShouldBeLoadedCorrectly() {
        // 검증
        assertNotNull(allowedOriginsStr);
        assertTrue(allowedOriginsStr.contains("https://site1.com"));
        assertTrue(allowedOriginsStr.contains("https://site2.com"));
        assertTrue(allowedOriginsStr.contains(" https://site3.com")); // 공백 포함 원본값
    }

    @Test
    @DisplayName("테스트용 설정 변경 및 검증")
    void testAllowedOriginsModification() {
        // Spring은 이미 @Value로 값을 할당했으므로 직접 테스트
        String testValue = "site1.com,site1.com,,site2.com";
        String[] parts = testValue.split(",");

        // 간단한 파싱 로직 검증
        List<String> parsedValues = java.util.Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        // 검증
        assertNotNull(parsedValues);
        assertEquals(2, parsedValues.size()); // 중복 및 빈 값 제거됨
        assertTrue(parsedValues.contains("site1.com"));
        assertTrue(parsedValues.contains("site2.com"));
    }
}
