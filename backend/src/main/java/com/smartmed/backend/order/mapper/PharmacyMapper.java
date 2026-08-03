package com.smartmed.backend.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.order.entity.Pharmacy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 药店 Mapper（08 ticket C 端订单展示，14 ticket 药店推荐复用）。
 */
@Mapper
public interface PharmacyMapper extends BaseMapper<Pharmacy> {
}
