package com.smartmed.backend.consultation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送问诊消息请求（仅 DOCTOR，仅 IN_PROGRESS）。
 */
@Data
public class MessageRequest {

    @NotBlank(message = "消息内容不能为空")
    private String content;
}
