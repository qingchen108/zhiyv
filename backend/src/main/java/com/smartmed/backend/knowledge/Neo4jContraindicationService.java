package com.smartmed.backend.knowledge;

import com.smartmed.backend.knowledge.dto.ContraindicationWarning;
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
 * 禁忌检测服务（ADR-0012，06 ticket）。
 * <p>
 * 直接 Cypher 查询（{@link Neo4jClient#query}），不建 SDN 实体/repository。
 * 检测两类冲突：
 * <ol>
 *   <li>过敏冲突：处方药品 -[:CONTAINS|CONTRAINDICATED_IN]-> Allergen，比对就诊人过敏史文本子串。</li>
 *   <li>药物相互作用：处方内药品两两 -[:INTERACTS_WITH]->。</li>
 * </ol>
 * 不做疾病-药品禁忌（图谱无对应关系）。
 * <p>
 * 用 {@link ObjectProvider} 注入 Neo4jClient：Neo4j 未配置或不可用时（如部分集成测试排除 Neo4j），
 * 降级返回空 warnings + ERROR 日志，不阻断开方。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jContraindicationService {

    private final ObjectProvider<Neo4jClient> neo4jClientProvider;

    /**
     * 检测处方药品的禁忌冲突。
     *
     * @param drugNames      处方内药品名列表（中文，与 Neo4j Drug 节点名对齐）
     * @param allergyHistory 实际就诊人过敏史文本（NULL/空则跳过过敏比对，仅做相互作用）
     * @return 警告列表（空列表表示无冲突或降级）
     */
    public List<ContraindicationWarning> detect(List<String> drugNames, String allergyHistory) {
        List<ContraindicationWarning> warnings = new ArrayList<>();
        if (drugNames == null || drugNames.isEmpty()) {
            return warnings;
        }

        Neo4jClient neo4jClient = neo4jClientProvider.getIfAvailable();
        if (neo4jClient == null) {
            log.warn("Neo4jClient 不可用，禁忌检测降级返回空列表");
            return warnings;
        }

        // 1. 过敏冲突（过敏史非空才比对）
        if (allergyHistory != null && !allergyHistory.isBlank()) {
            warnings.addAll(detectAllergy(neo4jClient, drugNames, allergyHistory));
        }

        // 2. 药物相互作用（处方内两两）
        warnings.addAll(detectInteraction(neo4jClient, drugNames));

        return warnings;
    }

    /** 过敏冲突：查药品含的过敏原，子串匹配过敏史文本。 */
    private List<ContraindicationWarning> detectAllergy(Neo4jClient neo4jClient, List<String> drugNames, String allergyHistory) {
        try {
            String cypher = """
                    MATCH (d:Drug)-[:CONTAINS|CONTRAINDICATED_IN]->(a:Allergen)
                    WHERE d.name IN $drugNames
                    RETURN d.name AS drugName, a.name AS allergenName
                    """;
            Collection<Map<String, Object>> rows = neo4jClient.query(cypher)
                    .bindAll(Map.of("drugNames", drugNames))
                    .fetch()
                    .all();
            List<ContraindicationWarning> warnings = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String drugName = (String) row.get("drugName");
                String allergenName = (String) row.get("allergenName");
                // 子串匹配：过敏史文本含过敏原名即命中
                if (allergenName != null && allergyHistory.contains(allergenName)) {
                    warnings.add(ContraindicationWarning.builder()
                            .type("ALLERGY")
                            .drugName(drugName)
                            .targetName(allergenName)
                            .description("药品「" + drugName + "」含过敏原「" + allergenName + "」，与患者过敏史冲突")
                            .build());
                }
            }
            return warnings;
        } catch (Exception e) {
            log.error("Neo4j 过敏冲突检测失败，降级返回空列表", e);
            return List.of();
        }
    }

    /** 药物相互作用：处方内两两 INTERACTS_WITH。 */
    private List<ContraindicationWarning> detectInteraction(Neo4jClient neo4jClient, List<String> drugNames) {
        if (drugNames.size() < 2) {
            return List.of();
        }
        try {
            String cypher = """
                    MATCH (a:Drug)-[:INTERACTS_WITH]->(b:Drug)
                    WHERE a.name IN $drugNames AND b.name IN $drugNames AND a.name <> b.name
                    RETURN a.name AS drugA, b.name AS drugB
                    """;
            Collection<Map<String, Object>> rows = neo4jClient.query(cypher)
                    .bindAll(Map.of("drugNames", drugNames))
                    .fetch()
                    .all();
            List<ContraindicationWarning> warnings = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String drugA = (String) row.get("drugA");
                String drugB = (String) row.get("drugB");
                warnings.add(ContraindicationWarning.builder()
                        .type("INTERACTION")
                        .drugName(drugA)
                        .targetName(drugB)
                        .description("药品「" + drugA + "」与「" + drugB + "」存在相互作用")
                        .build());
            }
            return warnings;
        } catch (Exception e) {
            log.error("Neo4j 药物相互作用检测失败，降级返回空列表", e);
            return List.of();
        }
    }
}
