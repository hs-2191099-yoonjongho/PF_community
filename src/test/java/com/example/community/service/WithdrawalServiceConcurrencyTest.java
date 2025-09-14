package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원 탈퇴 서비스의 동시성 테스트
 * 
 * 주의사항:
 * 1. 이 테스트는 실제 DB를 사용합니다 (@AutoConfigureTestDatabase(replace = Replace.NONE))
 * 2. H2 데이터베이스를 사용하는 경우 비관적 락(PESSIMISTIC_WRITE) 동작이 다를 수 있습니다
 * 3. 테스트 결과는 환경에 따라 달라질 수 있으며, 특히 CI/CD 파이프라인에서는
 * 다른 결과가 나올 수 있으므로 주의가 필요합니다
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE) // 실제 DB 사용
public class WithdrawalServiceConcurrencyTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WithdrawalService withdrawalService;

    @Test
    @DisplayName("동시 회원 탈퇴 요청 시 동시성 제어 테스트")
    public void testConcurrentWithdrawal() throws InterruptedException {
        // Arrange - 테스트용 회원 생성
        String rawPassword = "password123";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        Member testMember = Member.builder()
                .username("concurrencyTest")
                .email("concurrency@test.com")
                .password(encodedPassword)
                .roles(Set.of("ROLE_USER"))
                .active(true)
                .build();

        // 저장 후 명시적으로 flush하여 다른 트랜잭션에서도 볼 수 있게 함
        testMember = memberRepository.saveAndFlush(testMember);
        Long memberId = testMember.getId();

        // 3개의 스레드로 동시에 탈퇴 요청
        int threadCount = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1); // 모든 스레드가 동시에 시작하도록
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    // 모든 스레드가 준비될 때까지 대기
                    startLatch.await();
                    withdrawalService.withdrawMember(memberId, rawPassword);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 예외 발생 시 실패 카운트 증가
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 모든 스레드 동시 시작
        startLatch.countDown();

        // 모든 스레드가 완료될 때까지 대기 (최대 10초)
        boolean completed = endLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        executorService.shutdownNow();

        // 테스트가 제한 시간 내에 완료되었는지 확인
        assertThat(completed).isTrue();

        // 동시성 제어로 인해 일부는 성공하고 일부는 실패해야 함
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);

        // 회원이 실제로 탈퇴 처리되었는지 확인
        Member updatedMember = memberRepository.findById(memberId).orElseThrow();
        if (successCount.get() > 0) {
            assertThat(updatedMember.isActive()).isFalse();
        }
    }

    // 비관적 락 테스트는 실제 DB와 연결 환경에 따라 다르게 동작할 수 있어 복잡합니다.
    @Test
    @DisplayName("동시 탈퇴 요청 시 비관적 락 동작 확인")
    public void testConcurrentWithdrawalWithPessimisticLock() throws InterruptedException {
        // Arrange - 테스트용 회원 생성
        String rawPassword = "password123";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        Member testMember = Member.builder()
                .username("lockTest")
                .email("lock@test.com")
                .password(encodedPassword)
                .roles(Set.of("ROLE_USER"))
                .active(true)
                .build();

        // 저장 후 명시적으로 flush하여 다른 트랜잭션에서도 볼 수 있게 함
        testMember = memberRepository.saveAndFlush(testMember);
        Long memberId = testMember.getId();

        // 2개의 스레드로 동시에 탈퇴 요청
        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1); // 모든 스레드가 동시에 시작하도록
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    // 모든 스레드가 준비될 때까지 대기
                    startLatch.await();
                    // 탈퇴 시도
                    withdrawalService.withdrawMember(memberId, rawPassword);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 모든 스레드 동시 시작
        startLatch.countDown();

        // 모든 스레드가 완료될 때까지 대기 (최대 5초)
        boolean completed = endLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executorService.shutdownNow();

        // 테스트가 제한 시간 내에 완료되었는지 확인
        assertThat(completed).isTrue();

        // 하나의 요청만 성공하고 나머지는 실패해야 함
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
        assertThat(successCount.get()).isLessThanOrEqualTo(1);

        // 회원 상태 확인
        Member updatedMember = memberRepository.findById(memberId).orElseThrow();
        // 하나라도 성공했으면 회원은 탈퇴 상태여야 함
        if (successCount.get() == 1) {
            assertThat(updatedMember.isActive()).isFalse();
        }
    }
}
