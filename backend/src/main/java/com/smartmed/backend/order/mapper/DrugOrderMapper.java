package com.smartmed.backend.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.order.entity.DrugOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购药订单 Mapper（08 ticket C 端记录查询，14 ticket 下单复用）。
 */
@Mapper
public interface DrugOrderMapper extends BaseMapper<DrugOrder> {
}
