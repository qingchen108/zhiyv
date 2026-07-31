package com.smartmed.backend.consultation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 问诊消息（医生与患者图文对话）。
 * <p>
 * sender_type 分 DOCTOR / PATIENT；06 仅医生写 DOCTOR 消息、读全部（CONTEXT §10）。
 * 仅 IN_PROGRESS 可发消息。
 */
@Data
@TableName("consultation_message")
public class ConsultationMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long consultationId;
    /** DOCTOR / PATIENT。 */
    private String senderType;
    private String content;
    private OffsetDateTime createdAt;
}
