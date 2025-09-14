package com.example.community.repository;

import com.example.community.domain.auth.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * 리프레시 토큰 엔티티에 대한 데이터 접근 인터페이스
 * 토큰 관리, 조회, 폐기 기능을 제공합니다.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 모든 토큰 조회
     * 토큰 재사용 탐지에 사용됩니다.
     * 
     * @param tokenHash 토큰 해시
     * @return 리프레시 토큰 (Optional)
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 만료된 토큰 자동 청소
     * 운영 효율성을 위해 사용됩니다.
     * 
     * @param now 현재 시간
     * @return 삭제된 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    /**
     * 회원 탈퇴 시 해당 회원의 모든 토큰 삭제
     * 
     * @param userId 사용자 ID
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    /**
     * 활성(미폐기) 토큰만 폐기(CAS)
     * 
     * @param hash 토큰 해시
     * @return 영향 받은 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.tokenHash = :hash AND rt.revoked = false")
    int markRevokedIfActive(@Param("hash") String hash);
}