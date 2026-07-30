package com.smartmed.backend.registration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 确认挂号请求（05 ticket）。
 */
@Data
public class RegistrationConfirmRequest {

    /** 排班 ID（必填） */
    @NotNull(message = "排班ID不能为空")
    private Long scheduleId;

    /** 确认令牌（草稿创建时返回，必填） */
    @NotBlank(message = "确认令牌不能为空")
    private String confirmToken;

    /** 家庭成员 ID（选填，须与草稿一致） */
    private Long familyMemberId;
}
