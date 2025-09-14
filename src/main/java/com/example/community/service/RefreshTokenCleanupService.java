package com.example.community.service;

import com.example.community.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 만료된 리프레시 토큰을 자동으로 정리하는 서비스
 * 스케줄링을 통해 정기적으로 데이터베이스에서 만료된 토큰을 삭제하여
 * 데이터베이스 크기를 관리하고 성능을 최적화합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * 매일 새벽 2시(한국 시간)에 만료된 리프레시 토큰을 자동으로 정리합니다.
     * 비즈니스 트래픽이 적은 시간대에 실행되도록 설정되었습니다.
     */
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Seoul")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant start = Instant.now();
        log.info("만료된 리프레시 토큰 정리 작업 시작");

        int deletedCount = refreshTokenRepository.deleteExpired(start);

        Duration duration = Duration.between(start, Instant.now());
        log.info("만료된 리프레시 토큰 정리 완료: {}개 삭제, 소요시간: {}ms",
                deletedCount, duration.toMillis());
    }
}
