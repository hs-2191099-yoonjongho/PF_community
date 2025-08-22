package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.repository.MemberRepository;
import com.example.community.service.dto.AuthDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member signUp(AuthDtos.SignUp req) {
        if (members.existsByUsername(req.username())) throw new IllegalArgumentException("username already used");
        if (members.existsByEmail(req.email())) throw new IllegalArgumentException("email already used");
        Member m = Member.builder()
                .username(req.username())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .roles(Set.of("ROLE_USER"))
                .build();
        return members.save(m);
    }

    @Transactional(readOnly = true)
    public Member getByUsername(String username) {
        return members.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("member not found"));
    }
}
