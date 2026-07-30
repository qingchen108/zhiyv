package com.smartmed.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析（ADR-0003：jjwt + HS256）。
 * <p>
 * 单种 JWT + {@code typ} 声明区分端别：
 * <ul>
 *   <li>B 端：{@code sub=sys_user.id, typ=B, role, doctor_id?}，过期 12h</li>
 *   <li>C 端：{@code sub=patient.id, typ=C}，过期 7d</li>
 * </ul>
 * 均无 refresh token。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    public static final String TYP_B = "B";
    public static final String TYP_C = "C";

    @Value("${smartmed.jwt.secret}")
    private String secret;

    @Value("${smartmed.jwt.b-expire-seconds}")
    private long bExpireSeconds;

    @Value("${smartmed.jwt.c-expire-seconds}")
    private long cExpireSeconds;

    private SecretKey key;

    @PostConstruct
    void init() {
        // HS256 要求密钥至少 256 bit（32 字节）
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足 32 字节，无法用于 HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /** 签发 B 端 token。doctorId 为 null 时（ADMIN）不写入 claim。mustChangePassword 写入供 /me 零 DB 回读（ADR-0005）。 */
    public String issueBToken(Long userId, String username, String role, Long doctorId, boolean mustChangePassword) {
        long now = System.currentTimeMillis();
        long exp = now + bExpireSeconds * 1000;
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("typ", TYP_B)
                .claim("role", role)
                .claim("username", username)
                .claim("doctor_id", doctorId)
                .claim("must_change_password", mustChangePassword)
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(key)
                .compact();
    }

    /** 签发 C 端 token。 */
    public String issueCToken(Long patientId) {
        long now = System.currentTimeMillis();
        long exp = now + cExpireSeconds * 1000;
        return Jwts.builder()
                .subject(String.valueOf(patientId))
                .claim("typ", TYP_C)
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(key)
                .compact();
    }

    /** B 端过期秒数（供登录响应 expiresIn 字段）。 */
    public long getBExpireSeconds() {
        return bExpireSeconds;
    }

    /** C 端过期秒数（供 demo-login 响应 expiresIn 字段）。 */
    public long getCExpireSeconds() {
        return cExpireSeconds;
    }

    /**
     * 解析并校验 token。失败返回 null（由调用方决定写 401）。
     */
    public UserPrincipal parse(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String typ = c.get("typ", String.class);
            Long userId = Long.valueOf(c.getSubject());
            if (TYP_B.equals(typ)) {
                String role = c.get("role", String.class);
                String username = c.get("username", String.class);
                Object did = c.get("doctor_id");
                Long doctorId = did == null ? null : Long.valueOf(did.toString());
                Boolean mustChange = c.get("must_change_password", Boolean.class);
                return UserPrincipal.builder()
                        .typ(TYP_B)
                        .userId(userId)
                        .username(username)
                        .role(role)
                        .doctorId(doctorId)
                        .mustChangePassword(mustChange != null && mustChange)
                        .build();
            }
            if (TYP_C.equals(typ)) {
                return UserPrincipal.builder()
                        .typ(TYP_C)
                        .userId(userId)
                        .patientId(userId)
                        .build();
            }
            log.warn("未知 typ: {}", typ);
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
