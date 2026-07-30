package com.smartmed.backend.schedule.entity;

import lombok.Getter;

import java.time.LocalTime;

/**
 * 班次枚举（ADR-0009）。
 * <p>
 * 后端根据枚举自动填充 start_time / end_time，前端只需选枚举值。
 */
@Getter
public enum TimePeriod {

    MORNING(LocalTime.of(8, 0), LocalTime.of(12, 0)),
    AFTERNOON(LocalTime.of(14, 0), LocalTime.of(17, 30)),
    EVENING(LocalTime.of(18, 0), LocalTime.of(21, 0));

    private final LocalTime startTime;
    private final LocalTime endTime;

    TimePeriod(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
