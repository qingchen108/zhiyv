package com.smartmed.backend.drug.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 药品基础信息（PRD 6.7），供处方与禁忌检测使用。
 * 对应 01-schema.sql 第 12 张表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("drug")
public class Drug extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    /** 规格（如 0.3g×20 粒）。 */
    private String specification;
    private String manufacturer;
    private BigDecimal price;
    /** 剂型：片剂/胶囊/注射剂/散剂等。 */
    private String dosageForm;
}
