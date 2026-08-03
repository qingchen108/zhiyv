package com.smartmed.backend.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话会话（C 端 AI Agent 对话，PRD 5.2，ticket 10）。
 * <p>
 * 首条消息时创建（title=首条消息截断 ≤20 字，前端负责截断），按操作人隔离。
 * 物理删除时级联删除全部 chat_message（CONTEXT §8）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_session")
public class ChatSession extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起会话的患者 ID（外键 -> patient.id）。 */
    private Long patientId;

    /** 会话标题（首条消息前端截断 ≤20 字）。 */
    private String title;
}
