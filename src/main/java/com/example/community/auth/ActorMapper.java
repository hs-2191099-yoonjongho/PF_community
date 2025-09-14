package com.example.community.auth;

import com.example.community.security.MemberDetails;
import java.util.stream.Collectors;

// MemberDetails → Actor 변환 유틸리티
public class ActorMapper {
    // MemberDetails 객체를 Actor로 변환
    public static Actor from(MemberDetails me) {
        return new Actor(
                me.getId(),
                me.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .collect(Collectors.toSet()));
    }
}
