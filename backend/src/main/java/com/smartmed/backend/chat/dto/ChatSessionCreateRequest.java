package com.smartmed.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建会话请求（ticket 10）。
 *
 * @param title 会话标题（首条消息截断 ≤20 字，前端负责截断）
 */
public record ChatSessionCreateRequest(
        @NotBlank(message = "title 不能为空")
        @Size(max = 128, message = "title 最长 128 字")
        String title) {
}
