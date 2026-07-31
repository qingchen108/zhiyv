package com.smartmed.backend.security;

import com.smartmed.backend.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * B 端 refresh token 会话存储（ADR-0013）。
 * <p>
 * 基于 Redis 的两个 key（前缀 smartmed:，与 RegistrationRedisService 一致）：
 * <ul>
 *   <li>{@code smartmed:session:{rjti}} —— 会话本体，value 为 {@code userId:role:doctorId:mustChange:absoluteExp}，
 *       TTL = refresh 过期时长（8h）。存在即表示该会话有效。</li>
 *   <li>{@code smartmed:sessions:{userId}} —— 用户活跃会话的 rjti 集合（SET），TTL 8h，
 *       用于「改密后吊销全部会话」（Q15）。</li>
 * </ul>
 * <p>
 * 换发（rotate）流程（Q10 轮换 + 重用检测）：
 * <ol>
 *   <li>校验旧 refresh：签名有效、typ=B_RT、rjti 存在（Redis 会话 key 在）→ 通过。</li>
 *   <li>{@code delete(oldRjti)} 为 true → 旧 token 本次才被消费，换发新 refresh + 新 access；
 *       {@code delete} 为 false → 旧 token 被重放 → 判定泄露 → 吊销该用户全部会话并抛 401（重用检测）。</li>
 * </ol>
 * 并发续期安全：{@code delete} 的原子性保证同一 rjti 只能被消费一次；真正的轮换在 delete 之后。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider tokenProvider;

    /** Redis key 前缀（与 RegistrationRedisService 一致）。 */
    private static final String KEY_PREFIX = "smartmed:";
    /** 会话 TTL：固定 8h 窗口，与 refresh token 的 exp 对齐（Q12）。 */
    private static final Duration SESSION_TTL = Duration.ofHours(8);

    /** 会话 key：smartmed:session:{rjti}。 */
    private String sessionKey(String rjti) {
        return KEY_PREFIX + "session:" + rjti;
    }

    /** 用户活跃会话集合 key：smartmed:sessions:{userId}。 */
    private String userSessionsKey(Long userId) {
        return KEY_PREFIX + "sessions:" + userId;
    }

    /**
     * 创建会话（登录时）。存会话 + 登记到用户集合。
     * 返回会话 rjti。
     */
    public String createSession(Long userId, String role, Long doctorId, boolean mustChangePassword, long absoluteExp) {
        String rjti = tokenProvider.newId();
        String value = sessionValue(userId, role, doctorId, mustChangePassword, absoluteExp);
        try {
            redisTemplate.opsForValue().set(sessionKey(rjti), value, SESSION_TTL);
            redisTemplate.opsForSet().add(userSessionsKey(userId), rjti);
            redisTemplate.expire(userSessionsKey(userId), SESSION_TTL);
        } catch (DataAccessException e) {
            log.error("创建会话失败 Redis 不可用: {}", e.getMessage());
            throw new BusinessException(500, "认证服务暂不可用，请稍后再试");
        }
        return rjti;
    }

    /** 序列化会话 value：userId:role:doctorId:mustChange(1/0):absoluteExp。 */
    private String sessionValue(Long userId, String role, Long doctorId, boolean mustChangePassword, long absoluteExp) {
        return userId + ":" + role + ":" + (doctorId == null ? "-" : doctorId)
                + ":" + (mustChangePassword ? "1" : "0") + ":" + absoluteExp;
    }

    /** 吊销单个会话（logout 等）。key 不存在时静默成功。 */
    public void revoke(String rjti) {
        if (rjti == null) {
            return;
        }
        try {
            redisTemplate.delete(sessionKey(rjti));
        } catch (DataAccessException e) {
            log.error("吊销会话失败 Redis 不可用: {}", e.getMessage());
        }
    }

    /**
     * 校验 access token 所属会话是否仍有效（ADR-0013）。
     * <p>
     * 每个受保护请求在 JwtAuthFilter 调用一次，支持实时吊销：
     * logout / 改密 / 重用检测后，对应 access token 即刻失效（而非等 30min 自然过期）。
     * Redis 不可用时 fail-open（放行），避免基础设施故障阻断现有会话。
     */
    public boolean isSessionActive(String rjti) {
        if (rjti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(rjti)));
        } catch (DataAccessException e) {
            log.error("会话校验失败 Redis 不可用，fail-open: {}", e.getMessage());
            return true;
        }
    }

    /** 吊销某用户全部会话（改密后 Q15）。 */
    public void revokeAllByUser(Long userId) {
        try {
            String key = userSessionsKey(userId);
            for (String rjti : redisTemplate.opsForSet().members(key)) {
                redisTemplate.delete(sessionKey(rjti));
            }
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.error("吊销用户会话失败 Redis 不可用: {}", e.getMessage());
        }
    }

    /**
     * 轮换（换发）：校验 + 消费旧 refresh，返回新会话数据。
     * <p>
     * 轮换分两步：
     * <ol>
     *   <li><b>校验</b>（不消费）：签名（调用方已 parse）、rjti 在 Redis 且属于该用户、未过固定窗口。</li>
     *   <li><b>消费</b>：{@code delete} 旧会话 key。返回 1 = 首次消费，换发；返回 0 = 重放，吊销整条用户会话。</li>
     * </ol>
     * 注意消费前不 delete 旧 key——保证并发下先拿到旧会话的请求能拿到会话数据；
     * 消费（delete）的原子性保证同一 rjti 只会有一个请求进入「换发」分支。
     */
    public RotateResult rotate(Long userId, String rjti, long now) {
        String value;
        try {
            value = redisTemplate.opsForValue().get(sessionKey(rjti));
        } catch (DataAccessException e) {
            log.error("轮换校验失败 Redis 不可用: {}", e.getMessage());
            throw new BusinessException(500, "认证服务暂不可用，请稍后再试");
        }
        if (value == null) {
            log.info("会话不存在或已吊销: rjti={}", rjti);
            throw new BusinessException(401, "未登录或登录已过期");
        }
        String[] parts = value.split(":", -1);
        if (parts.length != 5 || !parts[0].equals(String.valueOf(userId))) {
            log.warn("会话数据异常: rjti={}", rjti);
            throw new BusinessException(401, "未登录或登录已过期");
        }
        String role = parts[1];
        Long doctorId = "-".equals(parts[2]) ? null : Long.valueOf(parts[2]);
        boolean mustChangePassword = "1".equals(parts[3]);
        long absoluteExp;
        try {
            absoluteExp = Long.parseLong(parts[4]);
        } catch (NumberFormatException e) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        // 固定窗口（Q12）：绝对截止时刻已过 -> 拒绝续期，强制重新登录
        if (now >= absoluteExp) {
            log.info("固定会话窗口已过期，强制重新登录: userId={}", userId);
            revoke(rjti);
            throw new BusinessException(401, "登录已超过 8 小时，请重新登录");
        }

        // 消费旧 rjti（轮换核心）：true = 首次消费；false = 已被消费过（重用检测）
        Boolean consumed;
        try {
            consumed = redisTemplate.delete(sessionKey(rjti));
        } catch (DataAccessException e) {
            log.error("轮换消费失败 Redis 不可用: {}", e.getMessage());
            throw new BusinessException(500, "认证服务暂不可用，请稍后再试");
        }
        if (consumed == null || !consumed) {
            // 重用检测（Q10）：同一 rjti 被再次换发 -> 疑似泄露，吊销该用户全部会话
            log.warn("检测到 refresh token 重用，吊销用户全部会话: userId={}", userId);
            revokeAllByUser(userId);
            throw new BusinessException(401, "检测到异常登录，请重新登录");
        }

        String newRjti = tokenProvider.newId();
        String newValue = sessionValue(userId, role, doctorId, mustChangePassword, absoluteExp);
        redisTemplate.opsForValue().set(sessionKey(newRjti), newValue, SESSION_TTL);
        redisTemplate.opsForSet().add(userSessionsKey(userId), newRjti);
        redisTemplate.expire(userSessionsKey(userId), SESSION_TTL);

        return RotateResult.builder()
                .newRjti(newRjti)
                .role(role)
                .doctorId(doctorId)
                .mustChangePassword(mustChangePassword)
                .absoluteExp(absoluteExp)
                .build();
    }

    /** 轮换结果：新会话的 rjti 与会话元数据。 */
    @lombok.Builder
    @lombok.Getter
    public static class RotateResult {
        private final String newRjti;
        private final String role;
        private final Long doctorId;
        private final boolean mustChangePassword;
        private final long absoluteExp;
    }
}
