package com.smartmed.backend.schedule.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 排班号源 Redis 同步服务（ADR-0009）。
 * <p>
 * key 格式：{@code schedule:{id}:remaining_slots}
 * <ul>
 *   <li>创建/恢复 → SET key = remaining</li>
 *   <li>停诊/删除 → DEL key</li>
 *   <li>调整余量 → SET key = newRemaining</li>
 * </ul>
 * 写入失败走 @Async 重试一次 + ERROR 日志（已有容错策略）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleRedisService {

    private static final String KEY_PREFIX = "schedule:";
    private static final String KEY_SUFFIX = ":remaining_slots";

    private final StringRedisTemplate redisTemplate;

    /** 同步写入 Redis（创建/恢复/调整时调用）。 */
    public void syncSlots(Long scheduleId, int remainingSlots) {
        String key = buildKey(scheduleId);
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(remainingSlots));
        } catch (Exception e) {
            log.error("Redis SET 失败 key={}, 触发异步重试", key, e);
            asyncRetrySet(key, remainingSlots);
        }
    }

    /** 删除 Redis key（停诊/删除时调用）。 */
    public void removeSlots(Long scheduleId) {
        String key = buildKey(scheduleId);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis DEL 失败 key={}, 触发异步重试", key, e);
            asyncRetryDel(key);
        }
    }

    @Async
    public void asyncRetrySet(String key, int value) {
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(value));
            log.info("Redis 异步重试 SET 成功 key={}", key);
        } catch (Exception e) {
            log.error("Redis 异步重试 SET 仍失败 key={}", key, e);
        }
    }

    @Async
    public void asyncRetryDel(String key) {
        try {
            redisTemplate.delete(key);
            log.info("Redis 异步重试 DEL 成功 key={}", key);
        } catch (Exception e) {
            log.error("Redis 异步重试 DEL 仍失败 key={}", key, e);
        }
    }

    private String buildKey(Long scheduleId) {
        return KEY_PREFIX + scheduleId + KEY_SUFFIX;
    }
}
