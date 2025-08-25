package com.example.community.service;

import com.example.community.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/*
 * 만료된 토큰 자동 정리로 데이터베이스 성능 유지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    // 매일 새벽 2시(한국 시간)
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Seoul")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("만료된 리프레시 토큰 정리 작업 시작");
        int deletedCount = refreshTokenRepository.deleteExpired(Instant.now());
        log.info("만료된 리프레시 토큰 정리 완료: {}개 삭제", deletedCount);
    }

    // 수동 실행용
    @Transactional
    public int manualCleanup() {
        log.info("수동 토큰 정리 실행");
        return refreshTokenRepository.deleteExpired(Instant.now());
    }
}
