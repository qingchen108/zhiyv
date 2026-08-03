package com.smartmed.backend.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * C 端 Agent 对话请求（09 ticket，ADR-0014 无状态全量历史）。
 * <p>
 * 网关只做透传校验（role/content 非空、role 限定 user|assistant），
 * 不解析语义，整包转发 Python Agent 服务。
 *
 * @param messages 全量历史消息（最新一条为本次用户输入）
 */
public record ChatStreamRequest(
        @NotEmpty(message = "messages 不能为空")
        List<@Valid Message> messages) {

    /** 单条消息。 */
    public record Message(
            @NotBlank(message = "role 不能为空")
            @Pattern(regexp = "user|assistant", message = "role 仅支持 user/assistant")
            String role,
            @NotBlank(message = "content 不能为空")
            String content) {
    }
}
