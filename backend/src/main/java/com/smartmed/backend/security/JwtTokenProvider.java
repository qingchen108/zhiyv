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
import java.util.UUID;

/**
 * JWT 签发与解析（ADR-0003：jjwt + HS256；07 增强：access/refresh 分离）。
 * <p>
 * B 端双 token 机制（ADR-0013）：
 * <ul>
 *   <li><b>access</b>：{@code typ=B}，{@code sub=sys_user.id, role, doctor_id, jti, refresh_jti, absolute_exp}，
 *       默认有效期 30min（b-access-expire-seconds）。{@code absolute_exp} 为固定 8h 窗口的绝对截止时刻，
 *       由会话创建时决定、续期不延长（Q12 固定窗口）。</li>
 *   <li><b>refresh</b>：{@code typ=B_RT}，{@code sub=sys_user.id, rjti}，默认有效期 8h（b-refresh-expire-seconds）。
 *       仅用于换发 access；Redis 存储 + 轮换 + 重用检测（RefreshTokenService）。</li>
 *   <li>C 端 demo-login 维持单 token（{@code typ=C}），不接入 refresh（Q14：仅 B 端）。</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    public static final String TYP_B = "B";
    public static final String TYP_C = "C";
    /** refresh token 的 typ 声明（区别于 access 的 typ=B）。 */
    public static final String TYP_B_RT = "B_RT";

    @Value("${smartmed.jwt.secret}")
    private String secret;

    @Value("${smartmed.jwt.b-access-expire-seconds}")
    private long bAccessExpireSeconds;

    @Value("${smartmed.jwt.b-refresh-expire-seconds}")
    private long bRefreshExpireSeconds;

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

    /**
     * 签发 B 端 access token。
     *
     * @param userId        sys_user.id
     * @param username      用户名
     * @param role          ADMIN / DOCTOR
     * @param doctorId      医生 ID，ADMIN 传 null
     * @param mustChangePassword 首登改密标志
     * @param jti           access token 唯一 ID（吊销会话时按 refresh_jti 查 Redis，jti 本身用于标识）
     * @param refreshJti    所属会话（refresh token）的 rjti
     * @param absoluteExp   固定会话窗口的绝对截止时刻（epoch ms，登录时刻 + 8h）
     * @param now           签发时刻（epoch ms），用于统一 now/exp 的取值，避免时钟抖动
     */
    public String issueAccessToken(Long userId, String username, String role, Long doctorId,
                                   boolean mustChangePassword, String jti, String refreshJti,
                                   long absoluteExp, long now) {
        long exp = now + bAccessExpireSeconds * 1000;
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("typ", TYP_B)
                .claim("role", role)
                .claim("username", username)
                .claim("doctor_id", doctorId)
                .claim("must_change_password", mustChangePassword)
                .claim("jti", jti)
                .claim("refresh_jti", refreshJti)
                .claim("absolute_exp", absoluteExp)
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(key)
                .compact();
    }

    /**
     * 签发 B 端 refresh token。{@code typ=B_RT}、携带 {@code rjti}，有效期 8h。
     * 存储/轮换/吊销交由 RefreshTokenService（Redis）负责。
     */
    public String issueRefreshToken(Long userId, String rjti, long now) {
        long exp = now + bRefreshExpireSeconds * 1000;
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("typ", TYP_B_RT)
                .claim("rjti", rjti)
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(key)
                .compact();
    }

    /** 生成一个随机的 jti/rjti。 */
    public String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 签发 C 端 token（demo-login，7d）。 */
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

    /** B 端 access 过期秒数（供登录/刷新响应 expiresIn 字段）。 */
    public long getBAccessExpireSeconds() {
        return bAccessExpireSeconds;
    }

    /** B 端 refresh 过期秒数（供前端过期判定）。 */
    public long getBRefreshExpireSeconds() {
        return bRefreshExpireSeconds;
    }

    /** C 端过期秒数（供 demo-login 响应 expiresIn 字段）。 */
    public long getCExpireSeconds() {
        return cExpireSeconds;
    }

    /**
     * 解析并校验 token（access 与 refresh 共用）。
     * 失败返回 null（由调用方决定写 401）。
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
                String jti = c.get("jti", String.class);
                String refreshJti = c.get("refresh_jti", String.class);
                Long absoluteExp = c.get("absolute_exp", Long.class);
                return UserPrincipal.builder()
                        .typ(TYP_B)
                        .userId(userId)
                        .username(username)
                        .role(role)
                        .doctorId(doctorId)
                        .mustChangePassword(mustChange != null && mustChange)
                        .jti(jti)
                        .refreshJti(refreshJti)
                        .absoluteExpiresAt(absoluteExp)
                        .build();
            }
            if (TYP_C.equals(typ)) {
                return UserPrincipal.builder()
                        .typ(TYP_C)
                        .userId(userId)
                        .patientId(userId)
                        .build();
            }
            if (TYP_B_RT.equals(typ)) {
                // refresh token：仅携带 sub + rjti，role 等会话数据由 RefreshTokenService 从 Redis 读
                String rjti = c.get("rjti", String.class);
                return UserPrincipal.builder()
                        .typ(TYP_B_RT)
                        .userId(userId)
                        .rjti(rjti)
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
