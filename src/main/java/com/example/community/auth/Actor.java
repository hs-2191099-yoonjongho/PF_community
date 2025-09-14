package com.example.community.auth;

import java.util.Objects;
import java.util.Set;


// 인증된 사용자(주체)의 id와 권한 정보를 담는 불변 객체
public record Actor(Long id, Set<String> roles) {


    // 생성자: id는 null 불가, roles는 null-safe 및 불변화 처리
    public Actor {
        Objects.requireNonNull(id, "id must not be null");
        // null-안전 + 불변화
        roles = (roles == null) ? Set.of() : Set.copyOf(roles);
    }


    // 관리자 권한 여부
    public boolean isAdmin() {
        return roles.contains("ROLE_ADMIN");
    }

    // 특정 권한 보유 여부
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
