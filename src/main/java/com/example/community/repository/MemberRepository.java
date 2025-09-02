package com.example.community.repository;

import com.example.community.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByUsername(String username);
    Optional<Member> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    
    @Query("SELECT m.tokenVersion FROM Member m WHERE m.id = :id")
    int findTokenVersionById(@Param("id") Long id);
    
    @Query("SELECT m.tokenVersion FROM Member m WHERE m.email = :email")
    int findTokenVersionByEmail(@Param("email") String email);
    
    /**
     * 회원 탈퇴 및 중요 작업을 위한 PESSIMISTIC_WRITE 락 획득
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findByIdWithPessimisticLock(@Param("id") Long id);
}
