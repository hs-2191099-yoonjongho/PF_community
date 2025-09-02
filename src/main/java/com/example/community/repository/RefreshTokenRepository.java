package com.example.community.repository;

import com.example.community.domain.auth.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    
    // 활성 토큰만 조회 (보안 강화)
    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);
    
    // 모든 토큰 조회 (재사용 탐지용)
    Optional<RefreshToken> findByTokenHash(String tokenHash);
      
    // 만료된 토큰 자동 청소 (운영 효율성)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
    
    // 사용자별 토큰 일괄 폐기 (로그아웃 시)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    int bulkRevokeByUserId(@Param("userId") Long userId);
    
    // 회원 탈퇴 시 해당 회원의 모든 토큰 삭제
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}