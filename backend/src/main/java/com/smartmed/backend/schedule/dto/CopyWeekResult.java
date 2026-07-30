package com.smartmed.backend.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 周复制结果。
 */
@Data
@AllArgsConstructor
public class CopyWeekResult {

    /** 跳过条数（目标周已存在） */
    private int skipped;
    /** 新建条数 */
    private int created;
}
