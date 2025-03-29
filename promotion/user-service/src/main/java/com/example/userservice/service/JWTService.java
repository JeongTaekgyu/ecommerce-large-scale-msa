package com.example.userservice.service;

import com.example.userservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@Slf4j
public class JWTService {
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String secretKey;

    @Autowired
    public JWTService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String generateToken(User user) {
        long currentTimeMillis = System.currentTimeMillis();
        // 유저 정보를 가지고 jwt를 만든다.
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", "USER")
                .issuedAt(new Date(currentTimeMillis)) // 발급된 시간
                .expiration(new Date(currentTimeMillis + 3600000)) // 만료 시간 1시간
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8))) // 서명 생성
                .compact();
    }

    public Claims validateToken(String token) {
        try {
            return parseJwtClaims(token);
        } catch (Exception e) {
            log.error("Token validation error: ", e);
            throw new IllegalArgumentException("Invalid token");
        }
    }

    // JWT 토큰을 검증(verify)하고, Claims(페이로드)를 추출하여 반환
    private Claims parseJwtClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String refreshToken(String token) {
        Claims claims = parseJwtClaims(token);
        long currentTimeMillis = System.currentTimeMillis();
        // 토큰을 가지고 jwt를 만든다.
        return Jwts.builder()
                .subject(claims.getSubject())
                .claims(claims)
                .issuedAt(new Date(currentTimeMillis))
                .expiration(new Date(currentTimeMillis + 3600000)) // 만료 시간 1시간
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
