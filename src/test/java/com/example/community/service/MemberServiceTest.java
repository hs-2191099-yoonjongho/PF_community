package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {
    @Mock
    MemberRepository memberRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    RefreshTokenRepository refreshTokenRepository;
    @InjectMocks
    MemberService memberService;

    @Test
    @DisplayName("비밀번호 변경 시 토큰 버전 증가 확인")
    void updatePassword_bumpTokenVersion() {
        // given
        Member member = Member.builder()
                .id(1L)
                .username("user1")
                .email("user1@email.com")
                .password("encodedOld")
                .tokenVersion(0)
                .build();
        when(memberRepository.findById(anyLong())).thenReturn(Optional.of(member));
        // 현재 비밀번호는 일치, 새 비밀번호는 다름
        when(passwordEncoder.matches(eq("old"), eq("encodedOld"))).thenReturn(true);
        when(passwordEncoder.matches(eq("new"), eq("encodedOld"))).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedNew");

        // when
        memberService.updatePassword(1L, "old", "new");

        // then
        assertThat(member.getTokenVersion()).isEqualTo(1);
        verify(refreshTokenRepository).deleteAllByUserId(1L);
    }
}
