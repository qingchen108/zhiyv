package com.smartmed.backend.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * 用药提醒（PRD 5.7，购药后按处方频次生成提醒计划）。
 * <p>
 * status 两态：ACTIVE（生效）/ DONE（已完成）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("medication_reminder")
public class MedicationReminder extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 患者 ID。 */
    private Long patientId;
    /** 关联处方 ID。 */
    private Long prescriptionId;
    /** 药品 ID。 */
    private Long drugId;
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
