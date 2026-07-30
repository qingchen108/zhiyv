package com.smartmed.backend.drug.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** 药品视图对象（响应）。 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DrugVO {

    private Long id;
    private String name;
    private String specification;
    private String manufacturer;
    private BigDecimal price;
    private String dosageForm;
}
