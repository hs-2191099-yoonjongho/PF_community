package com.example.community.web.dto;

import com.example.community.domain.Member;

public record MemberRes(Long id, String username, String email) {
    public static MemberRes of(Member m) {
        return new MemberRes(m.getId(), m.getUsername(), m.getEmail());
    }
}