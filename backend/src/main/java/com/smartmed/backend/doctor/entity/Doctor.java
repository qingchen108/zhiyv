package com.smartmed.backend.doctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 医生（关联科室，PRD 6.3）。
 * <p>
 * phone 不落本表，单源镜像到 sys_user.phone，展示靠 JOIN（ADR-0005）。
 * 年龄由 birthDate 派生计算，不存 age 列（与 patient 口径一致）。
 * 对应 01-schema.sql 第 6 张表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doctor")
public class Doctor extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long departmentId;
    /** 姓名，同步写 sys_user.username（ADR-0005）。 */
    private String name;
    /** 男/女（与 patient.gender 一致）。 */
    private String gender;
    private LocalDate birthDate;
    /** 职称：主任医师/副主任医师/主治医师/住院医师。 */
    private String title;
    private String specialty;
    private String avatarUrl;
    private String intro;
    /** 好评率，百分比，手动录入。 */
    private java.math.BigDecimal goodRate;
}
