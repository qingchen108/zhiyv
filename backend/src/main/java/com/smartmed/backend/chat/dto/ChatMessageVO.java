package com.smartmed.backend.chat.dto;

import java.time.OffsetDateTime;

/**
 * 对话消息视图（ticket 10）。
 *
 * @param id        消息 ID
 * @param role      USER / ASSISTANT / TOOL
 * @param content   消息内容
 * @param toolTrace 工具调用轨迹（TOOL 消息，JSON 字符串；非 TOOL 为 null）
 * @param createdAt 创建时间（created_at 升序渲染历史）
 */
public record ChatMessageVO(Long id, String role, String content, String toolTrace, OffsetDateTime createdAt) {
}
