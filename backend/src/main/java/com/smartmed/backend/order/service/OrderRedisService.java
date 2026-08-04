package com.smartmed.backend.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 购药 Redis 服务（14 ticket）：
 * 草稿管理 + SHA-256 confirmToken。
 * <p>
 * key 设计：
 * <ul>
 *   <li>草稿：{@code order_draft:{patientId}:{prescriptionId}} TTL 30min</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRedisService {

    private static final String DRAFT_PREFIX = "order_draft:";
    private static final Duration DRAFT_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    /** 构建草稿 key。 */
    public String buildDraftKey(Long patientId, Long prescriptionId) {
        return DRAFT_PREFIX + patientId + ":" + prescriptionId;
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

    /** 生成 SHA-256 confirmToken。 */
    public String generateConfirmToken(Long patientId, Long prescriptionId, Long pharmacyId,
                                       long createdAtMillis, String secret) {
        String raw = patientId + ":" + prescriptionId + ":" + pharmacyId + ":" + createdAtMillis + ":" + secret;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
