package com.smartmed.backend.prescription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 处方模板视图对象。
 * <p>
 * items + advice 由 content JSONB 反序列化得到，前端"使用模板"直接预填开方表单。
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrescriptionTemplateVO {

    private Long id;
    private Long doctorId;
    private String name;
    private String applicableDiagnosis;
    private String advice;
    private List<PrescriptionRequest.ItemRequest> items;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
