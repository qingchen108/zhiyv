package com.smartmed.backend.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmed.backend.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 购药订单（PRD 5.2.6，购药草稿存 Redis 不落此表，确认后才生成订单行）。
 * <p>
 * status 五态：PENDING / PAID / DELIVERING / COMPLETED / CANCELLED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("drug_order")
public class DrugOrder extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 购药患者 ID（操作人）。 */
    private Long patientId;
    /** 关联处方 ID。 */
    private Long prescriptionId;
    /** 购药药店 ID。 */
    private Long pharmacyId;
    /** 订单总金额。 */
    private BigDecimal totalAmount;
    /** PENDING / PAID / DELIVERING / COMPLETED / CANCELLED。 */
    private String status;
    /** 配送信息。 */
    private String deliveryInfo;
}
