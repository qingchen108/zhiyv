package com.smartmed.backend.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用药提醒视图（C 端记录查询，PRD 5.7）。
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReminderVO {

    private Long id;
    private Long prescriptionId;
    private Long drugId;
    /** 药品名称。 */
    private String drugName;
    /** 下次提醒时间。 */
    private OffsetDateTime nextRemindAt;
    /** 用药频率（如"每日 2 次"）。 */
    private String frequency;
    /** 本次用量。 */
    private String dosage;
    /** 备注。 */
    private String remark;
    /** ACTIVE / DONE。 */
    private String status;
}
