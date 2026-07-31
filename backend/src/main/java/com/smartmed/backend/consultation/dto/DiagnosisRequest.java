package com.smartmed.backend.consultation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 保存诊断请求（IN_PROGRESS 可改，complete 不强制诊断）。
 */
@Data
public class DiagnosisRequest {

    @NotNull(message = "诊断内容不能为 null（可为空串）")
    private String diagnosis;
}
