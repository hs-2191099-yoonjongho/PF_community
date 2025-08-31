package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.domain.auth.RefreshToken;
import com.example.community.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 리프레시 토큰 서비스 단위 테스트
 * 모의 객체를 사용하여 리프레시 토큰 생성, 검증, 순환, 폐기 기능 테스트
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private Member testUser;
    private RefreshToken validRefreshToken;

    @BeforeEach
    void setUp() {
        // 테스트에 사용할 리프레시 토큰 만료 시간 설정
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpMs", 3600000L); // 1시간

        // 테스트 사용자 생성
        testUser = Member.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("password")
                .roles(Set.of("ROLE_USER"))
                .build();

        // 유효한 리프레시 토큰 생성
        validRefreshToken = RefreshToken.builder()
                .id(1L)
                .tokenHash("hashedTokenValue")
                .user(testUser)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
    }

    @Test
    @DisplayName("리프레시 토큰 발급 테스트")
    void issueRefreshTokenTest() {
        // given
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(validRefreshToken);

        // when
        String refreshToken = refreshTokenService.issue(testUser);

        // then
        assertThat(refreshToken).isNotNull();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("유효한 리프레시 토큰 검증 테스트")
    void validateValidRefreshTokenTest() {
        // given
        String rawToken = "validRawToken";
        // 리프레시 토큰 서비스의 hashToken 메서드가 private이므로 모킹을 통해 우회
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(validRefreshToken));

        // when
        RefreshToken result = refreshTokenService.validateAndGet(rawToken);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(validRefreshToken.getId());
        assertThat(result.getUser()).isEqualTo(testUser);
    }

    @Test
    @DisplayName("만료된 리프레시 토큰 검증 테스트")
    void validateExpiredRefreshTokenTest() {
        // given
        String rawToken = "expiredRawToken";
        RefreshToken expiredToken = RefreshToken.builder()
                .id(2L)
                .tokenHash("expiredHashedToken")
                .user(testUser)
                .expiresAt(Instant.now().minusSeconds(3600)) // 만료된 토큰
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(expiredToken));

        // when
        RefreshToken result = refreshTokenService.validateAndGet(rawToken);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("리프레시 토큰 순환(rotate) 테스트")
    void rotateRefreshTokenTest() {
        // given
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(validRefreshToken);

        // when
        String newToken = refreshTokenService.rotate(validRefreshToken);

        // then
        assertThat(newToken).isNotNull();
        assertThat(validRefreshToken.isRevoked()).isTrue(); // 기존 토큰이 폐기되었는지 확인
        verify(refreshTokenRepository).save(validRefreshToken); // 폐기된 토큰 저장 확인
    }

    @Test
    @DisplayName("리프레시 토큰 폐기 테스트")
    void revokeRefreshTokenTest() {
        // given
        String rawToken = "tokenToRevoke";
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(validRefreshToken));

        // when
        refreshTokenService.revoke(rawToken);

        // then
        assertThat(validRefreshToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(validRefreshToken);
    }
}
