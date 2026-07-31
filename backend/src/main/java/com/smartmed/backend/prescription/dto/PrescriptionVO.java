package com.smartmed.backend.prescription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 处方视图对象（详情 + 病历聚合共用）。
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrescriptionVO {

    private Long id;
    private Long consultationId;
    private Long patientId;
    private Long doctorId;
    private String diagnosis;
    private String advice;
    /** ACTIVE / REVOKED。 */
    private String status;
    private List<ItemVO> items;
    private OffsetDateTime createdAt;

    @Data
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ItemVO {
        private Long id;
        private Long drugId;
        /** 药品名（详情展示用）。 */
        private String drugName;
        private String usageMethod;
        private String dosage;
        private String frequency;
        private String remark;
    }
}
