package com.smartmed.backend.consultation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 问诊（关联挂号，PRD 6.6）。
 * <p>
 * 确认挂号时自动创建（ADR-0011），status 三态单向流转：WAITING -> IN_PROGRESS -> COMPLETED。
 * pre_diagnosis 由 Agent（ticket 13）填充，06 阶段为 null。
 * diagnosis 为医生填写的问诊级诊断，与处方级诊断独立。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("consultation")
public class Consultation extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long registrationId;
    private Long patientId;
    private Long doctorId;
    /** AI 预问诊摘要（标注"AI 生成仅供参考"，06 阶段为 null，13 填充）。 */
    private String preDiagnosis;
    /** 医生诊断（问诊级）。 */
    private String diagnosis;
    /** WAITING / IN_PROGRESS / COMPLETED（单向不可回退，ADR-0011）。 */
    private String status;
}
