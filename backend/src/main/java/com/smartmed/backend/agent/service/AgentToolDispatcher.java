package com.smartmed.backend.agent.service;

import com.smartmed.backend.common.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Agent 工具分发器（09 ticket，ADR-0015）。
 * <p>
 * 工具契约单一来源是 agent/tools/tools.json（11 个工具，查询 7 + 动作 4），
 * Java 侧按名分发：未知工具 404；已知工具 09 阶段一律 501（真实 handler 留给 11-15 ticket）。
 */
@Component
public class AgentToolDispatcher {

    /**
     * 契约工具全集（11 个）——agent/tools/tools.json 的 09 静态镜像。
     * <p>
     * 工具契约唯一来源是 tools.json（Python 启动读入注册），Java 侧不跨目录读该文件，
     * 此处按契约同步镜像；11-15 实现真实 handler 时改为按名注入，若契约新增工具需同步本表。
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

    /**
     * 分发工具调用。
     *
     * @param toolName  工具名（蛇形）
     * @param arguments 工具参数（09 阶段不校验）
     * @return 工具执行结果
     */
    public Object dispatch(String toolName, Map<String, Object> arguments) {
        if (!KNOWN_TOOLS.contains(toolName)) {
            throw new BusinessException(404, "未知工具: " + toolName);
        }
        // TODO(11-15)：按工具名接入真实 handler——query 类直查 Java 业务数据，action 类建草稿（ADR-0015 确认链路）
        throw new BusinessException(501, "工具未实现: " + toolName);
    }
}
