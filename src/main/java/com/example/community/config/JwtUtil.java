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

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(issuer)
                .setAllowedClockSkewSeconds(30)
                .build();
    }

    public String generateAccessToken(String email) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + accessExpMs);
        
        // 사용자의 현재 토큰 버전 가져오기
        int tokenVersion = memberRepository.findTokenVersionByEmail(email);
        
        // 클레임에 토큰 버전 추가
        Map<String, Object> claims = new HashMap<>();
        claims.put("ver", tokenVersion);

        return Jwts.builder()
                .setSubject(email)
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(exp)
                .addClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateAccess(String token) {
        try {
            parser.parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getEmail(String token) {
        return parser.parseClaimsJws(token).getBody().getSubject();
    }
    
    /**
     * JWT 토큰을 파싱하여 Claims를 반환합니다.
     * 유효하지 않은 토큰인 경우 예외가 발생합니다.
     * 
     * @param token 검증할 JWT 토큰
     * @return 토큰에서 추출한 Claims 객체
     * @throws JwtException 토큰이 유효하지 않은 경우
     */
    public Claims parseClaims(String token) throws JwtException {
        return parser.parseClaimsJws(token).getBody();
    }
    
    /**
     * JWT 서명 키를 반환합니다.
     * 필터에서 클레임을 파싱할 때 사용됩니다.
     * @return JWT 서명 키
     */
    public SecretKey getKey() {
        return this.key;
    }
}