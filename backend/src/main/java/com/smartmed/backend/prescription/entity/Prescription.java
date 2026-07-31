package com.smartmed.backend.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 处方（关联问诊，保存即生效无需药师审核，PRD 6.6.3）。
 * <p>
 * status ACTIVE / REVOKED；06 不实现撤销（YAGNI），开方即 ACTIVE。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prescription")
public class Prescription extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long consultationId;
    private Long patientId;
    private Long doctorId;
    /** 处方级诊断（与 consultation.diagnosis 问诊级诊断独立）。 */
    private String diagnosis;
    /** 医嘱。 */
    private String advice;
    /** ACTIVE / REVOKED。 */
    private String status;
}
