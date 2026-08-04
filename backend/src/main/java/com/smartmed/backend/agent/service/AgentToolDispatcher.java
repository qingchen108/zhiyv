package com.smartmed.backend.agent.service;

import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.department.service.DepartmentService;
import com.smartmed.backend.doctor.service.DoctorService;
import com.smartmed.backend.knowledge.KnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Agent 工具分发器（09 ticket，ADR-0015）。
 * <p>
 * 工具契约单一来源是 agent/tools/tools.json（11 个工具，查询 7 + 动作 4），
 * Java 侧按名分发：未知工具 404；已知工具 09 阶段一律 501，11+ 逐一接入真实 handler。
 * <p>
 * 实现原则（ADR-0016）：Agent handler 委托给现有 Service 层，不直接操作 Mapper/DB。
 */
@Component
@RequiredArgsConstructor
public class AgentToolDispatcher {

    /**
     * 契约工具全集（11 个）——agent/tools/tools.json 的静态镜像。
     * <p>
     * 工具契约唯一来源是 tools.json（Python 启动读入注册），Java 侧不跨目录读该文件，
     * 此处按契约同步镜像；若契约新增工具需同步本表。
     */
    private static final Set<String> KNOWN_TOOLS = Set.of(
            // 查询类（7）
            "query_departments", "query_doctors", "query_schedule",
            "query_knowledge_graph", "get_medical_record", "get_prescription",
            "query_pharmacy_stock",
            // 动作类（4）
            "create_registration_draft", "write_pre_diagnosis",
            "create_order_draft", "create_reminder"
    );

    private final KnowledgeGraphService knowledgeGraphService;
    private final DepartmentService departmentService;
    private final DoctorService doctorService;

    /**
     * 分发工具调用。
     *
     * @param toolName  工具名（蛇形）
     * @param arguments 工具参数
     * @return 工具执行结果
     */
    public Object dispatch(String toolName, Map<String, Object> arguments) {
        if (!KNOWN_TOOLS.contains(toolName)) {
            throw new BusinessException(404, "未知工具: " + toolName);
        }

        return switch (toolName) {
            // === 11 ticket 导诊相关工具 ===
            case "query_knowledge_graph" -> dispatchQueryKnowledgeGraph(arguments);
            case "query_doctors" -> dispatchQueryDoctors(arguments);
            case "query_departments" -> dispatchQueryDepartments(arguments);

            // === 暂未实现的工具（12-15 ticket） ===
            default -> throw new BusinessException(501, "工具未实现: " + toolName);
        };
    }

    /** 知识图谱查询：症状→疾病→科室。 */
    private Object dispatchQueryKnowledgeGraph(Map<String, Object> args) {
        String keyword = extractString(args, "keyword");
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(400, "keyword 不能为空");
        }
        return Map.of("results", knowledgeGraphService.query(keyword));
    }

    /** 医生查询：按科室筛选，返回医生+号源推荐。 */
    private Object dispatchQueryDoctors(Map<String, Object> args) {
        Long departmentId = extractLong(args, "department_id");
        if (departmentId == null) {
            throw new BusinessException(400, "department_id 不能为空");
        }
        return Map.of("doctors", doctorService.queryForTriage(departmentId));
    }

    /** 科室查询：按名称模糊搜索，返回全部或匹配。 */
    private Object dispatchQueryDepartments(Map<String, Object> args) {
        String name = extractString(args, "name");
        // 返回分页第一页，size 放大到 100 涵盖全部科室
        return Map.of("departments", departmentService.page(1, 100, name));
    }

    // ============ 参数提取辅助 ============

    private static String extractString(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object val = args.get(key);
        return val instanceof String s ? s : null;
    }

    private static Long extractLong(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object val = args.get(key);
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}