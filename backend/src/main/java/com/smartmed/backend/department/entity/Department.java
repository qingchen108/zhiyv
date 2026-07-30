package com.smartmed.backend.department.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 科室（归属 hospital，03 新增时 hospital_id 硬编码为唯一医院 id=1）。
 * 对应 01-schema.sql 第 5 张表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("department")
public class Department extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属医院 ID（硬编码 1，CONTEXT 术语表"唯一医院"）。 */
    private Long hospitalId;
    private String name;
    private String description;
}
