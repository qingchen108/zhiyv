package com.smartmed.backend.consultation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 消息视图对象。
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageVO {

    private Long id;
    /** DOCTOR / PATIENT。 */
    private String senderType;
    private String content;
    private OffsetDateTime createdAt;
}
