package com.smartmed.backend.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.config.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 对话消息（C 端 AI Agent 对话内容，ticket 10）。
 * <p>
 * role：USER / ASSISTANT / TOOL（CHECK 约束）；TOOL 消息承载工具调用轨迹
 * （tool_trace JSONB，与 Python tool_call 事件一致：{tool, label}），card 事件不入库。
 * 保存责任在前端：每轮 done 后批量原子追加（user + tool×N + assistant）。
 * <p>
 * 注意：本表仅有 created_at（无 updated_at 列），故不继承 {@link com.smartmed.backend.base.BaseEntity}。
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 ID（外键 -> chat_session.id）。 */
    private Long sessionId;

    /** USER / ASSISTANT / TOOL。 */
    private String role;

    /** 消息内容。 */
    private String content;

    /** 工具调用轨迹 JSON 字符串（{tool, label}，JsonbTypeHandler 写 jsonb 列）。 */
    @TableField(value = "tool_trace", typeHandler = JsonbTypeHandler.class)
    private String toolTrace;

    /** 创建时间（UTC，带时区），INSERT 自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
