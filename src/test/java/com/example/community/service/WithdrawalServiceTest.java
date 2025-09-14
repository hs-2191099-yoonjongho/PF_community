package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.repository.CommentRepository;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.PostRepository;
import com.example.community.repository.RefreshTokenRepository;
import com.example.community.service.exception.EntityNotFoundException;
import com.example.community.service.exception.WithdrawalException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WithdrawalServiceTest {

        @Mock
        private MemberRepository memberRepository;

        @Mock
        private PostRepository postRepository;

        @Mock
        private CommentRepository commentRepository;

        @Mock
        private RefreshTokenRepository refreshTokenRepository;

        @Mock
        private PasswordEncoder passwordEncoder;

        @InjectMocks
        private WithdrawalService withdrawalService;

        @Test
        @DisplayName("정상적인 회원 탈퇴 처리")
        void withdrawMemberSuccess() {
                // Arrange
                Long memberId = 1L;
                String password = "correctPassword";

                Member member = Member.builder()
                                .id(memberId)
                                .username("testUser")
                                .email("test@example.com")
                                .password("encodedPassword")
                                .roles(Set.of("ROLE_USER"))
                                .active(true)
                                .build();

                when(memberRepository.findByIdWithPessimisticLock(memberId)).thenReturn(Optional.of(member));
                when(passwordEncoder.matches(password, member.getPassword())).thenReturn(true);
                when(postRepository.markPostsByAuthorIdAsWithdrawn(memberId)).thenReturn(2);
                when(commentRepository.anonymizeByAuthorId(memberId)).thenReturn(3);

                // Act
                withdrawalService.withdrawMember(memberId, password);

                // Assert
                assertThat(member.isActive()).isFalse();
                assertThat(member.getWithdrawalDate()).isNotNull();
                assertThat(member.getTokenVersion()).isEqualTo(1);

                verify(memberRepository).findByIdWithPessimisticLock(memberId);
                // 영속 상태 엔티티는 트랜잭션 커밋 시 자동으로 변경 감지되므로 save() 호출 검증 제거
                verify(refreshTokenRepository).deleteAllByUserId(memberId);
                verify(postRepository).markPostsByAuthorIdAsWithdrawn(memberId);
                verify(commentRepository).anonymizeByAuthorId(memberId);
        }

        @Test
        @DisplayName("존재하지 않는 회원 탈퇴 시도")
        void withdrawNonExistentMember() {
                // Arrange
                Long memberId = 999L;
                String password = "password";

                when(memberRepository.findByIdWithPessimisticLock(memberId)).thenReturn(Optional.empty());

                // Act & Assert
                assertThatThrownBy(() -> withdrawalService.withdrawMember(memberId, password))
                                .isInstanceOf(EntityNotFoundException.class)
                                .hasMessageContaining("회원");
        }

        @Test
        @DisplayName("이미 탈퇴한 회원 재탈퇴 시도")
        void withdrawAlreadyWithdrawnMember() {
                // Arrange
                Long memberId = 1L;
                String password = "password";

                Member member = Member.builder()
                                .id(memberId)
                                .username("testUser")
                                .email("test@example.com")
                                .password("encodedPassword")
                                .roles(Set.of("ROLE_USER"))
                                .active(false) // 이미 탈퇴 상태
                                .build();

                when(memberRepository.findByIdWithPessimisticLock(memberId)).thenReturn(Optional.of(member));

                // Act & Assert
                assertThatThrownBy(() -> withdrawalService.withdrawMember(memberId, password))
                                .isInstanceOf(WithdrawalException.class)
                                .hasMessageContaining("이미 탈퇴");
        }

        @Test
        @DisplayName("관리자 계정 탈퇴 시도")
        void withdrawAdminAccount() {
                // Arrange
                Long memberId = 1L;
                String password = "password";

                Member member = Member.builder()
                                .id(memberId)
                                .username("admin")
                                .email("admin@example.com")
                                .password("encodedPassword")
                                .roles(Set.of("ROLE_ADMIN", "ROLE_USER"))
                                .active(true)
                                .build();

                when(memberRepository.findByIdWithPessimisticLock(memberId)).thenReturn(Optional.of(member));

                // Act & Assert
                assertThatThrownBy(() -> withdrawalService.withdrawMember(memberId, password))
                                .isInstanceOf(WithdrawalException.class)
                                .hasMessageContaining("관리자");
        }

        @Test
        @DisplayName("잘못된 비밀번호로 탈퇴 시도")
        void withdrawWithIncorrectPassword() {
                // Arrange
                Long memberId = 1L;
                String wrongPassword = "wrongPassword";

                Member member = Member.builder()
                                .id(memberId)
                                .username("testUser")
                                .email("test@example.com")
                                .password("encodedPassword")
                                .roles(Set.of("ROLE_USER"))
                                .active(true)
                                .build();

                when(memberRepository.findByIdWithPessimisticLock(memberId)).thenReturn(Optional.of(member));
                when(passwordEncoder.matches(wrongPassword, member.getPassword())).thenReturn(false);

                // Act & Assert
                assertThatThrownBy(() -> withdrawalService.withdrawMember(memberId, wrongPassword))
                                .isInstanceOf(WithdrawalException.class)
                                .hasMessageContaining("비밀번호");
        }
}
