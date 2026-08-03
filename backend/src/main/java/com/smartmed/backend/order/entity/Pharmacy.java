package com.smartmed.backend.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 药店信息（PRD 5.2.6 购药对比，08 ticket C 端订单展示）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pharmacy")
public class Pharmacy extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 药店名称。 */
    private String name;
    /** 药店地址。 */
    private String address;
    /** 联系电话。 */
    private String phone;
    /** 纬度（用于距离计算）。 */
    private BigDecimal latitude;
    /** 经度（用于距离计算）。 */
    private BigDecimal longitude;
}
