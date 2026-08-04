package com.smartmed.backend.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 知识图谱查询结果（ticket 11 导诊工具）。
 * <p>
 * 由症状关键词匹配，经 Symptom → Disease → Department 路径推理，
 * 返回可能的疾病与推荐科室，附带可解释理由。
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgeGraphResult {

    /** 匹配的症状名（如"头痛"）。 */
    private String symptom;

    /** 可能的疾病名（如"上呼吸道感染"）。 */
    private String disease;

    /** 推荐科室名（如"呼吸内科"）。 */
    private String department;

    /** 科室描述（如"诊治呼吸道及肺部疾病"）。 */
    private String departmentDesc;

    /** 推荐理由（如"您的症状（头痛）可能与上呼吸道感染有关，建议就诊呼吸内科"）。 */
    private String reason;
}