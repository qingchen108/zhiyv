package com.smartmed.backend.drug.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DrugPharmacyStockVO {

    private Long drugId;
    private String drugName;
    private String drugSpecification;
    private Long pharmacyId;
    private String pharmacyName;
    private String pharmacyAddress;
    private BigDecimal price;
    private Integer stock;
    private Integer distanceM;
    private Integer deliveryEtaMin;
}
