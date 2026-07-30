package com.smartmed.backend.registration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 挂号记录（05 ticket，ADR-0010）。
 * <p>
 * patient_id = 操作人（登录患者），family_member_id = 实际就诊人（NULL 表示本人）。
 * status 三态：REGISTERED / VISITED / CANCELLED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("registration")
public class Registration extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人（登录患者 JWT 身份） */
    private Long patientId;
    /** 排班 ID */
    private Long scheduleId;
    /** 医生 ID（冗余，方便查询） */
    private Long doctorId;
    /** 实际就诊人（家庭成员 ID，NULL=本人），ADR-0010 */
    private Long familyMemberId;
    /** 挂号单号 REG+yyyyMMdd+seq */
    private String regNo;
    /** REGISTERED / VISITED / CANCELLED */
    private String status;
}
