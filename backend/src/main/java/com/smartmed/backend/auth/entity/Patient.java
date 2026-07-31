package com.smartmed.backend.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * patient 患者档案（C 端）。
 * <p>
 * 无密码列，demo-login 免密按预设 patient.id 签 JWT（CONTEXT §2）。
 * 对应 01-schema.sql 第 3 张表。
 */
@Data
@TableName("patient")
public class Patient {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String phone;
    private String gender;
    private LocalDate birthDate;
    private String allergyHistory;
}
