package com.smartmed.backend.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 开方请求（06 ticket，CONTEXT §10）。
 * <p>
 * items 至少 1 条；diagnosis 选填；force 默认 false（仅审计标记，不影响保存）。
 */
@Data
public class PrescriptionRequest {

    @NotNull(message = "问诊 ID 不能为空")
    private Long consultationId;

    /** 处方级诊断（与问诊级诊断独立）。 */
    private String diagnosis;

    /** 医嘱。 */
    private String advice;

    /** 强制保存标记：true 表示医生已确认知晓禁忌警告强制开方（仅审计日志，不影响保存）。 */
    private Boolean force;

    @NotEmpty(message = "处方明细至少一条")
    @Valid
    private List<ItemRequest> items;

    @Data
    public static class ItemRequest {
        @NotNull(message = "药品 ID 不能为空")
        private Long drugId;
        /** 用法：口服/外用/注射。 */
        private String usageMethod;
        /** 每次用量。 */
        private String dosage;
        /** 用药频率，如每日 2 次。 */
        private String frequency;
        private String remark;
    }
}
