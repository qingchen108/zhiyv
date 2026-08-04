package com.smartmed.backend.knowledge;

import com.smartmed.backend.knowledge.dto.KnowledgeGraphResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 导诊知识图谱查询服务（ticket 11）。
 * <p>
 * 按症状/疾病关键词子串匹配，经 Symptom → INDICATES → Disease → BELONGS_TO → Department
 * 路径推理，返回可能的疾病与推荐科室，附带可解释理由。
 * <p>
 * Neo4j 不可用时降级返回空列表 + ERROR 日志（与 {@link Neo4jContraindicationService} 一致）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    private final ObjectProvider<Neo4jClient> neo4jClientProvider;

    /**
     * 按关键词查询知识图谱。
     *
     * @param keyword 症状或疾病关键词（子串匹配）
     * @return 匹配结果列表（按 Symptom → Disease → Department 推理），空列表表示无匹配或降级
     */
    public List<KnowledgeGraphResult> query(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        Neo4jClient neo4jClient = neo4jClientProvider.getIfAvailable();
        if (neo4jClient == null) {
            log.warn("Neo4jClient 不可用，知识图谱查询降级返回空列表");
            return List.of();
        }

        try {
            String cypher = """
                    MATCH (s:Symptom)-[:INDICATES]->(d:Disease)-[:BELONGS_TO]->(dept:Department)
                    WHERE s.name CONTAINS $keyword OR d.name CONTAINS $keyword
                    RETURN s.name AS symptom, d.name AS disease,
                           dept.name AS department, dept.desc AS departmentDesc
                    """;
            Collection<Map<String, Object>> rows = neo4jClient.query(cypher)
                    .bindAll(Map.of("keyword", keyword))
                    .fetch()
                    .all();

            List<KnowledgeGraphResult> results = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String symptom = (String) row.get("symptom");
                String disease = (String) row.get("disease");
                String department = (String) row.get("department");
                String departmentDesc = (String) row.get("departmentDesc");
                String reason = String.format(
                        "您的症状（%s）可能与「%s」有关，建议就诊「%s」（%s）",
                        symptom, disease, department,
                        departmentDesc != null ? departmentDesc : "相关科室"
                );
                results.add(KnowledgeGraphResult.builder()
                        .symptom(symptom)
                        .disease(disease)
                        .department(department)
                        .departmentDesc(departmentDesc)
                        .reason(reason)
                        .build());
            }
            return results;
        } catch (Exception e) {
            log.error("Neo4j 知识图谱查询失败，降级返回空列表: keyword={}", keyword, e);
            return List.of();
        }
    }
}