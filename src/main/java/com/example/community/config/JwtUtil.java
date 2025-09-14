package com.example.community.config;

import com.example.community.repository.MemberRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


/**
 * JWT 관련 유틸리티 (토큰 발급, 검증, 파싱 등)
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-exp-ms}")
    private long accessExpMs;

    @Value("${jwt.issuer}")
    private String issuer;

    private final MemberRepository memberRepository;

    private SecretKey key;
    private JwtParser parser;


    /**
     * 시크릿 키 및 파서 초기화
     */
    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(issuer)
                .setAllowedClockSkewSeconds(30)
                .build();
    }

    /**
     * id(Long) 기반 JWT AccessToken 발급
     * @param id 사용자 PK
     * @return JWT AccessToken 문자열
     */
    public String generateAccessToken(long id) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + accessExpMs);
        int tokenVersion = memberRepository.findTokenVersionById(id);
        Map<String, Object> claims = new HashMap<>();
        claims.put("ver", tokenVersion);
        return Jwts.builder()
                .setSubject(Long.toString(id))
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(exp)
                .addClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }


    /**
     * JWT AccessToken 유효성 검증
     * @param token JWT AccessToken
     * @return 유효하면 true, 아니면 false
     */
    public boolean validateAccess(String token) {
        try {
            parser.parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }


    /**
     * JWT에서 id(Long) 추출
     * @param token JWT AccessToken
     * @return subject(id) 값(Long)
     */
    public Long getId(String token) {
        return Long.parseLong(parser.parseClaimsJws(token).getBody().getSubject());
    }


    /**
     * JWT 토큰을 파싱하여 Claims를 반환
     * @param token JWT 토큰
     * @return Claims 객체
     * @throws JwtException 유효하지 않은 토큰
     */
    public Claims parseClaims(String token) throws JwtException {
        return parser.parseClaimsJws(token).getBody();
    }

    /**
     * JWT 서명 키 반환 (필터 등에서 사용)
     * @return JWT 서명용 SecretKey
     */
    public SecretKey getKey() {
        return this.key;
    }
}