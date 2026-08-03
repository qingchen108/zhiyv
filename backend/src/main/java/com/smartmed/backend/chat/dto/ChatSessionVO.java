package com.smartmed.backend.chat.dto;

import java.time.OffsetDateTime;

/**
 * 会话视图（ticket 10）。
 *
 * @param id        会话 ID
 * @param title     会话标题
 * @param updatedAt 更新时间（updated_at 倒序用于会话列表排序展示）
 */
public record ChatSessionVO(Long id, String title, OffsetDateTime updatedAt) {
}
