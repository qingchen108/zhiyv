package com.smartmed.backend.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 业务实体基类（03 引入，兑现 02 留的口子）。
 * <p>
 * 仅含 {@code createdAt} / {@code updatedAt}，无逻辑删除字段（CONTEXT §2 物理删除）。
 * 字段类型用 {@link OffsetDateTime} 对齐 PG {@code TIMESTAMPTZ}（postgres 驱动不允许 TIMESTAMPTZ 映射 LocalDateTime）。
 * 由 {@link com.smartmed.backend.config.AutoFillMetaObjectHandler} 自动填充：
 * INSERT 填两个，UPDATE 填 updatedAt；DB {@code DEFAULT now()} 兜底。
 * <p>
 * 仅 03 新增实体（Department/Doctor/Drug）继承，不回头改 02 的 SysUser/Patient。
 */
@Data
public abstract class BaseEntity {

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
