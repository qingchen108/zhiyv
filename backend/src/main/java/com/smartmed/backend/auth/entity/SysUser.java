package com.smartmed.backend.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * sys_user 系统登录账号（B 端 ADMIN/DOCTOR）。
 * <p>
 * 与 doctor 业务实体分离（CONTEXT §2），patient 不走此表。
 * 对应 01-schema.sql 第 2 张表。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    /** BCrypt 哈希（$2b$10$...），对齐 01 种子数据。 */
    private String passwordHash;
    /** ADMIN / DOCTOR */
    private String role;
    /** 关联 doctor.id（DOCTOR 必填，ADMIN 为 null）。 */
    private Long doctorId;
    /** 1=启用 0=禁用。 */
    private Integer status;
}
