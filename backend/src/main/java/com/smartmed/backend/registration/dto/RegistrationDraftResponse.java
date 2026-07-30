package com.smartmed.backend.registration.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 草稿创建响应（05 ticket）。
 */
@Data
@Builder
public class RegistrationDraftResponse {

    /** 草稿 Redis key（供调试，前端不需要） */
    private String draftKey;
    /** 确认令牌（确认时回传） */
    private String confirmToken;
    /** 排班 ID */
    private Long scheduleId;
    /** 医生姓名 */
    private String doctorName;
    /** 科室名称 */
    private String departmentName;
}
