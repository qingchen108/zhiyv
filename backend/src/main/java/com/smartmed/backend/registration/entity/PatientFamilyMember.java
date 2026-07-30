package com.smartmed.backend.registration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 家庭成员档案（PRD 5.6，05 ticket 引入实体）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("patient_family_member")
public class PatientFamilyMember extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属患者 ID */
    private Long patientId;
    private String name;
    /** 与患者关系：本人/父母/子女/配偶/其他亲人/朋友 */
    private String relationship;
    private String phone;
    private String gender;
    private LocalDate birthDate;
    private String allergyHistory;
}
