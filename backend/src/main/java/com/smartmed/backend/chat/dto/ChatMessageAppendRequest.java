package com.smartmed.backend.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量追加消息请求（ticket 10，done 后每轮一次原子保存）。
 * <p>
 * 同一轮 user + tool×N + assistant 一次性提交，一个事务落库；
 * 失败轮次（error/中断）不落库，由前端本地错误气泡兜底。
 *
 * @param messages 本轮消息列表（顺序即落库顺序）
 */
public record ChatMessageAppendRequest(
        @NotEmpty(message = "messages 不能为空")
        List<@Valid Message> messages) {

    /** 单条消息。 */
    public record Message(
            @NotBlank(message = "role 不能为空")
            @Pattern(regexp = "USER|ASSISTANT|TOOL", message = "role 仅支持 USER/ASSISTANT/TOOL")
            String role,
            @NotBlank(message = "content 不能为空")
            @Size(max = 20000, message = "content 过长")
            String content,
            @Size(max = 2000, message = "toolTrace 过长")
            String toolTrace) {
    }
}
