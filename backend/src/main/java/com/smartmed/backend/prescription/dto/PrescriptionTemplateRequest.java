package com.smartmed.backend.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 处方模板新建/编辑请求。
 * <p>
 * content 结构与开方 items 同构（CONTEXT §10）：{@code {items:[...], advice:"..."}}。
 */
@Data
public class PrescriptionTemplateRequest {

    @NotBlank(message = "模板名称不能为空")
    private String name;

    /** 适用诊断。 */
    private String applicableDiagnosis;

    /** 医嘱。 */
    private String advice;

    @NotEmpty(message = "模板明细至少一条")
    @Valid
    private List<PrescriptionRequest.ItemRequest> items;
}
