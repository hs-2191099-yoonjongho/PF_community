package com.example.community.repository;

import com.example.community.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * 회원 엔티티에 대한 데이터 접근 인터페이스
 * 회원 조회, 인증, 토큰 관리 기능을 제공합니다.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 회원 id로 토큰 버전 조회 (JWT id 기반 검증용)
     * 
     * @param id 회원 id
     * @return 토큰 버전
     */
    @Query("SELECT m.tokenVersion FROM Member m WHERE m.id = :id")
    Integer findTokenVersionById(@Param("id") Long id);

    /**
     * 이메일로 회원 조회
     * 
     * @param email 이메일
     * @return 회원 (Optional)
     */
    Optional<Member> findByEmail(String email);

    /**
     * 회원 탈퇴 및 중요 작업을 위한 PESSIMISTIC_WRITE 락 획득
     * 동시성 제어를 위해 사용됩니다.
     * 
     * @param id 회원 ID
     * @return 회원 (Optional)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findByIdWithPessimisticLock(@Param("id") Long id);
}
