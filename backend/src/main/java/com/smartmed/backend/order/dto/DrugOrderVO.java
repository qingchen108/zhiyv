package com.smartmed.backend.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 购药订单视图（C 端记录查询，PRD 5.5）。
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DrugOrderVO {

    private Long id;
    private Long patientId;
    private Long prescriptionId;
    private Long pharmacyId;
    /** 药店名称。 */
    private String pharmacyName;
    /** 药店地址。 */
    private String pharmacyAddress;
    /** 订单总金额。 */
    private BigDecimal totalAmount;
    /** PENDING / PAID / DELIVERING / COMPLETED / CANCELLED。 */
    private String status;
    /** 配送信息。 */
    private String deliveryInfo;
    private OffsetDateTime createdAt;
}
