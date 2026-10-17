package com.buildmart.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtTokenService {
    private final SecretKey key;
    private final SecretKey userKey;
    private final long expireHours;

    public JwtTokenService(@Value("${app.jwt.secret}") String secret,
                           @Value("${app.jwt.user-secret:${app.jwt.secret}}") String userSecret,
                           @Value("${app.jwt.expire-hours:24}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.userKey = Keys.hmacShaKeyFor(userSecret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    public String issue(Long adminId, String username, String roleCode) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("adminId", adminId)
                .claim("role", roleCode)
                .claim("tokenType", "ADMIN")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public String issueUser(Long userId, String openid) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(openid)
                .claim("userId", userId)
                .claim("tokenType", "USER")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(userKey)
                .compact();
    }

    public Claims parseAdmin(String token) {
        Claims claims = parse(token, key);
        requireType(claims, "ADMIN");
        return claims;
    }

    public Claims parseUser(String token) {
        Claims claims = parse(token, userKey);
        requireType(claims, "USER");
        return claims;
    }

    private Claims parse(String token, SecretKey signingKey) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("JWT 令牌不能为空");
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    private void requireType(Claims claims, String expectedType) {
        if (!expectedType.equals(claims.get("tokenType", String.class))) {
            throw new IllegalArgumentException("JWT 令牌类型错误");
        }
    }
}
