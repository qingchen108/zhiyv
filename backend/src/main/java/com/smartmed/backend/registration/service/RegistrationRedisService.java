package com.smartmed.backend.registration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * 挂号 Redis 服务（05 ticket）：草稿管理 + Lua 原子扣减 + 防刷限流。
 * <p>
 * key 设计（CONTEXT §7）：
 * <ul>
 *   <li>草稿：{@code reg_draft:{operatorId}:{visitorId}:{scheduleId}} TTL 30min</li>
 *   <li>防刷：{@code reg_ratelimit:{visitorId}:{scheduleId}} TTL 5s</li>
 *   <li>号源：{@code schedule:{id}:remaining_slots}（复用 04 已有 key）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationRedisService {

    private static final String DRAFT_PREFIX = "reg_draft:";
    private static final String RATELIMIT_PREFIX = "reg_ratelimit:";
    private static final String SLOT_KEY_PREFIX = "schedule:";
    private static final String SLOT_KEY_SUFFIX = ":remaining_slots";
    private static final Duration DRAFT_TTL = Duration.ofMinutes(30);
    private static final Duration RATELIMIT_TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;

    /**
     * Lua 原子扣减脚本。
     * KEYS[1] = schedule:{id}:remaining_slots
     * 返回：1=成功, -1=号源不足, -2=key不存在（停诊/删除）
     */
    private static final String DEDUCT_LUA = """
            local val = redis.call('GET', KEYS[1])
            if val == false then
                return -2
            end
            if tonumber(val) <= 0 then
                return -1
            end
            redis.call('DECR', KEYS[1])
            return 1
            """;

    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT;

    static {
        DEDUCT_SCRIPT = new DefaultRedisScript<>();
        DEDUCT_SCRIPT.setScriptText(DEDUCT_LUA);
        DEDUCT_SCRIPT.setResultType(Long.class);
    }

    // ==================== 草稿 ====================

    /** 构建草稿 key。 */
    public String buildDraftKey(Long operatorId, String visitorId, Long scheduleId) {
        return DRAFT_PREFIX + operatorId + ":" + visitorId + ":" + scheduleId;
    }

    /** 构建 visitorId 标识。 */
    public String buildVisitorId(Long familyMemberId) {
        return familyMemberId == null ? "self" : "fm:" + familyMemberId;
    }

    /** 写入草稿（JSON value + TTL 30min）。 */
    public void saveDraft(String key, String jsonValue) {
        redisTemplate.opsForValue().set(key, jsonValue, DRAFT_TTL);
    }

    /** 读取草稿，不存在返回 null。 */
    public String getDraft(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /** 删除草稿（消费后不可重复使用）。 */
    public void deleteDraft(String key) {
        redisTemplate.delete(key);
    }

    // ==================== 确认令牌 ====================

    /** 生成 SHA-256 确认令牌。 */
    public String generateConfirmToken(Long patientId, Long scheduleId, long createdAtMillis, String secret) {
        String raw = patientId + ":" + scheduleId + ":" + createdAtMillis + ":" + secret;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ==================== 防刷 ====================

    /** 构建防刷 key。 */
    private String buildRateLimitKey(Long operatorId, String visitorId, Long scheduleId) {
        return RATELIMIT_PREFIX + operatorId + ":" + visitorId + ":" + scheduleId;
    }

    /**
     * 尝试设置防刷标记（SETNX + TTL 5s）。
     *
     * @return true=通过（未触发限流），false=被拦截（5秒内重复）
     */
    public boolean tryAcquireRateLimit(Long operatorId, String visitorId, Long scheduleId) {
        String key = buildRateLimitKey(operatorId, visitorId, scheduleId);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", RATELIMIT_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    // ==================== 号源扣减 ====================

    /**
     * Lua 原子扣减号源。
     *
     * @return 1=成功, -1=号源不足, -2=key不存在（停诊/删除）
     */
    public long deductSlot(Long scheduleId) {
        String key = SLOT_KEY_PREFIX + scheduleId + SLOT_KEY_SUFFIX;
        Long result = redisTemplate.execute(DEDUCT_SCRIPT, List.of(key));
        return result != null ? result : -2;
    }

    /** 补偿回滚（PG 写入失败时 INCR 回 Redis）。 */
    public void compensateSlot(Long scheduleId) {
        String key = SLOT_KEY_PREFIX + scheduleId + SLOT_KEY_SUFFIX;
        try {
            redisTemplate.opsForValue().increment(key);
            log.info("号源补偿 INCR 成功 key={}", key);
        } catch (Exception e) {
            log.error("号源补偿 INCR 失败 key={}", key, e);
        }
    }

    /** 取消挂号释放号源（INCR）。 */
    public void releaseSlot(Long scheduleId) {
        String key = SLOT_KEY_PREFIX + scheduleId + SLOT_KEY_SUFFIX;
        try {
            redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.error("取消释放 INCR 失败 key={}", key, e);
        }
    }

    /** 查询 Redis 中剩余号源（草稿乐观校验用），key 不存在返回 -1。 */
    public int getRemainingSlots(Long scheduleId) {
        String key = SLOT_KEY_PREFIX + scheduleId + SLOT_KEY_SUFFIX;
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Integer.parseInt(val) : -1;
    }
}
