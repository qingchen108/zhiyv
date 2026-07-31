package com.smartmed.backend.consultation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.consultation.entity.ConsultationMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问诊消息 Mapper（06 ticket）。
 */
@Mapper
public interface ConsultationMessageMapper extends BaseMapper<ConsultationMessage> {
}
