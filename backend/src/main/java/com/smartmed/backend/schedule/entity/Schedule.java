package com.smartmed.backend.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 排班（号源主数据，CONTEXT §7，ADR-0009）。
 * <p>
 * status 仅 PUBLISHED / SUSPENDED 两态（创建即发布）。
 * time_period 为枚举班次，start_time/end_time 由后端自动填充。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("schedule")
public class Schedule extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long doctorId;
    private Long departmentId;
    private LocalDate scheduleDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer totalSlots;
    private Integer remainingSlots;
    /** PUBLISHED / SUSPENDED */
    private String status;
    /** 班次枚举：MORNING / AFTERNOON / EVENING（ADR-0009） */
    private String timePeriod;
}
