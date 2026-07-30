package com.smartmed.backend.registration.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建挂号草稿请求（05 ticket）。
 */
@Data
public class RegistrationDraftRequest {

    /** 排班 ID（必填） */
    @NotNull(message = "排班ID不能为空")
    private Long scheduleId;

    /** 家庭成员 ID（选填，NULL=本人就诊） */
    private Long familyMemberId;
}
