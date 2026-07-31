package com.smartmed.backend.prescription.dto;

import com.smartmed.backend.knowledge.dto.ContraindicationWarning;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 开方响应（处方 + 禁忌警告列表）。
 * <p>
 * warnings 为空表示无冲突或 Neo4j 降级；有冲突不阻断保存（仍 ACTIVE）。
 */
@Data
@Builder
@AllArgsConstructor
public class PrescriptionCreateResponse {

    private PrescriptionVO prescription;
    private List<ContraindicationWarning> warnings;
}
