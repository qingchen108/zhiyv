package com.smartmed.backend.agent.dto;

import java.util.Map;

/**
 * 工具调用请求体（09 ticket，ADR-0015 泛化路由）。
 *
 * @param arguments 工具参数，JSON Schema 权威定义见 agent/tools/tools.json（09 阶段不按工具校验）
 */
public record AgentToolRequest(Map<String, Object> arguments) {
}
