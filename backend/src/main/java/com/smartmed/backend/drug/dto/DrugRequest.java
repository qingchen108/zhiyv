package com.smartmed.backend.drug.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 药品新增/编辑请求。 */
@Data
public class DrugRequest {

    @NotBlank(message = "药品名称不能为空")
    private String name;

    private String specification;
    private String manufacturer;

    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    private String dosageForm;
}
