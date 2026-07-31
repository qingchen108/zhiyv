package com.smartmed.backend.prescription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.prescription.entity.PrescriptionItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 处方明细 Mapper（06 ticket）。
 */
@Mapper
public interface PrescriptionItemMapper extends BaseMapper<PrescriptionItem> {
}
