package com.example.community.service;

import com.example.community.config.JwtUtil;
import com.example.community.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @Mock
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        jwtUtil = new JwtUtil(memberRepository);
        set(jwtUtil, "secret", "local-test-secret-min-32-chars-1234567890");
        set(jwtUtil, "accessExpMs", 3600000L);
        set(jwtUtil, "issuer", "community-app");
        // 테스트를 위한 모의 데이터 설정
        when(memberRepository.findTokenVersionById(1L)).thenReturn(0);
        // call @PostConstruct
        invokeInit(jwtUtil);
    }

    @Test
    @DisplayName("JWT 액세스 토큰 생성 테스트 (id 기반)")
    void generateAccessTokenTest() {
        String token = jwtUtil.generateAccessToken(1L);
        assertThat(token).isNotNull();
        assertThat(token.split("\\.").length).isEqualTo(3);
    }

    @Test
    @DisplayName("JWT 토큰 검증 테스트 - 유효한 토큰 (id 기반)")
    void validateAccessTokenTest() {
        String token = jwtUtil.generateAccessToken(1L);
        assertThat(jwtUtil.validateAccess(token)).isTrue();
    }

    @Test
    @DisplayName("JWT 토큰 검증 테스트 - 만료된 토큰 (id 기반)")
    void validateExpiredTokenTest() throws Exception {
        set(jwtUtil, "accessExpMs", -60000L); // 60초 과거
        invokeInit(jwtUtil); // 재초기화
        String token = jwtUtil.generateAccessToken(1L);
        assertThat(jwtUtil.validateAccess(token)).isFalse();
    }

    @Test
    @DisplayName("JWT 토큰에서 id 추출 테스트")
    void getIdFromTokenTest() {
        String token = jwtUtil.generateAccessToken(1L);
        assertThat(jwtUtil.getId(token)).isEqualTo(1L);
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void invokeInit(JwtUtil target) throws Exception {
        var m = JwtUtil.class.getDeclaredMethod("init");
        m.setAccessible(true);
        m.invoke(target);
    }
}
