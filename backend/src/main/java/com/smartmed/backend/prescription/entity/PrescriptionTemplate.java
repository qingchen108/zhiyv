package com.smartmed.backend.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.base.BaseEntity;
import com.smartmed.backend.config.JsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 处方模板（医生个人模板，PRD 6.8）。
 * <p>
 * content 为 JSONB，结构 {@code {"items":[{drugId,usageMethod,dosage,frequency,remark}],"advice":"..."}}，
 * 与开方 items 同构（CONTEXT §10），开方时前端预填来源。
 * 归属 doctor_id 限本人，跨医生 403。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "prescription_template", autoResultMap = true)
public class PrescriptionTemplate extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long doctorId;
    private String name;
    private String applicableDiagnosis;
    /** JSONB 字符串（JsonbTypeHandler 用 PGobject 包装写入 jsonb 列，ADR-0012 配套）。 */
    @TableField(value = "content", typeHandler = JsonbTypeHandler.class)
    private String content;
}
