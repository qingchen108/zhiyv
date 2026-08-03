package com.smartmed.backend.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.order.entity.MedicationReminder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用药提醒 Mapper（08 ticket C 端记录查询，14 ticket 提醒设置复用）。
 */
@Mapper
public interface MedicationReminderMapper extends BaseMapper<MedicationReminder> {
}
