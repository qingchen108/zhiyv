package com.smartmed.backend.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 处方明细（药品 + 用法用量）。
 * <p>
 * 字段结构与处方模板 content.items 同构，开方时模板可直接预填（CONTEXT §10）。
 */
@Data
@TableName("prescription_item")
public class PrescriptionItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long prescriptionId;
    private Long drugId;
    /** 用法：口服 / 外用 / 注射。 */
    private String usageMethod;
    /** 每次用量。 */
    private String dosage;
    /** 用药频率，如每日 2 次。 */
    private String frequency;
    private String remark;
}
