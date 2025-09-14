package com.example.community.repository;

import com.example.community.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@org.springframework.test.context.ActiveProfiles("test")
class MemberRepositoryTest {

    @Autowired
    private MemberRepository members;

    @Test
    @DisplayName("이메일/아이디 유니크 제약 위반 발생")
    void unique_constraints() {
        Member a = Member.builder().username("u1").email("e1@test.com").password("p").roles(Set.of("ROLE_USER"))
                .build();
        members.saveAndFlush(a);

        Member b = Member.builder().username("u2").email("e1@test.com").password("p").roles(Set.of("ROLE_USER"))
                .build();
        assertThatThrownBy(() -> {
            members.saveAndFlush(b);
        })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("회원 저장/조회 기본 동작")
    void save_and_find() {
        Member a = Member.builder().username("u1").email("e1@test.com").password("p").roles(Set.of("ROLE_USER"))
                .build();
        members.save(a);
        assertThat(members.findByEmail("e1@test.com")).isPresent();
    }
}
