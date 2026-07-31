package com.smartmed.backend.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 禁忌警告项（开方时 Neo4j 检测返回）。
 * <p>
 * type=ALLERGY 表示过敏冲突（药品含过敏原命中就诊人过敏史）；
 * type=INTERACTION 表示药物相互作用（处方内两药 INTERACTS_WITH）。
 */
@Data
@Builder
@AllArgsConstructor
public class ContraindicationWarning {

    /** ALLERGY / INTERACTION。 */
    private String type;
    /** 触发警告的药品名。 */
    private String drugName;
    /** ALLERGY 时为过敏原名；INTERACTION 时为冲突的另一个药品名。 */
    private String targetName;
    /** 说明文本。 */
    private String description;
}
